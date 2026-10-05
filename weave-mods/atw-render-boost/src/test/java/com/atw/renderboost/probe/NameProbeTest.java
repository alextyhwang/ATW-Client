package com.atw.renderboost.probe;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NameProbeTest {
    static final class Clock implements NameProbe.Clock {
        long now=100, cpu=10; int cpuReads; boolean available=true;
        public long wall() { return now; }
        public long cpu() { cpuReads++; return available ? cpu : -1; }
        void advance(long wall, long cpu) { now+=wall; this.cpu+=cpu; }
    }
    static long value(Properties p,String key) { return Long.parseLong(p.getProperty(key)); }
    static NameProbe collecting(Clock c,boolean timing) {
        NameProbe p=new NameProbe(c); p.start(timing); p.frame(c.now); return p;
    }
    @Test void disabledCollectorRecordsNothingAndReadsNoCpu() {
        Clock c=new Clock(); NameProbe p=new NameProbe(c);
        assertFalse(p.active()); assertEquals(0,p.enter(0,0)); p.exit(0,true); p.frame(c.now); p.frameEnd(c.now);
        assertEquals(0,value(p.snapshot(),"player.outerName.calls")); assertEquals(0,c.cpuReads);
    }
    @Test void overloadsCountOneOuterAndNestedMetricsAreInclusive() {
        Clock c=new Clock(); NameProbe p=collecting(c,true);
        int outer=p.enter(0,0); c.advance(2,1); int overload=p.enter(0,1);
        int bridge=p.enter(1,2); c.advance(5,3); int hover=p.enter(3,2); c.advance(7,4);
        p.exit(hover,false); p.exit(bridge,false); p.exit(overload,false); c.advance(3,2); p.exit(outer,false);
        p.frameEnd(c.now); Properties s=p.snapshot(); assertEquals(1,value(s,"player.outerName.calls"));
        assertEquals(0,value(s,"armorStand.outerName.calls")); assertEquals(17,value(s,"player.outerName.wallNs"));
        assertEquals(12,value(s,"player.displayComponent.wallNs")); assertEquals(7,value(s,"player.hoverEvent.wallNs"));
        assertEquals(10,value(s,"player.outerName.cpuNs")); assertEquals(0,p.depth());
    }
    @Test void callsOutsideNameDoNotContribute() {
        Clock c=new Clock(); NameProbe p=collecting(c,true);
        assertEquals(0,p.enter(1,0)); assertEquals(0,p.enter(4,0)); assertEquals(0,p.enter(3,0));
        assertEquals(0,value(p.snapshot(),"player.displayComponent.calls"));
    }
    @Test void countersControlHasNoCpuOrScopeTiming() {
        Clock c=new Clock(); NameProbe p=collecting(c,false);
        int outer=p.enter(0,1), getter=p.enter(2,0); c.advance(70,20); p.exit(getter,false); p.exit(outer,false); p.frameEnd(c.now);
        Properties s=p.snapshot(); assertEquals(1,value(s,"armorStand.displayName.calls"));
        assertEquals(0,value(s,"armorStand.displayName.timedCalls")); assertEquals(0,c.cpuReads);
        assertEquals(0,value(s,"armorStand.displayName.wallNs"));
    }
    @Test void oneIn32FramesUseSameMaskForAllNestedScopes() {
        Clock c=new Clock(); NameProbe p=new NameProbe(c); p.start(true);
        for(int f=0;f<65;f++) {
            p.frame(c.now); int a=p.enter(0,2), b=p.enter(4,0); c.advance(2,1);
            p.exit(b,false); p.exit(a,false); p.frameEnd(c.now); c.advance(1,1);
        }
        Properties s=p.snapshot(); assertEquals(65,value(s,"otherLiving.outerName.calls"));
        assertEquals(3,value(s,"otherLiving.outerName.timedCalls")); assertEquals(3,value(s,"otherLiving.componentWidth.timedCalls"));
        assertEquals(3,value(s,"sampledFrames"));
    }
    @Test void wallFallbackIsExplicitAndHasNoInventedCpuDuration() {
        Clock c=new Clock(); c.available=false; NameProbe p=collecting(c,true);
        int a=p.enter(0,0); c.advance(22,8); p.exit(a,false); p.frameEnd(c.now);
        Properties s=p.snapshot(); assertEquals(22,value(s,"player.outerName.wallNs"));
        assertEquals(0,value(s,"player.outerName.cpuTimedCalls")); assertEquals(0,value(s,"sampledFrameCpuSamples"));
    }
    @Test void exactDeadlineTurnsOffAndExportsOnlyAfterStop() {
        Clock c=new Clock(); NameProbe p=collecting(c,true); assertNull(p.takeCompleted());
        int a=p.enter(0,0); c.advance(NameProbe.DURATION_NS,1); assertFalse(p.active()); p.exit(a,true);
        assertEquals(0,p.depth()); assertEquals(0,p.enter(0,0));
        Properties s=p.takeCompleted(); assertNotNull(s); assertEquals("false",s.getProperty("active"));
        assertEquals(1,value(s,"abandonedEntries")); assertEquals(0,value(s,"player.outerName.timedCalls")); assertNull(p.takeCompleted());
    }
    @Test void longMethodAndEntryPastDeadlineCannotKeepCollecting() {
        Clock c=new Clock(); NameProbe p=collecting(c,false); int a=p.enter(0,0);
        c.advance(NameProbe.DURATION_NS+7,1); assertEquals(0,p.enter(1,0)); p.exit(a,false);
        assertFalse(p.active()); assertNotNull(p.takeCompleted());
    }
    @Test void incompleteFrameDiscardsPendingTimingSoNameAndFrameDenominatorsMatch() {
        Clock c=new Clock(); NameProbe p=collecting(c,true); int a=p.enter(0,0); c.advance(15,4); p.exit(a,false);
        c.advance(NameProbe.DURATION_NS,1); p.frameEnd(c.now); Properties s=p.takeCompleted();
        assertEquals(1,value(s,"player.outerName.calls")); assertEquals(0,value(s,"player.outerName.timedCalls"));
        assertEquals(0,value(s,"completedSampledFrames")); assertEquals(1,value(s,"abandonedFrames"));
        assertEquals(0,value(s,"sampledFrameCpuNs")); assertEquals(0,value(s,"player.outerName.cpuNs"));
    }
    @Test void exceptionalExitsClearDepthAndNextEntityUsesNewBucket() {
        Clock c=new Clock(); NameProbe p=collecting(c,true); int a=p.enter(0,0), b=p.enter(1,0);
        p.exit(b,true); p.exit(a,true); int d=p.enter(0,1), e=p.enter(4,1); p.exit(e,false); p.exit(d,false);
        Properties s=p.snapshot(); assertEquals(1,value(s,"player.outerName.exceptionalCalls"));
        assertEquals(1,value(s,"player.displayComponent.exceptionalCalls")); assertEquals(1,value(s,"armorStand.componentWidth.calls"));
        assertEquals(0,p.depth());
    }
    @Test void stackAndEntityBucketsStayBoundedAndRecoverAfterOverflow() {
        Clock c=new Clock(); NameProbe p=collecting(c,true); int[] tokens=new int[NameProbe.LIMIT+5];
        tokens[0]=p.enter(0,1000);
        for(int i=1;i<tokens.length;i++) tokens[i]=p.enter(1,0);
        assertEquals(NameProbe.LIMIT,p.depth());
        for(int i=tokens.length-1;i>=0;i--) p.exit(tokens[i],false);
        assertEquals(0,p.depth()); int a=p.enter(0,1); p.exit(a,false);
        Properties s=p.snapshot(); assertEquals(5,value(s,"overflowEntries"));
        assertEquals(1,value(s,"otherLiving.outerName.calls")); assertEquals(1,value(s,"armorStand.outerName.calls"));
        assertEquals(201,s.size());
    }
    @Test void restartClearsAllTotalsAndFrameUnbalanceFailsClosed() {
        Clock c=new Clock(); NameProbe p=collecting(c,true); int a=p.enter(0,0); c.advance(5,4); p.exit(a,false); p.frameEnd(c.now);
        p.stop(); p.takeCompleted(); p.start(false); p.frame(c.now); p.enter(0,0); p.frame(c.now);
        assertFalse(p.active()); Properties s=p.takeCompleted(); assertEquals("unbalanced scope at frame boundary",s.getProperty("stopReason"));
        assertEquals(0,value(s,"sampledFrameCpuNs")); assertEquals(0,value(s,"player.outerName.timedCalls"));
    }
    @Test void runtimeClassHierarchySplitsPlayerSubclassesArmorStandsAndOthersWithoutGetters() throws Exception {
        class Types extends ClassLoader {
            Class<?> make(String name,String parent) {
                org.objectweb.asm.ClassWriter w=new org.objectweb.asm.ClassWriter(0);
                w.visit(52,1,name,null,parent,null);
                org.objectweb.asm.MethodVisitor m=w.visitMethod(1,"<init>","()V",null,null);
                m.visitCode(); m.visitVarInsn(25,0); m.visitMethodInsn(183,parent,"<init>","()V",false);
                m.visitInsn(177); m.visitMaxs(1,1); m.visitEnd(); w.visitEnd(); byte[] bytes=w.toByteArray();
                return defineClass(name.replace('/','.'),bytes,0,bytes.length);
            }
        }
        Types types=new Types(); types.make("net/minecraft/entity/player/EntityPlayer","java/lang/Object");
        Class<?> player=types.make("fixture/PlayerSubclass","net/minecraft/entity/player/EntityPlayer");
        Class<?> stand=types.make("net/minecraft/entity/item/EntityArmorStand","java/lang/Object");
        assertEquals(0,NameProbeRuntime.classify(player.getConstructor().newInstance()));
        assertEquals(1,NameProbeRuntime.classify(stand.getConstructor().newInstance()));
        assertEquals(2,NameProbeRuntime.classify(new Object())); assertEquals(2,NameProbeRuntime.classify(null));
    }
}
