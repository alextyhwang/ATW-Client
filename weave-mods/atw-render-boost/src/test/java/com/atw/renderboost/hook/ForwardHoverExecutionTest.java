package com.atw.renderboost.hook;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

/** JVM execution of the production runtime and injected seam. Native stubs are NOT live evidence. */
class ForwardHoverExecutionTest implements Opcodes {
    public static final class Effects {
        public static final List<String> trace=new ArrayList<>();public static final List<Object> compounds=new ArrayList<>();
        public static UUID id=UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");public static String name="name",type="player",point;
        static Throwable failure;static Runnable nested,boundary;static String boundaryPoint;
        public static void event(String at){trace.add(at);if(at.equals(point))raise(failure);if(at.equals("text")&&nested!=null){Runnable r=nested;nested=null;r.run();}}
        public static void fire(String at){if(at.equals(boundaryPoint)&&boundary!=null){Runnable r=boundary;boundary=null;r.run();}}
        static <T extends Throwable> void raise(Throwable error)throws T {throw (T)error;}
    }
    public static class Base {}
    public static class Tag extends Base {
        public String data;
        public Tag(String value){Effects.event("tag");data=Objects.requireNonNull(value);}
        @Override public String toString(){return '"'+data.replace("\\","\\\\").replace("\"","\\\"")+'"';}
    }
    public static class Factory {public static HashMap<String,Base> newHashMap(){return new HashMap<>();}}
    public static class Compound extends Base {
        public Map<String,Base> tagMap;
        public Compound(){Effects.event("compound");tagMap=Factory.newHashMap();Effects.compounds.add(this);}
        public void setString(String key,String value){Effects.event("set:"+key);tagMap.put(key,new Tag(value));}
        @Override public String toString(){Effects.event("stringify");StringBuilder b=new StringBuilder("{");
            for(Map.Entry<String,Base> e:tagMap.entrySet()){if(b.length()>1)b.append(',');b.append(e.getKey()).append(':').append(e.getValue());}
            return b.append('}').toString();}
    }
    public interface Chat {}
    public static class Text implements Chat {public String value;public int style;public Text(String s){Effects.event("text");value=s;}}
    public static class Action {public static final Action SHOW_ENTITY=new Action();}
    public static class Hover {public Chat value;public Hover(Action a,Chat c){Effects.event("hover");value=c;}}
    public static class EntityList {public static String getEntityString(Entity e){Effects.event("type");return Effects.type;}}
    public static class Entity {
        public UUID getUniqueID(){Effects.event("uuid");return Effects.id;}
        public String getName(){Effects.event("name");return Effects.name;}
        public Hover getHoverEvent(){Compound c=new Compound();String type=EntityList.getEntityString(this);
            c.setString("id",getUniqueID().toString());if(type!=null)c.setString("type",type);c.setString("name",getName());
            return new Hover(Action.SHOW_ENTITY,new Text(c.toString()));}
    }
    static Map<String,String> mapping(){Map<String,String> m=new HashMap<>();
        Class<?>[] types={Entity.class,Base.class,Compound.class,Tag.class,Factory.class,EntityList.class,Chat.class,Text.class,Hover.class,Action.class};
        String[] names={ForwardHoverHook.ENTITY,ForwardHoverHook.TARGETS[1],ForwardHoverHook.TARGETS[2],ForwardHoverHook.TARGETS[3],ForwardHoverHook.MAPS,
            "net/minecraft/entity/EntityList","net/minecraft/util/IChatComponent","net/minecraft/util/ChatComponentText","net/minecraft/event/HoverEvent","net/minecraft/event/HoverEvent$Action"};
        for(int i=0;i<types.length;i++)m.put(Type.getInternalName(types[i]),names[i]);return m;
    }
    static ClassNode node(Class<?> type)throws Exception {ClassNode c=new ClassNode();try(InputStream in=type.getResourceAsStream("/"+Type.getInternalName(type)+".class")){
        new ClassReader(in).accept(new ClassRemapper(c,new SimpleRemapper(mapping())),0);}return c;}
    static MethodNode body()throws Exception{return node(Entity.class).methods.stream().filter(m->m.name.equals("getHoverEvent")).findFirst().get();}
    static final class Fixture extends ClassLoader {
        final Map<String,byte[]> bytes=new HashMap<>();final Class<?> runtime,ticket,entity,compound,tag;final Object receiver;
        final String marker=NameEncodingHook.MARKER+"$"+UUID.randomUUID().toString().replace("-","");
        final String entityMarker=ForwardHoverHook.MARKER+"$"+UUID.randomUUID().toString().replace("-","");
        final Class<?> maps;
        Fixture(boolean markers,boolean factoryProof)throws Exception {this(markers,factoryProof,null,null);}
        Fixture(boolean markers,boolean factoryProof,MethodNode supplied,Map<String,ClassNode> nativeBodies)throws Exception {
            super(ForwardHoverExecutionTest.class.getClassLoader());
            for(Class<?> type:new Class<?>[]{Entity.class,Base.class,Compound.class,Tag.class,EntityList.class,Chat.class,Text.class,Hover.class,Action.class}){
                ClassNode c=node(type);
                if(nativeBodies!=null&&nativeBodies.containsKey(c.name))c=nativeBodies.get(c.name);
                if(c.name.equals(ForwardHoverHook.ENTITY)){
                    if(supplied!=null){c.methods.removeIf(m->m.name.equals("getHoverEvent"));c.methods.add(supplied);}
                    MethodNode m=c.methods.stream().filter(x->x.name.equals("getHoverEvent")).findFirst().get();
                    MethodNode baseline=new MethodNode(m.access,"baseline",m.desc,m.signature,null);m.accept(baseline);baseline.name="baseline";c.methods.add(baseline);
                    int[] plan=ForwardHoverHook.inject(m);NameProbeHook.wrap(m,3);ForwardHoverHook.finish(m,plan);
                    if(markers)c.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,entityMarker,"I",null,0x41545746));
                }else if(Arrays.asList(ForwardHoverHook.TARGETS).contains(c.name)&&markers){
                    c.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,NameParseHook.MARKER,"I",null,NameParseHook.MARKER_VALUE));
                    c.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,marker,"I",null,NameEncodingHook.MARKER_VALUE));
                }
                put(c);
            }
            ClassNode factory=node(Factory.class);if(markers)factory.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,marker,"I",null,NameEncodingHook.MARKER_VALUE));
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);factory.accept(w);byte[] data=w.toByteArray();
            maps=new ClassLoader(getParent()){Class<?> define(){return defineClass(ForwardHoverHook.MAPS.replace('/','.'),data,0,data.length);}}.define();
            runtime=loadClass(ForwardHoverHook.RUNTIME.replace('/','.'));ticket=loadClass(ForwardHoverHook.RUNTIME.replace('/','.')+"$Ticket");
            entity=loadClass(ForwardHoverHook.ENTITY.replace('/','.'));compound=loadClass(ForwardHoverHook.TARGETS[2].replace('/','.'));tag=loadClass(ForwardHoverHook.TARGETS[3].replace('/','.'));
            receiver=entity.getConstructor().newInstance();frame(new Object());
            for(String target:ForwardHoverHook.TARGETS)if(!target.equals(ForwardHoverHook.MAPS)||factoryProof)evidence(target,true);
        }
        void put(ClassNode c)throws Exception{
            for(MethodNode m:c.methods)if((m.access&(ACC_ABSTRACT|ACC_NATIVE))==0)new Analyzer<>(new BasicVerifier()).analyze(c.name,m);
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected String getCommonSuperClass(String a,String b){return "java/lang/Object";}};
            c.accept(w);bytes.put(c.name.replace('/','.'),w.toByteArray());
        }
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(name.equals(ForwardHoverHook.MAPS.replace('/','.')))return maps;
            if(!bytes.containsKey(name)&&!name.startsWith(ForwardHoverHook.RUNTIME.replace('/','.')))return super.loadClass(name,resolve);
            Class<?> c=findLoadedClass(name);if(c==null){byte[] data=bytes.get(name);
                if(data==null)try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);data=out.toByteArray();
                }catch(IOException e){throw new ClassNotFoundException(name,e);}
                if(name.equals(ForwardHoverHook.RUNTIME.replace('/','.'))){ClassNode node=new ClassNode();new ClassReader(data).accept(node,0);
                    for(MethodNode m:node.methods)for(AbstractInsnNode n:m.instructions.toArray()){
                        String point=null;boolean after=false;
                        if(m.name.equals("hit")&&n instanceof TypeInsnNode&&n.getOpcode()==NEW&&((TypeInsnNode)n).desc.equals("java/lang/String"))point="allocation";
                        if(n instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)n;
                            if(m.name.equals("eligible")&&call.name.equals("forName"))point="eligible";
                            if(m.name.equals("begin")&&call.name.equals("eligible")){point="begin";after=true;}
                            if(m.name.equals("begin")&&call.name.equals("lookup"))point="lookup";
                            if(m.name.equals("begin")&&call.name.equals("inspect"))point="inspect";
                            if(m.name.equals("hit")&&call.name.equals("epoch"))point="hit";
                            if(m.name.equals("hit")&&call.owner.equals("java/lang/String")&&call.name.equals("<init>")){point="wrapper";after=true;}
                            if(m.name.equals("accept")&&call.name.equals("epoch"))point="accept";
                            if(m.name.equals("commit")&&call.name.equals("epoch"))point="commit";
                            if(m.name.equals("commit")&&call.name.equals("record"))point="record";
                        }
                        if(point!=null){InsnList code=new InsnList();code.add(new LdcInsnNode(point));code.add(new MethodInsnNode(INVOKESTATIC,Type.getInternalName(Effects.class),"fire","(Ljava/lang/String;)V",false));
                            if(after)m.instructions.insert(n,code);else m.instructions.insertBefore(n,code);}
                    }
                    ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(w);data=w.toByteArray();
                }
                c=defineClass(name,data,0,data.length);
            }if(resolve)resolveClass(c);return c;
        }
        Object call(String name,Class<?>[] types,Object...args)throws Exception{return runtime.getMethod(name,types).invoke(null,args);}
        void evidence(String target,boolean accepted)throws Exception{call("evidence",new Class<?>[]{String.class,boolean.class,String.class},target,accepted,target.equals(ForwardHoverHook.ENTITY)?entityMarker:marker);}
        void request(boolean on)throws Exception{call("request",new Class<?>[]{boolean.class},on);}
        void frame(Object world)throws Exception{call("frame",new Class<?>[]{Thread.class,Object.class},Thread.currentThread(),world);}
        Object invoke(boolean injected)throws Exception{return entity.getMethod(injected?"getHoverEvent":"baseline").invoke(receiver);}
        String content(Object hover)throws Exception{Object text=hover.getClass().getField("value").get(hover);return (String)text.getClass().getField("value").get(text);}
        long[] counters()throws Exception{return (long[])call("counters",new Class<?>[0]);}
        Properties metadata()throws Exception{Properties p=new Properties();call("metadata",new Class<?>[]{Properties.class},p);return p;}
        void clear()throws Exception{call("clear",new Class<?>[0]);}
        Object begin(Object value,String id,String type,String name)throws Exception{return call("begin",new Class<?>[]{Class.class,Object.class,String.class,String.class,String.class},entity,value,id,type,name);}
        String hit(Object t)throws Exception{return (String)call("hit",new Class<?>[]{ticket},t);}
        void commit(Object t,String output)throws Exception{call("commit",new Class<?>[]{ticket,String.class},t,output);}
    }
    @AfterEach void reset(){Effects.trace.clear();Effects.compounds.clear();Effects.point=null;Effects.failure=null;Effects.name="name";Effects.type="player";
        Effects.id=UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");Effects.boundary=null;Effects.boundaryPoint=null;Effects.nested=null;}
    static void parity(Fixture f)throws Exception{
        for(String type:new String[]{null,"","player"})for(String name:new String[]{"","Aa","BB","§a","\"\\,{}:","\u0000\n\t","\ud83d\ude00","\ud800","\udc00"}){
            Effects.type=type;Effects.name=name;Effects.trace.clear();Object baseline=f.invoke(false);List<String> trace=new ArrayList<>(Effects.trace);
            Object again=f.invoke(false);f.request(false);Object off=f.invoke(true);f.request(true);
            Effects.trace.clear();Object miss=f.invoke(true);assertEquals(trace,Effects.trace);
            Effects.trace.clear();Object first=f.invoke(true),second=f.invoke(true);List<String> hitTrace=new ArrayList<>(trace);hitTrace.remove("stringify");
            List<String> twice=new ArrayList<>(hitTrace);twice.addAll(hitTrace);assertEquals(twice,Effects.trace);
            Set<Object> objects=Collections.newSetFromMap(new IdentityHashMap<>()),strings=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Object result:new Object[]{baseline,again,off,miss,first,second}){assertTrue(objects.add(result));assertEquals(f.content(baseline),f.content(result));assertTrue(strings.add(f.content(result)));}
            Object text=first.getClass().getField("value").get(first);text.getClass().getField("value").set(text,"mutated");assertEquals(f.content(baseline),f.content(f.invoke(true)));
            assertEquals("1",f.metadata().getProperty("forwardEntries"));
        }
    }
    @Test void exactValuesOrderCallbacksFreshOutwardStringsAndMutableComponents()throws Exception{parity(new Fixture(true,true));}
    @Test void allNativeAllocationsRemainFreshAndCurrentGetterValuesChangeImmediately()throws Exception{
        Fixture f=new Fixture(true,true);f.request(true);Object first=f.invoke(true);Effects.name="changed";Object changed=f.invoke(true);assertNotEquals(f.content(first),f.content(changed));
        Effects.name="name";assertEquals(f.content(first),f.content(f.invoke(true)));assertEquals(1,f.counters()[0]);
        Effects.id=UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");assertNotEquals(f.content(first),f.content(f.invoke(true)));
        Set<Object> maps=Collections.newSetFromMap(new IdentityHashMap<>()),tags=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Object c:Effects.compounds){Map<?,?> map=(Map<?,?>)f.compound.getField("tagMap").get(c);assertTrue(maps.add(map));for(Object t:map.values())assertTrue(tags.add(t));}
    }
    @Test void originalFailuresNullsAndConstructorsNeverRetryOrAdmitExceptionalMiss()throws Exception{
        for(String point:new String[]{"compound","type","uuid","name","set:id","set:type","set:name","tag","stringify","text","hover"}){
            Fixture f=new Fixture(true,true);f.request(true);RuntimeException error=new IllegalStateException(point);Effects.point=point;Effects.failure=error;
            Effects.trace.clear();assertSame(error,assertThrows(InvocationTargetException.class,()->f.invoke(false)).getCause());List<String> baseline=new ArrayList<>(Effects.trace);
            Effects.trace.clear();assertSame(error,assertThrows(InvocationTargetException.class,()->f.invoke(true)).getCause());assertEquals(baseline,Effects.trace);assertEquals(0,f.counters()[2]);Effects.point=null;
        }
        for(boolean nullUuid:new boolean[]{true,false}){
            Fixture f=new Fixture(true,true);f.request(true);if(nullUuid)Effects.id=null;else Effects.name=null;
            Effects.trace.clear();assertInstanceOf(NullPointerException.class,assertThrows(InvocationTargetException.class,()->f.invoke(false)).getCause());List<String> baseline=new ArrayList<>(Effects.trace);
            Effects.trace.clear();assertInstanceOf(NullPointerException.class,assertThrows(InvocationTargetException.class,()->f.invoke(true)).getCause());assertEquals(baseline,Effects.trace);assertEquals(0,f.counters()[2]);reset();
        }
        Fixture f=new Fixture(true,true);f.request(true);f.invoke(true);Effects.point="text";Effects.failure=new IllegalArgumentException("hit constructor");
        Effects.trace.clear();assertSame(Effects.failure,assertThrows(InvocationTargetException.class,()->f.invoke(true)).getCause());assertFalse(Effects.trace.contains("stringify"));assertEquals(0,f.counters()[10]);
    }
    @Test void fatalHelperErrorsPropagateUnchangedIncludingCommitAfterProbeCleanup()throws Exception{
        NameProbeHookTest.Clock clock=new NameProbeHookTest.Clock();com.atw.renderboost.probe.NameProbe probe=NameProbeHookTest.arm(clock);
        try{for(String point:new String[]{"eligible","begin","inspect","lookup","hit","allocation","wrapper","accept","commit","record"})for(Error error:new Error[]{new ThreadDeath(),new StackOverflowError("controlled"),new InternalError("controlled"),new UnknownError("controlled")}){
            Fixture f=new Fixture(true,true);f.request(true);if(Arrays.asList("hit","allocation","wrapper","accept").contains(point))f.invoke(true);
            long[] before=f.counters();long exceptional=NameProbeHookTest.value(probe.snapshot(),"otherLiving.hoverEvent.exceptionalCalls");
            Effects.boundaryPoint=point;Effects.boundary=()->{throw error;};
            assertSame(error,assertThrows(InvocationTargetException.class,()->f.invoke(true)).getCause(),point);assertEquals(before[0],f.counters()[0]);assertEquals(before[2],f.counters()[2]);assertEquals(before[6],f.counters()[6]);
            assertEquals(0,probe.depth());if(point.equals("commit")||point.equals("record"))assertEquals(exceptional,NameProbeHookTest.value(probe.snapshot(),"otherLiving.hoverEvent.exceptionalCalls"));
        }}finally{new NameProbeHookTest().clear();}
    }
    @Test void recoverableLookupWrapperRecordFailureAndEpochRacesRetainOriginalExecution()throws Exception{
        for(String point:new String[]{"eligible","begin","inspect","lookup","allocation","wrapper","accept","record"})for(boolean failure:new boolean[]{false,true}){
            Fixture f=new Fixture(true,true);f.request(true);Object original=f.invoke(false);
            if(Arrays.asList("allocation","wrapper","accept").contains(point))f.invoke(true);
            long[] before=f.counters();
            Effects.trace.clear();Effects.boundaryPoint=point;Effects.boundary=()->{if(failure)throw new OutOfMemoryError("controlled optional helper");try{f.clear();}catch(Exception e){throw new AssertionError(e);}};
            Object result=f.invoke(true);assertEquals(f.content(original),f.content(result));assertNotSame(f.content(original),f.content(result));
            assertEquals(before[0],f.counters()[0]);assertEquals(1,Collections.frequency(Effects.trace,"stringify"));
            assertEquals(before[6]+(failure?1:0),f.counters()[6],point);assertEquals("0",f.metadata().getProperty("forwardEntries"),point);
        }
    }
    @Test void lifecycleWrongThreadMissingWorldMarkersFactoryAndRedefinitionFailClosed()throws Exception{
        Fixture f=new Fixture(true,true);assertFalse(f.runtime.getField("requested").getBoolean(null));f.request(true);f.invoke(true);f.invoke(true);assertEquals(1,f.counters()[0]);
        long[] before=f.counters();f.clear();assertFalse((Boolean)f.call("stable",new Class<?>[]{long[].class,boolean.class},before,true));f.invoke(true);assertEquals(1,f.counters()[0]);
        f.frame(new Object());f.invoke(true);assertEquals(1,f.counters()[0]);f.request(false);f.request(true);f.invoke(true);assertEquals(1,f.counters()[0]);
        f.frame(null);f.invoke(true);assertEquals("0",f.metadata().getProperty("forwardEntries"));f.frame(new Object());
        AtomicReference<Throwable> failure=new AtomicReference<>();Thread other=new Thread(()->{try{f.invoke(true);}catch(Throwable e){failure.set(e);}});other.start();other.join();assertNull(failure.get());assertEquals(1,f.counters()[0]);
        f.evidence(ForwardHoverHook.MAPS,true);f.invoke(true);assertEquals("false",f.metadata().getProperty("forwardActive"));f.evidence(ForwardHoverHook.MAPS,true);f.invoke(true);assertEquals(1,f.counters()[0]);
        for(boolean marker:new boolean[]{false,true}){Fixture unavailable=new Fixture(marker,false);unavailable.request(true);unavailable.invoke(true);unavailable.invoke(true);assertEquals(0,unavailable.counters()[0]);}
        Fixture missing=new Fixture(false,true);missing.request(true);missing.invoke(true);assertTrue(missing.counters()[6]+missing.counters()[7]>0);
    }
    @Test void originalFatalErrorsAndHitConstructorFailuresPropagateWithoutCompletedHits()throws Exception{
        for(String point:new String[]{"uuid","stringify","text","hover"})for(Error error:new Error[]{new ThreadDeath(),new StackOverflowError("original")}){
            Fixture f=new Fixture(true,true);f.request(true);Effects.point=point;Effects.failure=error;
            Effects.trace.clear();assertSame(error,assertThrows(InvocationTargetException.class,()->f.invoke(false)).getCause());List<String> expected=new ArrayList<>(Effects.trace);
            Effects.trace.clear();assertSame(error,assertThrows(InvocationTargetException.class,()->f.invoke(true)).getCause());assertEquals(expected,Effects.trace);assertEquals(0,f.counters()[2]);Effects.point=null;
        }
        for(String point:new String[]{"text","hover"}){
            Fixture f=new Fixture(true,true);f.request(true);f.invoke(true);Effects.point=point;Effects.failure=new IllegalStateException("hit constructor");
            Effects.trace.clear();assertSame(Effects.failure,assertThrows(InvocationTargetException.class,()->f.invoke(true)).getCause());assertEquals(1,f.counters()[0]);assertEquals(0,f.counters()[10]);assertFalse(Effects.trace.contains("stringify"));Effects.point=null;
        }
    }
    @Test void oversizedInputsUseOriginalStringificationAndPreserveBoundedStorage()throws Exception{
        Fixture f=new Fixture(true,true);f.request(true);char[] large=new char[4097];Arrays.fill(large,'"');Effects.name=new String(large);
        Object baseline=f.invoke(false);Object first=f.invoke(true),second=f.invoke(true);assertEquals(f.content(baseline),f.content(first));assertEquals(f.content(first),f.content(second));assertNotSame(f.content(first),f.content(second));
        assertEquals("0",f.metadata().getProperty("forwardEntries"));assertEquals(0,f.counters()[0]);assertEquals(2,f.counters()[3]);
    }
    @Test void reentrantConstructorUsesInvocationLocalTicketsAndExportRequiresCompletedHits()throws Exception{
        Fixture f=new Fixture(true,true);f.request(true);Effects.nested=()->{String outer=Effects.name;try{Effects.name="inner";f.invoke(true);}catch(Exception e){throw new AssertionError(e);}finally{Effects.name=outer;}};
        Object outer=f.invoke(true);assertEquals("2",f.metadata().getProperty("forwardEntries"));long[] start=f.counters();assertEquals(f.content(outer),f.content(f.invoke(true)));
        Properties p=new Properties();f.call("exportDelta",new Class<?>[]{Properties.class,long[].class},p,start);assertEquals("true",p.getProperty("forwardCacheActive"));assertEquals("1",p.getProperty("forwardStringifyCallsSkipped"));assertEquals("1",p.getProperty("forwardCompletedHits"));
        assertTrue((Boolean)f.call("stable",new Class<?>[]{long[].class,boolean.class},start,true));f.clear();assertFalse((Boolean)f.call("stable",new Class<?>[]{long[].class,boolean.class},start,true));
    }
}
