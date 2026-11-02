package com.atw.renderboost.hook;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

/** Existing private capture inputs read in place. No captures/evidence are regenerated. */
@Tag("private-name-capture")
class ForwardHoverCaptureTest implements Opcodes {
    static class Relocated extends ClassLoader {
        Relocated(){super(ForwardHoverCaptureTest.class.getClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            String internal=name.replace('.','/');boolean asm=internal.startsWith("net/weavemc/loader/impl/shaded/asm/");
            if(!asm&&!name.startsWith("com.atw.renderboost.hook."))return super.loadClass(name,resolve);
            Class<?> type=findLoadedClass(name);
            if(type==null)try(InputStream in=getParent().getResourceAsStream((asm?"org/objectweb/asm/"+internal.substring("net/weavemc/loader/impl/shaded/asm/".length()):internal)+".class")){
                ClassWriter w=new ClassWriter(0);new ClassReader(in).accept(new org.objectweb.asm.commons.ClassRemapper(w,new org.objectweb.asm.commons.Remapper(){
                    @Override public String map(String n){return n.startsWith("org/objectweb/asm/")?"net/weavemc/loader/impl/shaded/asm/"+n.substring("org/objectweb/asm/".length()):n;}
                }),0);byte[] bytes=w.toByteArray();type=defineClass(name,bytes,0,bytes.length);
            }catch(IOException e){throw new ClassNotFoundException(name,e);}if(resolve)resolveClass(type);return type;
        }
        Object node(ClassNode c)throws Exception{Class<?> node=loadClass("net.weavemc.loader.impl.shaded.asm.tree.ClassNode"),reader=loadClass("net.weavemc.loader.impl.shaded.asm.ClassReader"),visitor=loadClass("net.weavemc.loader.impl.shaded.asm.ClassVisitor");
            ClassWriter w=new ClassWriter(0);c.accept(w);Object result=node.getConstructor().newInstance();reader.getMethod("accept",visitor,int.class).invoke(reader.getConstructor(byte[].class).newInstance((Object)w.toByteArray()),result,0);return result;}
    }
    static ClassNode entity()throws Exception{return NameProbeCaptureTest.captures().stream().filter(c->c.name.equals(ForwardHoverHook.ENTITY)).findFirst().get();}
    static MethodNode hover(ClassNode c){return c.methods.stream().filter(m->NameProbeHookStage.logicalName(m.name).equals("getHoverEvent")).findFirst().get();}
    @Test @Tag("private-name-codec-capture") void exactDependencyAndSharedPipelineWorkUnderWeavesRelocatedAsm()throws Exception{
        Relocated loader=new Relocated();Class<?> node=loader.loadClass("net.weavemc.loader.impl.shaded.asm.tree.ClassNode"),method=loader.loadClass("net.weavemc.loader.impl.shaded.asm.tree.MethodNode");
        Class<?> forward=loader.loadClass(ForwardHoverHook.class.getName());java.lang.reflect.Method dependency=forward.getDeclaredMethod("dependencyMarker",node);dependency.setAccessible(true);
        for(ClassNode c:NameParseCaptureTest.captures())if(Arrays.asList(ForwardHoverHook.TARGETS).contains(c.name)){
            c=NameProbeHookStageCaptureTest.rename(c,false);new NameParseHook().transform(c,()->{});new NameEncodingHook().transform(c,()->{});assertNotNull(dependency.invoke(null,loader.node(c)));
        }
        ClassNode factory=NameEncodingFactoryCaptureTest.packaged();new NameEncodingHook().transform(factory,()->fail());assertNotNull(dependency.invoke(null,loader.node(factory)));
        Object owner=loader.node(NameProbeHookStageCaptureTest.rename(entity(),false));List<?> methods=(List<?>)node.getField("methods").get(owner);Object m=methods.stream().filter(x->{try{return ((String)method.getField("name").get(x)).endsWith("getHoverEvent");}catch(Exception e){throw new AssertionError(e);}}).findFirst().get();
        Class<?> stage=loader.loadClass(NameProbeHookStage.class.getName());java.lang.reflect.Constructor<?> constructor=stage.getDeclaredConstructor(node);constructor.setAccessible(true);Object proof=constructor.newInstance(owner);
        java.lang.reflect.Method compare=stage.getDeclaredMethod("comparison",method);compare.setAccessible(true);Object comparison=compare.invoke(proof,m);
        java.lang.reflect.Method compose=forward.getDeclaredMethod("compose",node,method,method);compose.setAccessible(true);assertEquals(true,compose.invoke(null,owner,m,comparison));
        Class<?> analyzer=loader.loadClass("net.weavemc.loader.impl.shaded.asm.tree.analysis.Analyzer"),interpreter=loader.loadClass("net.weavemc.loader.impl.shaded.asm.tree.analysis.Interpreter"),verifier=loader.loadClass("net.weavemc.loader.impl.shaded.asm.tree.analysis.BasicVerifier");
        analyzer.getMethod("analyze",String.class,method).invoke(analyzer.getConstructor(interpreter).newInstance(verifier.getConstructor().newInstance()),ForwardHoverHook.ENTITY,m);
    }
    @Test void exactPristineAndConflictCompositionPreservesAllOriginalNodesAndPlacesCommitOutsideProbeHandler()throws Exception{
        for(boolean conflict:new boolean[]{false,true}){
            ClassNode c=entity();if(conflict)c=NameProbeHookStageCaptureTest.rename(c,false);MethodNode m=hover(c);
            MethodNode comparison=new NameProbeHookStage(c).comparison(m);assertTrue(NameProbeHook.matches(c.name,comparison));
            List<AbstractInsnNode> original=Arrays.asList(m.instructions.toArray());new NameProbeHook().transform(c,()->{});
            int last=-1;for(AbstractInsnNode n:original){int position=m.instructions.indexOf(n);assertTrue(position>last);last=position;}
            assertTrue(c.fields.stream().anyMatch(f->f.name.startsWith(ForwardHoverHook.MARKER+"$")));assertEquals(1,m.tryCatchBlocks.size());
            int enters=0,exits=0,commits=0;for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)n;
                if(call.owner.equals(NameProbeHook.RUNTIME)){if(call.name.equals("enter"))enters++;if(call.name.equals("exit"))exits++;}
                if(call.owner.equals(ForwardHoverHook.RUNTIME)&&call.name.equals("commit")){commits++;assertTrue(m.instructions.indexOf(n)>m.instructions.indexOf(m.tryCatchBlocks.get(0).end));}}
            assertEquals(1,enters);assertEquals(2,exits);assertEquals(1,commits);new Analyzer<>(new BasicVerifier()).analyze(c.name,m);
            int count=m.instructions.size();new NameProbeHook().transform(c,()->fail("prewrapped input must not be patched again"));assertEquals(count,m.instructions.size());
        }
    }
    @Test void producerBodyOperandAccessBranchHandlerAndProvenanceMutationsRejectBeforeForwardMutation()throws Exception{
        for(int change=0;change<8;change++){
            ClassNode c=entity();MethodNode m=hover(c);
            if(change==0)m.instructions.insert(new InsnNode(NOP));
            if(change==1)m.access^=ACC_FINAL;
            if(change==2)for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode&&((MethodInsnNode)n).name.equals("getName")){((MethodInsnNode)n).owner+="$unproved";break;}
            if(change==3)for(AbstractInsnNode n:m.instructions)if(n instanceof JumpInsnNode){((JumpInsnNode)n).setOpcode(IFNONNULL);break;}
            if(change==4){LabelNode start=new LabelNode(),end=new LabelNode();m.instructions.insert(start);m.instructions.add(end);m.tryCatchBlocks.add(new TryCatchBlockNode(start,end,end,null));}
            if(change==5)m.name=NameProbeHookStage.PREFIX+m.name;
            if(change==6){m.visibleAnnotations=new ArrayList<>();AnnotationNode a=new AnnotationNode(NameProbeHookStage.MERGED);a.values=Arrays.asList("mixin","unproved","priority",1000,"sessionId","00000000-0000-0000-0000-000000000000");m.visibleAnnotations.add(a);}
            if(change==7)c.methods.remove(m);
            AbstractInsnNode[] before=m.instructions.toArray();new NameProbeHook().transform(c,()->{});assertArrayEquals(before,m.instructions.toArray());
            assertFalse(c.fields.stream().anyMatch(f->f.name.startsWith(ForwardHoverHook.MARKER)));
        }
    }
    @Test @Tag("private-name-codec-capture") void unchangedNamesReverseDependenciesAndExactStartupFactoryComposeWithoutNewNbtDeclarations()throws Exception{
        int checked=0;
        for(ClassNode nativeClass:NameParseCaptureTest.captures()){
            ClassNode c=NameProbeHookStageCaptureTest.rename(nativeClass,false);new NameParseHook().transform(c,()->{});
            assertTrue(NameEncodingHook.matches(c));new NameEncodingHook().transform(c,()->{});
            if(!Arrays.asList(ForwardHoverHook.TARGETS).contains(c.name))continue;
            List<FieldNode> fields=new ArrayList<>(c.fields);Map<MethodNode,List<AbstractInsnNode>> methods=new IdentityHashMap<>();for(MethodNode m:c.methods)methods.put(m,Arrays.asList(m.instructions.toArray()));
            assertNotNull(ForwardHoverHook.dependencyMarker(c));new ForwardHoverHook().transform(c,()->fail("dependencies cannot inject methods"));
            assertEquals(fields,c.fields);for(MethodNode m:c.methods)assertEquals(methods.get(m),Arrays.asList(m.instructions.toArray()));
            for(int change=0;change<4;change++){ClassNode altered=new ClassNode();c.accept(altered);
                if(change==0)altered.methods.get(0).instructions.insert(new InsnNode(NOP));
                if(change==1)altered.fields.get(0).access^=ACC_FINAL;
                if(change==2)altered.fields.stream().filter(f->f.name.startsWith(NameEncodingHook.MARKER)).findFirst().get().value=0;
                if(change==3)Collections.reverse(altered.methods);
                assertNull(ForwardHoverHook.dependencyMarker(altered),c.name+" mutation "+change);
            }checked++;
        }
        assertEquals(3,checked);NameEncodingFactoryCaptureTest.verified();
        ClassNode historical=NameEncodingFactoryCaptureTest.captured();assertFalse(NameEncodingHook.matches(historical));
        ClassNode startup=NameEncodingFactoryCaptureTest.packaged();new NameEncodingHook().transform(startup,()->fail());assertNotNull(ForwardHoverHook.dependencyMarker(startup));
        List<FieldNode> fields=new ArrayList<>(startup.fields);new ForwardHoverHook().transform(startup,()->fail());assertEquals(fields,startup.fields);
        startup.methods.stream().filter(m->m.name.equals("newHashMap")&&m.desc.equals("()Ljava/util/HashMap;")).findFirst().get().instructions.insert(new InsnNode(NOP));assertNull(ForwardHoverHook.dependencyMarker(startup));
    }
    @Test void existingRestartProbeWrapperIsUnsupportedForNewForwardAdmission()throws Exception{
        String root=System.getProperty("atwboost.nameRestartCaptureRoot");if(root==null)return;
        ClassNode c=new ClassNode();try(java.util.stream.Stream<Path> files=Files.list(Paths.get(root))){Path p=files.filter(f->f.getFileName().toString().startsWith("net_minecraft_entity_Entity.")).findFirst().get();new ClassReader(Files.readAllBytes(p)).accept(c,0);}
        MethodNode m=hover(c);assertFalse(NameProbeHook.matches(c.name,m));AbstractInsnNode[] before=m.instructions.toArray();
        new NameProbeHook().transform(c,()->fail("restart wrapper cannot be injected twice"));assertArrayEquals(before,m.instructions.toArray());assertFalse(c.fields.stream().anyMatch(f->f.name.startsWith(ForwardHoverHook.MARKER)));
    }
}
