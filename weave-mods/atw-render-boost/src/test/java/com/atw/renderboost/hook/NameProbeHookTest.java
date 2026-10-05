package com.atw.renderboost.hook;

import com.atw.renderboost.probe.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual production wrappers and collector, not a second test implementation. */
class NameProbeHookTest implements Opcodes {
    static class Clock implements NameProbe.Clock {
        long now=100; public long wall() { return now; } public long cpu() { return now/2; }
    }
    public static final class Effects {
        public static RuntimeException failure; public static Clock clock; public static boolean expire;
        public static void original() { if(expire) clock.now+=NameProbe.DURATION_NS; if(failure!=null) throw failure; }
    }
    static void set(String name,Object value) throws Exception {
        Field f=NameProbeRuntime.class.getDeclaredField(name); f.setAccessible(true); f.set(null,value);
    }
    static NameProbe arm(Clock c) throws Exception {
        NameProbe p=new NameProbe(c); p.start(true); p.frame(c.now);
        set("probe",p); set("owner",Thread.currentThread()); NameProbeRuntime.collecting=true;
        Effects.clock=c; Effects.failure=null; Effects.expire=false; return p;
    }
    @AfterEach void clear() throws Exception {
        NameProbeRuntime.collecting=false; set("probe",null); set("owner",null); Effects.failure=null; Effects.clock=null; Effects.expire=false;
    }
    static long value(Properties p,String key) { return Long.parseLong(p.getProperty(key)); }
    static Class<?> define(ClassNode c) throws Exception {
        for(MethodNode m:c.methods) new Analyzer<>(new BasicVerifier()).analyze(c.name,m);
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS); c.accept(w); byte[] data=w.toByteArray();
        return new ClassLoader(NameProbeHookTest.class.getClassLoader()) {
            Class<?> make() { return defineClass(c.name.replace('/','.'),data,0,data.length); }
        }.make();
    }
    static ClassNode fixture() {
        ClassNode c=new ClassNode(); c.version=V1_8; c.access=ACC_PUBLIC; c.name="fixture/NameBody"; c.superName="java/lang/Object";
        MethodNode ctor=new MethodNode(ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(ALOAD,0)); ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/lang/Object","<init>","()V",false));
        ctor.instructions.add(new InsnNode(RETURN)); ctor.maxLocals=1; ctor.maxStack=1; c.methods.add(ctor);
        MethodNode getter=new MethodNode(ACC_PUBLIC|ACC_STATIC,"getter","()J",null,null);
        getter.instructions.add(new MethodInsnNode(INVOKESTATIC,Type.getInternalName(Effects.class),"original","()V",false));
        getter.instructions.add(new LdcInsnNode(19L)); getter.instructions.add(new InsnNode(LRETURN)); getter.maxStack=2;
        c.methods.add(getter); NameProbeHook.wrap(getter,1);
        MethodNode inner=new MethodNode(ACC_PUBLIC,"inner","(Ljava/lang/Object;)J",null,null);
        inner.instructions.add(new MethodInsnNode(INVOKESTATIC,c.name,"getter","()J",false)); inner.instructions.add(new InsnNode(LRETURN));
        inner.maxLocals=2; inner.maxStack=2; c.methods.add(inner); NameProbeHook.wrap(inner,0);
        MethodNode outer=new MethodNode(ACC_PUBLIC,"outer","(Ljava/lang/Object;)J",null,null);
        outer.instructions.add(new VarInsnNode(ALOAD,0)); outer.instructions.add(new VarInsnNode(ALOAD,1));
        outer.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,c.name,"inner","(Ljava/lang/Object;)J",false)); outer.instructions.add(new InsnNode(LRETURN));
        outer.maxLocals=2; outer.maxStack=2; c.methods.add(outer); NameProbeHook.wrap(outer,0); return c;
    }
    @Test void executableNestedScopesRetainWideReturnAndCountOneOuter() throws Exception {
        Clock c=new Clock(); NameProbe p=arm(c); Class<?> type=define(fixture()); Object receiver=type.getConstructor().newInstance();
        assertEquals(19L,type.getMethod("outer",Object.class).invoke(receiver,new Object()));
        assertEquals(0,p.depth()); Properties s=p.snapshot(); assertEquals(1,value(s,"otherLiving.outerName.calls")); assertEquals(1,value(s,"otherLiving.displayComponent.calls"));
    }
    @Test void executableImplicitExceptionRethrowsSameObjectAndFinallyCleansBothDepths() throws Exception {
        Clock c=new Clock(); NameProbe p=arm(c); Class<?> type=define(fixture()); Object receiver=type.getConstructor().newInstance();
        Effects.failure=new IllegalStateException("original dependency failure");
        InvocationTargetException e=assertThrows(InvocationTargetException.class,()->type.getMethod("outer",Object.class).invoke(receiver,new Object()));
        assertSame(Effects.failure,e.getCause()); assertEquals(0,p.depth());
        assertEquals(1,value(p.snapshot(),"otherLiving.outerName.exceptionalCalls")); assertEquals(1,value(p.snapshot(),"otherLiving.displayComponent.exceptionalCalls"));
        Effects.failure=null; assertEquals(19L,type.getMethod("outer",Object.class).invoke(receiver,new Object())); assertEquals(0,p.depth());
    }
    @Test void executableDisabledWrappersPreserveResultsAndDoNotEnterCollector() throws Exception {
        Clock c=new Clock(); NameProbe p=arm(c); NameProbeRuntime.collecting=false;
        Class<?> type=define(fixture()); assertEquals(19L,type.getMethod("outer",Object.class).invoke(type.getConstructor().newInstance(),new Object()));
        assertEquals(0,value(p.snapshot(),"otherLiving.outerName.calls")); assertEquals(0,p.depth());
    }
    @Test void executableDeadlineDuringOriginalBodyReturnsOriginalValueAndAutomaticallyOff() throws Exception {
        Clock c=new Clock(); NameProbe p=arm(c); Effects.expire=true;
        Class<?> type=define(fixture()); assertEquals(19L,type.getMethod("outer",Object.class).invoke(type.getConstructor().newInstance(),new Object()));
        assertFalse(NameProbeRuntime.collecting); assertFalse(p.active()); assertEquals(0,p.depth()); assertNotNull(p.takeCompleted());
    }
    @Test void unknownShapeAccessAndDescriptorRetainExactOriginalPath() {
        for(int change=0;change<3;change++) {
            ClassNode c=new ClassNode(); c.name=NameProbeHook.TARGETS[0]; c.superName="java/lang/Object";
            MethodNode m=new MethodNode(change==1?ACC_PRIVATE:ACC_PUBLIC,"renderName",change==2?"(Ljava/lang/Object;DDD)V":"(Lnet/minecraft/entity/EntityLivingBase;DDD)V",null,null);
            m.instructions.add(new InsnNode(RETURN)); c.methods.add(m); AbstractInsnNode[] before=m.instructions.toArray();
            new NameProbeHook().transform(c,()->fail("Unknown shape must not request frames"));
            assertArrayEquals(before,m.instructions.toArray()); assertTrue(m.tryCatchBlocks.isEmpty());
        }
    }
}
