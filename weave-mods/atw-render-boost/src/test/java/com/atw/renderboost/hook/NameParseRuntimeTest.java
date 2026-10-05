package com.atw.renderboost.hook;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the production helper; only native game classes are dependency stubs. */
class NameParseRuntimeTest implements Opcodes {
    public static final class Boundary {
        static Runnable action;static int initializations;static boolean denyProperty;
        public static void fire(){Runnable a=action;action=null;if(a!=null)a.run();}
        public static void initialized(){initializations++;}
        public static String getProperty(String name,String fallback){if(denyProperty)throw new SecurityException("controlled property denial");return System.getProperty(name,fallback);}
    }
    static final class Fixture extends ClassLoader {
        boolean intercept;
        final Map<String,byte[]> bytes=new HashMap<>();final Class<?> runtime;final Object codec;final Class<?> compound;
        Fixture(boolean markers)throws Exception {
            super(NameParseRuntimeTest.class.getClassLoader());
            for(String name:NameParseHook.TARGETS) {
                ClassNode c=NameProbeCaptureTest.empty(name,false);
                if(name.equals("net/kyori/adventure/util/Codec$1"))c.superName="net/kyori/adventure/util/Codec";
                if(name.equals("net/minecraft/nbt/NBTTagCompound"))c.superName=org.objectweb.asm.Type.getInternalName(NameParseHookTest.Compound.class);
                MethodNode init=new MethodNode(ACC_PUBLIC,"<init>","()V",null,null);init.instructions.add(new VarInsnNode(ALOAD,0));init.instructions.add(new MethodInsnNode(INVOKESPECIAL,c.superName,"<init>","()V",false));init.instructions.add(new InsnNode(RETURN));c.methods.add(init);
                if(markers)c.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_SYNTHETIC,NameParseHook.MARKER,"I",null,NameParseHook.MARKER_VALUE));
                if(name.equals("net/minecraft/nbt/JsonToNBT$Primitive")) {
                    MethodNode clinit=new MethodNode(ACC_STATIC,"<clinit>","()V",null,null);
                    clinit.instructions.add(new MethodInsnNode(INVOKESTATIC,org.objectweb.asm.Type.getInternalName(Boundary.class),"initialized","()V",false));clinit.instructions.add(new InsnNode(RETURN));c.methods.add(clinit);
                }
                if(name.equals(NameParseHook.CONVERTER)) {
                    c.fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC|ACC_FINAL,NameParseHook.CODEC_FIELD,"Lnet/kyori/adventure/util/Codec;",null,null));
                    MethodNode clinit=new MethodNode(ACC_STATIC,"<clinit>","()V",null,null);clinit.instructions.add(new TypeInsnNode(NEW,"net/kyori/adventure/util/Codec$1"));clinit.instructions.add(new InsnNode(DUP));clinit.instructions.add(new MethodInsnNode(INVOKESPECIAL,"net/kyori/adventure/util/Codec$1","<init>","()V",false));clinit.instructions.add(new FieldInsnNode(PUTSTATIC,name,NameParseHook.CODEC_FIELD,"Lnet/kyori/adventure/util/Codec;"));clinit.instructions.add(new InsnNode(RETURN));c.methods.add(clinit);
                }
                ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);bytes.put(name.replace('/','.'),w.toByteArray());
            }
            runtime=loadClass("com.atw.renderboost.cache.NameParseRuntime");compound=loadClass("net.minecraft.nbt.NBTTagCompound");
            Class<?> converter=loadClass(NameParseHook.CONVERTER.replace('/','.'));Field field=converter.getDeclaredField(NameParseHook.CODEC_FIELD);field.setAccessible(true);codec=field.get(null);
            call("frame",new Class<?>[]{Thread.class,Object.class},Thread.currentThread(),new Object());
            for(String name:NameParseHook.TARGETS)call("evidence",new Class<?>[]{String.class,boolean.class,boolean.class},name,true,name.equals(NameParseHook.CONVERTER));
        }
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(!bytes.containsKey(name) && !name.startsWith("com.atw.renderboost.cache.NameParseRuntime"))return super.loadClass(name,resolve);
            Class<?> result=findLoadedClass(name);
            if(result==null) {
                byte[] b=bytes.get(name);
                if(b==null)try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);b=out.toByteArray();}catch(IOException e){throw new ClassNotFoundException(name,e);}
                if(name.equals("com.atw.renderboost.cache.NameParseRuntime")) {
                    ClassNode c=new ClassNode();new ClassReader(b).accept(c,0);
                    for(MethodNode m:c.methods)if(m.name.equals("lookup"))for(AbstractInsnNode i:m.instructions.toArray())
                        if(i instanceof MethodInsnNode && ((MethodInsnNode)i).name.equals("eligible"))
                            m.instructions.insert(i,new MethodInsnNode(INVOKESTATIC,org.objectweb.asm.Type.getInternalName(Boundary.class),"fire","()V",false));
                    for(MethodNode m:c.methods)if(m.name.equals("startupRequest"))for(AbstractInsnNode i:m.instructions.toArray())
                        if(i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals("java/lang/System") && ((MethodInsnNode)i).name.equals("getProperty"))
                            ((MethodInsnNode)i).owner=org.objectweb.asm.Type.getInternalName(Boundary.class);
                    ClassWriter w=new ClassWriter(0);c.accept(w);b=w.toByteArray();
                }
                result=defineClass(name,b,0,b.length);
            }
            if(resolve)resolveClass(result);return result;
        }
        Object call(String method,Class<?>[] types,Object...args)throws Exception{return runtime.getMethod(method,types).invoke(null,args);}
        Object lookup(Object codec,String key)throws Exception{return call("lookup",new Class<?>[]{Object.class,String.class},codec,key);}
        void request(boolean on)throws Exception{call("request",new Class<?>[]{boolean.class},on);}
        Object original()throws Exception{Object c=compound.getConstructor().newInstance();compound.getMethod("setString",String.class,String.class).invoke(c,"name","native decoded");compound.getMethod("setString",String.class,String.class).invoke(c,"id","uuid");return c;}
        void record(String key,Object original)throws Exception{call("record",new Class<?>[]{Object.class,String.class,Object.class},codec,key,original);}
        long[] counters()throws Exception{return (long[])call("counters",new Class<?>[0]);}
    }
    @Test void explicitOffSingletonIdentityFreshnessAndWorldLifecycleExecuteProductionHelper()throws Exception {
        Fixture f=new Fixture(true);f.request(false);String key="{name:\"n\",id:\"i\"}";Object original=f.original();
        assertNull(f.lookup(f.codec,key));f.record(key,original);assertEquals(0,f.counters()[2]);
        f.request(true);assertNull(f.lookup(f.codec,key));f.record(key,original);
        Object a=f.lookup(f.codec,key),b=f.lookup(f.codec,key);assertNotNull(a);assertNotSame(original,a);assertNotSame(a,b);
        assertNotSame(((NameParseHookTest.Compound)original).values.get("name"),((NameParseHookTest.Compound)a).values.get("name"));
        Object other=f.loadClass("net.kyori.adventure.util.Codec$1").getConstructor().newInstance();assertNull(f.lookup(other,key));assertNull(f.lookup(f.codec,key));
        f.record(key,original);assertNotNull(f.lookup(f.codec,key));
        f.call("frame",new Class<?>[]{Thread.class,Object.class},Thread.currentThread(),new Object());assertNull(f.lookup(f.codec,key));
        f.record(key,original);f.request(false);f.request(true);assertNull(f.lookup(f.codec,key));
    }
    @ParameterizedTest @CsvSource(value={"<absent>,true","true,true","false,false","FALSE,false","invalid,false","<empty>,false"})
    void startupPropertyControlsOnlyInitialRequestAndSessionCommandsStayNonpersistent(String value,boolean expected)throws Exception {
        String old=System.getProperty("atwboost.names");
        try {
            if(value.equals("<absent>"))System.clearProperty("atwboost.names");else System.setProperty("atwboost.names",value.equals("<empty>")?"":value);
            String initial=System.getProperty("atwboost.names");Fixture f=new Fixture(true);
            assertEquals(expected,f.runtime.getField("requested").getBoolean(null));
            assertFalse((Boolean)f.call("active",new Class<?>[0]),"request alone does not establish singleton/cache activation");
            f.request(!expected);assertEquals(!expected,f.runtime.getField("requested").getBoolean(null));
            assertEquals(initial,System.getProperty("atwboost.names"));
            assertEquals(expected,new Fixture(true).runtime.getField("requested").getBoolean(null),"new startup uses original preference");
        }finally{if(old==null)System.clearProperty("atwboost.names");else System.setProperty("atwboost.names",old);}
    }
    @Test void defaultRequestRequiresAllExistingRuntimeGuardsBeforeAnyCacheActivity()throws Exception {
        String old=System.getProperty("atwboost.names");
        try {
            System.clearProperty("atwboost.names");Fixture f=new Fixture(false);String key="{name:\"n\",id:\"i\"}";
            assertTrue(f.runtime.getField("requested").getBoolean(null));f.record(key,f.original());assertNull(f.lookup(f.codec,key));assertEquals(0,f.counters()[0]);assertEquals(0,f.counters()[2]);
        }finally{if(old==null)System.clearProperty("atwboost.names");else System.setProperty("atwboost.names",old);}
    }
    @Test void deniedStartupPropertyFallsBackOffAndCommandsRemainAvailable()throws Exception {
        Boundary.denyProperty=true;
        try{Fixture f=new Fixture(true);assertFalse(f.runtime.getField("requested").getBoolean(null));f.request(true);assertTrue(f.runtime.getField("requested").getBoolean(null));}
        finally{Boundary.denyProperty=false;}
    }
    @Test void missingLoadedBodyMarkersAndUnknownProofNeverEnable()throws Exception {
        Fixture f=new Fixture(false);f.request(true);String key="{name:\"n\",id:\"i\"}";f.record(key,f.original());assertNull(f.lookup(f.codec,key));assertTrue(f.counters()[7]>0);
        f=new Fixture(true);f.request(true);f.call("evidence",new Class<?>[]{String.class,boolean.class,boolean.class},NameParseHook.TARGETS[3],false,false);f.record(key,f.original());assertNull(f.lookup(f.codec,key));
    }
    @Test void backgroundEvidenceQueuesInvalidationWithoutTouchingCachedStringsUntilOwnerBoundary()throws Exception {
        Fixture f=new Fixture(true);f.request(true);String key="{name:\"n\",id:\"i\"}";f.record(key,f.original());assertNotNull(f.lookup(f.codec,key));long[] before=f.counters();
        AtomicReference<Throwable> error=new AtomicReference<>();Thread t=new Thread(()->{try{f.call("evidence",new Class<?>[]{String.class,boolean.class,boolean.class},NameParseHook.TARGETS[3],false,false);}catch(Throwable e){error.set(e);}});t.start();t.join();assertNull(error.get());
        assertFalse((Boolean)f.call("stable",new Class<?>[]{long[].class,boolean.class},before,true));assertNull(f.lookup(f.codec,key));assertFalse((Boolean)f.call("active",new Class<?>[0]));
    }
    @Test void benchmarkReportsMeasuredActivityAndRejectsInvalidationTransitions()throws Exception {
        Fixture f=new Fixture(true);f.request(true);String key="{name:\"n\",id:\"i\"}";f.record(key,f.original());assertNotNull(f.lookup(f.codec,key));long[] start=f.counters();
        assertNotNull(f.lookup(f.codec,key));assertNull(f.lookup(f.codec,"{name:\"other\",id:\"i\"}"));
        Properties p=new Properties();f.call("exportDelta",new Class<?>[]{Properties.class,long[].class},p,start);
        assertEquals("true",p.getProperty("namesRequested"));assertEquals("true",p.getProperty("namesCacheActive"));assertEquals("1",p.getProperty("nameParseHits"));assertEquals("1",p.getProperty("nameParseMisses"));
        f.call("clear",new Class<?>[0]);assertFalse((Boolean)f.call("stable",new Class<?>[]{long[].class,boolean.class},start,true));
    }
    @Test void evidenceRejectionExactlyAfterEligibilityCannotReturnAnOldCacheHit()throws Exception {
        Fixture f=new Fixture(true);f.request(true);String key="{name:\"n\",id:\"i\"}";f.record(key,f.original());assertNotNull(f.lookup(f.codec,key));long hits=f.counters()[0];
        Boundary.action=()-> {
            Thread t=new Thread(()->{try{f.call("evidence",new Class<?>[]{String.class,boolean.class,boolean.class},NameParseHook.TARGETS[3],false,false);}catch(Exception e){throw new AssertionError(e);}});
            t.start();try{t.join();}catch(InterruptedException e){throw new AssertionError(e);}
        };
        try{assertNull(f.lookup(f.codec,key));assertEquals(hits,f.counters()[0]);}finally{Boundary.action=null;}
    }
    @Test void proofMarkerMetadataChecksDoNotInitializeParserClassesBeforeOriginalDecode()throws Exception {
        Boundary.initializations=0;Fixture f=new Fixture(true);f.request(true);String key="{name:\"n\",id:\"i\"}";
        f.record(key,f.original());assertNotNull(f.lookup(f.codec,key));assertEquals(0,Boundary.initializations);
        Class.forName("net.minecraft.nbt.JsonToNBT$Primitive",true,f);assertEquals(1,Boundary.initializations);
    }
    @Test void failedFreshAllocationLatchesFallbackAndNeverEscapesToOriginalCaller()throws Exception {
        Fixture f=new Fixture(true);f.request(true);String key="{name:\"n\",id:\"i\"}";f.record(key,f.original());
        // Unknown return objects bypass recording without interfering with the original caller.
        Object wrong=new Object();f.call("record",new Class<?>[]{Object.class,String.class,Object.class},f.codec,key,wrong);assertNotNull(f.lookup(f.codec,key));
        NameParseHookTest.Effects.readFailure=new IllegalStateException("injected native helper failure");
        try{f.record("{name:\"new\",id:\"i\"}",f.original());}finally{NameParseHookTest.Effects.readFailure=null;}
        assertNull(f.lookup(f.codec,key));assertTrue(f.counters()[6]>0);
    }
}
