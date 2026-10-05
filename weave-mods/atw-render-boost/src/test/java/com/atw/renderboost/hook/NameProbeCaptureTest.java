package com.atw.renderboost.hook;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import com.atw.renderboost.probe.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.CheckClassAdapter;
import static org.junit.jupiter.api.Assertions.*;

/** Private files are read in place, never bundled, written, copied or installed by these tests. */
@Tag("private-name-capture")
class NameProbeCaptureTest implements Opcodes {
    static List<ClassNode> captures() throws Exception {
        Path root=Paths.get(System.getProperty("atwboost.nameCaptureRoot"));
        List<ClassNode> nodes=new ArrayList<>();
        try(java.util.stream.Stream<Path> files=Files.list(root)) {
            for(Path path:(Iterable<Path>)files.filter(p->p.toString().endsWith(".class"))::iterator) {
                ClassNode c=new ClassNode(); new ClassReader(Files.readAllBytes(path)).accept(c,0); nodes.add(c);
            }
        }
        assertEquals(11,nodes.size()); return nodes;
    }
    @Test void allTwelveActualMethodsPassDirectNodeGateAndOriginalInstructionsHandlersAndVisibilityRemain() throws Exception {
        int accepted=0; NameProbeHook hook=new NameProbeHook();
        for(ClassNode c:captures()) {
            Map<MethodNode,List<AbstractInsnNode>> original=new IdentityHashMap<>();
            Map<MethodNode,List<TryCatchBlockNode>> handlers=new IdentityHashMap<>();
            Map<MethodNode,Integer> access=new IdentityHashMap<>();
            for(MethodNode m:c.methods) if(NameProbeHook.metric(c.name,m)>=0) {
                assertTrue(NameProbeHook.matches(c.name,m),c.name+"."+m.name+m.desc); accepted++;
                original.put(m,Arrays.asList(m.instructions.toArray())); handlers.put(m,new ArrayList<>(m.tryCatchBlocks)); access.put(m,m.access);
            }
            hook.transform(c,()->{});
            for(MethodNode m:original.keySet()) {
                List<AbstractInsnNode> after=Arrays.asList(m.instructions.toArray()); int last=-1;
                for(AbstractInsnNode i:original.get(m)) { int index=after.indexOf(i); assertTrue(index>last,"original instructions/labels stay ordered"); last=index; }
                assertEquals(access.get(m).intValue(),m.access); assertEquals(handlers.get(m),m.tryCatchBlocks.subList(0,handlers.get(m).size()));
                assertEquals(handlers.get(m).size()+1,m.tryCatchBlocks.size());
                new Analyzer<>(new BasicVerifier()).analyze(c.name,m);
            }
            // Structural ASM validation without resolving game classes or publishing bytes.
            c.accept(new CheckClassAdapter(new ClassWriter(0),false));
            int count=c.methods.stream().mapToInt(m->m.instructions.size()).sum(); hook.transform(c,()->fail("second transform must be idempotent"));
            assertEquals(count,c.methods.stream().mapToInt(m->m.instructions.size()).sum());
        }
        assertEquals(12,accepted);
    }
    @Test void everyCapturedMethodRejectsBodyAndAccessMutationsWithoutTouchingOriginalPath() throws Exception {
        int tested=0;
        for(ClassNode c:captures()) for(MethodNode m:c.methods) if(NameProbeHook.metric(c.name,m)>=0) {
            tested++;
            for(int change=0;change<2;change++) {
                MethodNode altered=new MethodNode(m.access,m.name,m.desc,m.signature,m.exceptions.toArray(new String[0])); m.accept(altered);
                if(change==0) altered.instructions.insert(new InsnNode(NOP)); else altered.access^=ACC_FINAL;
                assertFalse(NameProbeHook.matches(c.name,altered));
                ClassNode single=new ClassNode(); single.name=c.name; single.methods.add(altered);
                AbstractInsnNode[] before=altered.instructions.toArray();
                new NameProbeHook().transform(single,()->fail("unknown capture must remain original"));
                assertArrayEquals(before,altered.instructions.toArray());
            }
        }
        assertEquals(12,tested);
    }
    static final class HookStageNode extends ClassNode { HookStageNode() { super(ASM9); } }
    @Test void directGateAcceptsSubclassNodeAndUnrelatedWeaveChangesWithoutSerializedClassShapeDependency() throws Exception {
        int tested=0;
        for(ClassNode c:captures()) {
            HookStageNode stage=new HookStageNode(); c.accept(stage);
            stage.fields.add(new FieldNode(ACC_PRIVATE,"$otherHookField","I",null,null));
            stage.methods.add(new MethodNode(ACC_PUBLIC,"$otherHookMethod","()V",null,null));
            for(MethodNode m:stage.methods) if(NameProbeHook.metric(stage.name,m)>=0) {
                assertTrue(NameProbeHook.matches(stage.name,m)); tested++;
            }
        }
        assertEquals(12,tested);
    }
    static class Relocated extends ClassLoader {
        Relocated() { super(NameProbeCaptureTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
            String internal=name.replace('.','/');
            boolean asm=internal.startsWith("net/weavemc/loader/impl/shaded/asm/");
            if(!asm && !name.equals(NameProbeHook.class.getName()) && !name.equals(TerrainEvidence.class.getName())) return super.loadClass(name,resolve);
            Class<?> found=findLoadedClass(name);
            String resource=(asm ? "org/objectweb/asm/"+internal.substring("net/weavemc/loader/impl/shaded/asm/".length()) : internal)+".class";
            if(found==null) try(InputStream in=getParent().getResourceAsStream(resource)) {
                ClassWriter w=new ClassWriter(0);
                new ClassReader(in).accept(new ClassRemapper(w,new Remapper() {
                    @Override public String map(String n) { return n.startsWith("org/objectweb/asm/") ? "net/weavemc/loader/impl/shaded/asm/"+n.substring("org/objectweb/asm/".length()) : n; }
                }),0); byte[] data=w.toByteArray(); found=defineClass(name,data,0,data.length);
            } catch(IOException e) { throw new ClassNotFoundException(name,e); }
            if(resolve) resolveClass(found); return found;
        }
        Object node(ClassNode source) throws Exception {
            Class<?> node=loadClass("net.weavemc.loader.impl.shaded.asm.tree.ClassNode");
            Class<?> reader=loadClass("net.weavemc.loader.impl.shaded.asm.ClassReader");
            Class<?> visitor=loadClass("net.weavemc.loader.impl.shaded.asm.ClassVisitor");
            Object result=node.getConstructor().newInstance();
            reader.getMethod("accept",visitor,int.class).invoke(reader.getConstructor(byte[].class).newInstance((Object)TerrainHookStageTest.bytes(source)),result,0);
            return result;
        }
    }
    @Test void twelveActualMethodGatesPassUnderWeavesRelocatedAsmOperandTypes() throws Exception {
        Relocated loader=new Relocated(); int tested=0;
        Class<?> methodType=loader.loadClass("net.weavemc.loader.impl.shaded.asm.tree.MethodNode");
        Method matches=loader.loadClass(NameProbeHook.class.getName()).getMethod("matches",String.class,methodType);
        for(ClassNode c:captures()) {
            Object node=loader.node(c); List<?> methods=(List<?>)node.getClass().getField("methods").get(node);
            for(int i=0;i<c.methods.size();i++) if(NameProbeHook.metric(c.name,c.methods.get(i))>=0) {
                assertEquals(true,matches.invoke(null,c.name,methods.get(i))); tested++;
            }
        }
        assertEquals(12,tested);
    }
    public static final class ConversionEffect {
        public static int calls; public static RuntimeException failure;
        public static void original() { calls++; if(failure!=null) throw failure; }
    }
    static ClassNode empty(String name,boolean itf) {
        ClassNode c=new ClassNode(); c.name=name; c.version=V1_8; c.access=itf?ACC_PUBLIC|ACC_ABSTRACT|ACC_INTERFACE:ACC_PUBLIC; c.superName="java/lang/Object"; return c;
    }
    static void constructor(ClassNode c) {
        MethodNode m=new MethodNode(ACC_PUBLIC,"<init>","()V",null,null); m.maxLocals=1; m.maxStack=1;
        m.instructions.add(new VarInsnNode(ALOAD,0)); m.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/lang/Object","<init>","()V",false)); m.instructions.add(new InsnNode(RETURN)); c.methods.add(m);
    }
    @Test void actualCapturedWidthBytecodeExecutesWithOriginalCallsReturnAndExceptionUnderProductionWrapper() throws Exception {
        ClassNode captured=captures().stream().filter(c->c.name.equals(NameProbeHook.TARGETS[4])).findFirst().get();
        MethodNode width=captured.methods.stream().filter(m->NameProbeHook.metric(captured.name,m)==4).findFirst().get();
        assertTrue(NameProbeHook.matches(captured.name,width));
        MethodInsnNode bridge=null;
        for(AbstractInsnNode i:width.instructions) if(i instanceof MethodInsnNode && i.getOpcode()==INVOKESTATIC) bridge=(MethodInsnNode)i;
        assertNotNull(bridge); String bridgeType=Type.getReturnType(bridge.desc).getInternalName();
        String component="net/kyori/adventure/text/Component", chat="net/minecraft/util/IChatComponent";
        Map<String,ClassNode> nodes=new HashMap<>();
        nodes.put(component,empty(component,true)); nodes.put(bridgeType,empty(bridgeType,true));
        ClassNode chatNode=empty(chat,true); chatNode.methods.add(new MethodNode(ACC_PUBLIC|ACC_ABSTRACT,"getFormattedText","()Ljava/lang/String;",null,null)); nodes.put(chat,chatNode);
        ClassNode converter=empty(bridge.owner,false);
        MethodNode convert=new MethodNode(ACC_PUBLIC|ACC_STATIC,bridge.name,bridge.desc,null,null); convert.maxLocals=1; convert.maxStack=1;
        convert.instructions.add(new MethodInsnNode(INVOKESTATIC,Type.getInternalName(ConversionEffect.class),"original","()V",false));
        convert.instructions.add(new VarInsnNode(ALOAD,0)); convert.instructions.add(new TypeInsnNode(CHECKCAST,bridgeType)); convert.instructions.add(new InsnNode(ARETURN)); converter.methods.add(convert); nodes.put(converter.name,converter);
        ClassNode input=empty("fixture/CapturedWidthInput",false); input.interfaces.addAll(Arrays.asList(component,bridgeType,chat)); constructor(input);
        MethodNode formatted=new MethodNode(ACC_PUBLIC,"getFormattedText","()Ljava/lang/String;",null,null); formatted.maxLocals=1; formatted.maxStack=1;
        formatted.instructions.add(new LdcInsnNode("captured-test")); formatted.instructions.add(new InsnNode(ARETURN)); input.methods.add(formatted); nodes.put(input.name,input);
        ClassNode font=empty(captured.name,false); constructor(font);
        MethodNode baseline=new MethodNode(width.access,"baselineWidth",width.desc,null,null); width.accept(baseline); baseline.name="baselineWidth"; font.methods.add(baseline);
        font.methods.add(width); new NameProbeHook().transform(font,()->{});
        MethodNode stringWidth=new MethodNode(ACC_PUBLIC,"getStringWidth","(Ljava/lang/String;)I",null,null); stringWidth.maxLocals=2; stringWidth.maxStack=1;
        stringWidth.instructions.add(new VarInsnNode(ALOAD,1)); stringWidth.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","length","()I",false)); stringWidth.instructions.add(new InsnNode(IRETURN)); font.methods.add(stringWidth); nodes.put(font.name,font);
        Map<String,byte[]> bytes=new HashMap<>();
        for(ClassNode c:nodes.values()) {
            for(MethodNode m:c.methods) if((m.access&ACC_ABSTRACT)==0) new Analyzer<>(new BasicVerifier()).analyze(c.name,m);
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS); c.accept(w); bytes.put(c.name.replace('/','.'),w.toByteArray());
        }
        ClassLoader loader=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(!bytes.containsKey(name)) return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name); if(type==null) { byte[] b=bytes.get(name); type=defineClass(name,b,0,b.length); }
                if(resolve) resolveClass(type); return type;
            }
        };
        NameProbeHookTest.Clock clock=new NameProbeHookTest.Clock(); NameProbe p=NameProbeHookTest.arm(clock);
        try {
            Class<?> type=loader.loadClass(font.name.replace('/','.')), argType=loader.loadClass(component.replace('/','.'));
            Object receiver=type.getConstructor().newInstance(), argument=loader.loadClass(input.name.replace('/','.')).getConstructor().newInstance();
            ConversionEffect.calls=0; ConversionEffect.failure=null;
            Object baselineResult=type.getMethod("baselineWidth",argType).invoke(receiver,argument);
            int outer=p.enter(0,0); assertEquals(baselineResult,type.getMethod(width.name,argType).invoke(receiver,argument)); p.exit(outer,false);
            assertEquals(2,ConversionEffect.calls); assertEquals(1,NameProbeHookTest.value(p.snapshot(),"player.componentWidth.calls"));
            ConversionEffect.failure=new IllegalStateException("original converter failure"); outer=p.enter(0,0);
            java.lang.reflect.InvocationTargetException thrown=assertThrows(java.lang.reflect.InvocationTargetException.class,()->type.getMethod(width.name,argType).invoke(receiver,argument));
            assertSame(ConversionEffect.failure,thrown.getCause()); p.exit(outer,true); assertEquals(0,p.depth());
            assertEquals(1,NameProbeHookTest.value(p.snapshot(),"player.componentWidth.exceptionalCalls"));
        } finally { NameProbeRuntime.collecting=false; NameProbeHookTest.set("probe",null); NameProbeHookTest.set("owner",null); ConversionEffect.failure=null; }
    }
}
