package com.atw.renderboost.hook;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated production runtime, marker-bearing native stubs, independently defined Guava. */
class NameEncodingRuntimeTest implements Opcodes {
    public static final class Boundary {
        static Runnable action;static String point="begin";
        public static void fire(){fire("begin");}
        public static void fire(String at){if(!point.equals(at))return;Runnable a=action;action=null;if(a!=null)a.run();}
        public static Object encode(Object value){NameEncodingHookTest.event("encode");return value.toString();}
    }
    static final class Fixture extends ClassLoader {
        final Map<String,byte[]> bytes=new HashMap<>();final Class<?> runtime,ticket;final Object codec;final Class<?> maps;
        final String marker=NameEncodingHook.MARKER+"$"+UUID.randomUUID().toString().replace("-","");
        Fixture(boolean markers,boolean factoryProof)throws Exception {
            this(markers,factoryProof,null);
        }
        Fixture(boolean markers,boolean factoryProof,MethodNode original)throws Exception {
            super(NameEncodingRuntimeTest.class.getClassLoader());
            NameParseRuntimeTest.Fixture base=new NameParseRuntimeTest.Fixture(true);
            for(Map.Entry<String,byte[]> e:base.bytes.entrySet()) {
                ClassNode c=new ClassNode();new ClassReader(e.getValue()).accept(c,0);
                if(original!=null)executable(c,original);
                if(markers)c.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,marker,"I",null,NameEncodingHook.MARKER_VALUE));
                ClassWriter w=new ClassWriter(original==null?0:ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);c.accept(w);bytes.put(e.getKey(),w.toByteArray());
            }
            ClassNode guava=NameProbeCaptureTest.empty(NameEncodingHook.MAPS,false);
            if(markers)guava.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,marker,"I",null,NameEncodingHook.MARKER_VALUE));
            ClassWriter w=new ClassWriter(0);guava.accept(w);byte[] data=w.toByteArray();
            maps=new ClassLoader(getParent()){Class<?> define(){return defineClass(NameEncodingHook.MAPS.replace('/','.'),data,0,data.length);}}.define();
            runtime=loadClass("com.atw.renderboost.cache.NameEncodingRuntime");ticket=loadClass("com.atw.renderboost.cache.NameEncodingRuntime$Ticket");
            Class<?> converter=loadClass(NameParseHook.CONVERTER.replace('/','.'));Field field=converter.getDeclaredField(NameParseHook.CODEC_FIELD);field.setAccessible(true);codec=field.get(null);
            frame(new Object());
            for(String name:NameEncodingHook.TARGETS)if(!name.equals(NameEncodingHook.MAPS)||factoryProof)evidence(name,true);
        }
        private static void executable(ClassNode c,MethodNode original)throws Exception {
            if(c.name.equals("net/kyori/adventure/util/Codec")) {
                c.access=ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT;c.methods.clear();
                c.methods.add(new MethodNode(ACC_PUBLIC|ACC_ABSTRACT,"encode","(Ljava/lang/Object;)Ljava/lang/Object;",null,null));
            }else if(c.name.equals("net/kyori/adventure/util/Codec$1")) {
                c.superName="java/lang/Object";c.interfaces.add("net/kyori/adventure/util/Codec");
                for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions)if(n instanceof MethodInsnNode&&((MethodInsnNode)n).name.equals("<init>"))((MethodInsnNode)n).owner=c.superName;
                MethodNode encode=new MethodNode(ACC_PUBLIC,"encode","(Ljava/lang/Object;)Ljava/lang/Object;",null,null);
                encode.instructions.add(new VarInsnNode(ALOAD,1));encode.instructions.add(new MethodInsnNode(INVOKESTATIC,Type.getInternalName(Boundary.class),"encode","(Ljava/lang/Object;)Ljava/lang/Object;",false));encode.instructions.add(new InsnNode(ARETURN));c.methods.add(encode);
            }else if(c.name.equals(NameParseHook.CONVERTER)) {
                Map<String,String> mapping=NameEncodingHookTest.mapping(c.name);
                mapping.remove("net/kyori/adventure/util/Codec");mapping.remove(NameEncodingHook.RUNTIME);mapping.remove(NameEncodingHook.RUNTIME+"$Ticket");
                org.objectweb.asm.commons.Remapper remap=new org.objectweb.asm.commons.SimpleRemapper(mapping);
                for(boolean injected:new boolean[]{false,true}) {
                    MethodNode m=new MethodNode(original.access,original.name,original.desc,null,null);original.accept(m);
                    if(injected)NameEncodingHook.inject(m);else m.name="baseline";
                    MethodNode out=new MethodNode(m.access,m.name,remap.mapMethodDesc(m.desc),null,null);m.accept(new org.objectweb.asm.commons.MethodRemapper(out,remap));c.methods.add(out);
                }
            }
        }
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(name.equals(NameEncodingHook.MAPS.replace('/','.')))return maps;
            if(!bytes.containsKey(name)&&!name.startsWith("com.atw.renderboost.cache.NameEncodingRuntime")&&!name.startsWith("com.atw.renderboost.cache.NameParseRuntime"))return super.loadClass(name,resolve);
            Class<?> result=findLoadedClass(name);
            if(result==null){byte[] data=bytes.get(name);
                if(data==null)try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);data=out.toByteArray();}catch(IOException e){throw new ClassNotFoundException(name,e);}
                if(name.equals("com.atw.renderboost.cache.NameEncodingRuntime")) {
                    ClassNode c=new ClassNode();new ClassReader(data).accept(c,0);
                    for(MethodNode m:c.methods)for(AbstractInsnNode n:m.instructions.toArray()) {
                        if(m.name.equals("hit")&&n instanceof TypeInsnNode&&n.getOpcode()==NEW&&((TypeInsnNode)n).desc.equals("java/lang/String"))boundary(m,n,"wrapperAllocation",false);
                        if(!(n instanceof MethodInsnNode))continue;
                        MethodInsnNode call=(MethodInsnNode)n;
                        if(m.name.equals("begin")&&call.name.equals("eligible"))m.instructions.insert(n,new MethodInsnNode(INVOKESTATIC,Type.getInternalName(Boundary.class),"fire","()V",false));
                        if(m.name.equals("eligible")&&call.owner.equals("java/lang/Class")&&call.name.equals("forName"))boundary(m,n,"eligible",false);
                        if(m.name.equals("begin")&&call.name.equals("lookup"))boundary(m,n,"lookup",false);
                        if(m.name.equals("hit")&&call.name.equals("epoch"))boundary(m,n,"hit",false);
                        if(m.name.equals("hit")&&call.owner.equals("java/lang/String")&&call.name.equals("<init>"))boundary(m,n,"wrapper",true);
                        if(m.name.equals("commit")&&call.name.equals("record"))boundary(m,n,"commit",false);
                    }
                    ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);data=w.toByteArray();
                }
                result=defineClass(name,data,0,data.length);
            }if(resolve)resolveClass(result);return result;
        }
        private static void boundary(MethodNode m,AbstractInsnNode at,String point,boolean after) {
            InsnList code=new InsnList();code.add(new LdcInsnNode(point));code.add(new MethodInsnNode(INVOKESTATIC,Type.getInternalName(Boundary.class),"fire","(Ljava/lang/String;)V",false));
            if(after)m.instructions.insert(at,code);else m.instructions.insertBefore(at,code);
        }
        Object call(String method,Class<?>[] types,Object...args)throws Exception{return runtime.getMethod(method,types).invoke(null,args);}
        void evidence(String name,boolean accepted)throws Exception{call("evidence",new Class<?>[]{String.class,boolean.class,boolean.class,String.class},name,accepted,name.equals(NameParseHook.CONVERTER),marker);}
        void frame(Object world)throws Exception{call("frame",new Class<?>[]{Thread.class,Object.class},Thread.currentThread(),world);}
        void request(boolean on)throws Exception{call("request",new Class<?>[]{boolean.class},on);}
        Object begin(Object passed,String name)throws Exception{return call("begin",new Class<?>[]{Object.class,String.class,String.class,boolean.class,String.class},passed,"id","type",true,name);}
        String hit(Object t)throws Exception{return (String)call("hit",new Class<?>[]{ticket},t);}
        void commit(Object t,String text)throws Exception{call("commit",new Class<?>[]{ticket,String.class},t,text);}
        long[] counters()throws Exception{return (long[])call("counters",new Class<?>[0]);}
        boolean active()throws Exception{return (Boolean)call("active",new Class<?>[0]);}
        Properties metadata()throws Exception{Properties p=new Properties();call("metadata",new Class<?>[]{Properties.class},p);return p;}
        void prime()throws Exception{Object t=begin(codec,"n");assertNotNull(t);assertNull(hit(t));commit(t,"original");assertEquals("original",hit(begin(codec,"n")));}
    }
    @Test void defaultOffAndMissingFactoryEvidenceRemainUnavailableDespiteNameProof()throws Exception {
        Fixture f=new Fixture(true,false);assertFalse(f.runtime.getField("requested").getBoolean(null));f.request(true);assertNull(f.begin(f.codec,"n"));
        assertFalse(f.active());assertEquals("false",f.metadata().getProperty("encodingFactoryAdmitted"));assertEquals(0,f.counters()[2]);
        assertFalse(NameEncodingHook.factoryMatches(NameEncodingEvidenceOutputTest.factory(),NameEncodingEvidenceOutputTest.missingFactoryEvidence()));
    }
    @Test void unchangedNameRuntimeStaysActiveOnSameLoadedCodecWithEncodingOffOnAndUnavailable()throws Exception {
        Fixture f=new Fixture(true,false);Class<?> names=f.loadClass("com.atw.renderboost.cache.NameParseRuntime");assertTrue(names.getField("requested").getBoolean(null));
        names.getMethod("frame",Thread.class,Object.class).invoke(null,Thread.currentThread(),new Object());
        for(String owner:NameParseHook.TARGETS)names.getMethod("evidence",String.class,boolean.class,boolean.class).invoke(null,owner,true,owner.equals(NameParseHook.CONVERTER));
        Class<?> compound=f.loadClass("net.minecraft.nbt.NBTTagCompound");Object original=compound.getConstructor().newInstance();compound.getMethod("setString",String.class,String.class).invoke(original,"name","name");compound.getMethod("setString",String.class,String.class).invoke(original,"id","id");
        String key="{name:\"name\",id:\"id\"}";names.getMethod("record",Object.class,String.class,Object.class).invoke(null,f.codec,key,original);
        for(boolean encoding:new boolean[]{false,true,false,true}){f.request(encoding);assertNull(f.begin(f.codec,"n"));assertNotNull(names.getMethod("lookup",Object.class,String.class).invoke(null,f.codec,key));assertTrue((Boolean)names.getMethod("active").invoke(null));}
        assertEquals(4,((long[])names.getMethod("counters").invoke(null))[0]);
    }
    @Test void exactSingletonCommonLoaderAndSeparatelyDefinedGuavaBindWithMeasuredActivity()throws Exception {
        Fixture f=new Fixture(true,true);f.request(true);assertNotSame(f,f.maps.getClassLoader());f.prime();long[] start=f.counters();
        assertEquals("original",f.hit(f.begin(f.codec,"n")));Properties p=new Properties();f.call("exportDelta",new Class<?>[]{Properties.class,long[].class},p,start);
        assertEquals("true",p.getProperty("encodingCacheActive"));assertEquals("1",p.getProperty("encodingEncodeCallsSkipped"));assertEquals("1",p.getProperty("encodingHits"));
        assertTrue((Boolean)f.call("stable",new Class<?>[]{long[].class,boolean.class},start,true));
        Object arbitrary=f.loadClass("net.kyori.adventure.util.Codec$1").getConstructor().newInstance();assertNull(f.begin(arbitrary,"n"));assertFalse(f.active());assertTrue(f.counters()[7]>0);assertNull(f.begin(f.codec,"n"));
        Fixture other=new Fixture(true,true);other.request(true);assertNull(other.begin(f.codec,"n"));assertFalse(other.active());
    }
    @Test void toggleWorldReloadThreadAndEpochTransitionsDiscardStagedAndRetainedEntries()throws Exception {
        Fixture f=new Fixture(true,true);f.request(true);f.prime();Object staged=f.begin(f.codec,"pending");long[] start=f.counters();
        f.call("clear",new Class<?>[0]);f.commit(staged,"must not retain");assertEquals("0",f.metadata().getProperty("encodingEntries"));assertFalse((Boolean)f.call("stable",new Class<?>[]{long[].class,boolean.class},start,true));
        f.prime();f.frame(new Object());assertNull(f.hit(f.begin(f.codec,"n")));f.prime();f.request(false);f.request(true);assertNull(f.hit(f.begin(f.codec,"n")));
        f.prime();f.frame(null);assertNull(f.begin(f.codec,"n"));f.frame(new Object());
        AtomicReference<Throwable> error=new AtomicReference<>();Thread t=new Thread(()->{try{assertNull(f.begin(f.codec,"n"));f.call("frame",new Class<?>[]{Thread.class,Object.class},Thread.currentThread(),new Object());}catch(Throwable e){error.set(e);}});t.start();t.join();assertNull(error.get());assertNull(f.begin(f.codec,"n"));f.frame(new Object());assertNull(f.hit(f.begin(f.codec,"n")));
    }
    @Test void missingMarkersAndRepeatedOrRejectedEvidenceCannotBeBlessedByAPresentMarker()throws Exception {
        Fixture missing=new Fixture(false,true);missing.request(true);assertNull(missing.begin(missing.codec,"n"));assertTrue(missing.counters()[7]>0);
        Fixture f=new Fixture(true,true);f.request(true);f.prime();f.evidence(NameEncodingHook.MAPS,true);assertNull(f.begin(f.codec,"n"));assertFalse(f.active());f.evidence(NameEncodingHook.MAPS,true);assertNull(f.begin(f.codec,"n"));
        f=new Fixture(true,true);f.request(true);f.evidence(NameParseHook.TARGETS[3],false);assertNull(f.begin(f.codec,"n"));
    }
    @Test void epochRaceAfterEligibilityAndBetweenLookupAndHitAlwaysFallsBack()throws Exception {
        Fixture f=new Fixture(true,true);f.request(true);f.prime();long before=f.counters()[0];
        Boundary.action=()->{try{f.evidence(NameParseHook.TARGETS[3],false);}catch(Exception e){throw new AssertionError(e);}};
        assertNull(f.begin(f.codec,"n"));assertEquals(before,f.counters()[0]);
        Fixture second=new Fixture(true,true);second.request(true);second.prime();Object hit=second.begin(second.codec,"n");before=second.counters()[0];second.call("clear",new Class<?>[0]);assertNull(second.hit(hit));assertEquals(before,second.counters()[0]);
    }
    @Test void helperAllocationAndRecordingFailuresLatchFallbackWithoutEscapingOrRepeatingCallbacks()throws Exception {
        Fixture f=new Fixture(true,true);f.request(true);Boundary.action=()->{throw new OutOfMemoryError("controlled helper failure");};assertNull(f.begin(f.codec,"n"));assertEquals(1,f.counters()[6]);assertNull(f.begin(f.codec,"n"));
        Fixture second=new Fixture(true,true);second.request(true);Object staged=second.begin(second.codec,"n");Boundary.point="commit";Boundary.action=()->{throw new IllegalStateException("controlled record failure");};try{second.commit(staged,"original returned value");}finally{Boundary.point="begin";Boundary.action=null;}assertEquals(1,second.counters()[6]);assertEquals("0",second.metadata().getProperty("encodingEntries"));assertNull(second.begin(second.codec,"n"));
    }
    @Test void productionHitsReturnFreshWrappersWithoutChangingStoredString()throws Exception {
        Fixture f=new Fixture(true,true);f.request(true);Object miss=f.begin(f.codec,"n");String original=new String("original");f.commit(miss,original);
        Object ticket=f.begin(f.codec,"n");String first=f.hit(ticket),second=f.hit(f.begin(f.codec,"n"));
        assertEquals(original,first);assertEquals(first,second);assertNotSame(original,first);assertNotSame(original,second);assertNotSame(first,second);
        Field retained=f.ticket.getDeclaredField("hit");retained.setAccessible(true);assertSame(original,retained.get(ticket));assertEquals(2,f.counters()[0]);
    }
    @Test void terminationAndOtherFatalVmErrorsEscapeEveryNestedHelperUnchanged()throws Exception {
        for(String point:new String[]{"eligible","begin","lookup","hit","wrapperAllocation","wrapper","commit"})for(Error fatal:new Error[]{new ThreadDeath(),new StackOverflowError("controlled"),new InternalError("controlled"),new UnknownError("controlled")}) {
            Fixture f=new Fixture(true,true);f.request(true);Object ticket=null;
            if(point.equals("hit")||point.startsWith("wrapper")){f.prime();ticket=f.begin(f.codec,"n");}else if(point.equals("commit"))ticket=f.begin(f.codec,"n");
            final Object staged=ticket;long[] before=f.counters();Boundary.point=point;Boundary.action=()->{throw fatal;};
            try{assertSame(fatal,assertThrows(java.lang.reflect.InvocationTargetException.class,()->{
                if(point.equals("hit")||point.startsWith("wrapper"))f.hit(staged);else if(point.equals("commit"))f.commit(staged,"original");else f.begin(f.codec,"n");
            }).getCause(),point);assertEquals(before[0],f.counters()[0]);assertEquals(before[6],f.counters()[6]);assertEquals(before[7],f.counters()[7]);}
            finally{Boundary.action=null;Boundary.point="begin";}
        }
    }
    @Test void wrapperAllocationFailureAndPostAllocationEpochRaceNeverCountAsAcceptedHits()throws Exception {
        for(boolean allocationFailure:new boolean[]{false,true}) {
            Fixture f=new Fixture(true,true);f.request(true);f.prime();Object ticket=f.begin(f.codec,"n");long[] before=f.counters();Boundary.point=allocationFailure?"wrapperAllocation":"wrapper";
            Boundary.action=()->{if(allocationFailure)throw new OutOfMemoryError("controlled wrapper allocation");try{f.call("clear",new Class<?>[0]);}catch(Exception e){throw new AssertionError(e);}};
            try{assertNull(f.hit(ticket));assertEquals(before[0],f.counters()[0]);assertEquals(before[6]+(allocationFailure?1:0),f.counters()[6]);assertEquals("0",f.metadata().getProperty("encodingEntries"));}
            finally{Boundary.action=null;Boundary.point="begin";}
        }
    }
    @Test void helperMarkerChecksNeverInitializeParserDependencies()throws Exception {
        NameParseRuntimeTest.Boundary.initializations=0;Fixture f=new Fixture(true,true);f.request(true);f.prime();assertEquals(0,NameParseRuntimeTest.Boundary.initializations);
    }
    @Test void oversizedInputsOutputsAndFailedInvocationTicketsCannotRetainEntries()throws Exception {
        Fixture f=new Fixture(true,true);f.request(true);assertNull(f.begin(f.codec,String.join("",Collections.nCopies(4097,"x"))));
        Object t=f.begin(f.codec,"n");f.commit(t,String.join("",Collections.nCopies(8193,"x")));assertEquals(0,f.counters()[2]);assertEquals(2,f.counters()[3]);
        f.begin(f.codec,"failed original invocation");assertEquals("0",f.metadata().getProperty("encodingEntries"));
    }
}
