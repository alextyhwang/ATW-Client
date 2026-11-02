package com.atw.renderboost.hook;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** The injected encode seam calls the real production runtime, never the test Bridge.
 * Native dependency bodies/markers are fixtures, not admission evidence for live use. */
class NameEncodingProductionExecutionTest {
    static final class Execution {
        final NameEncodingRuntimeTest.Fixture runtime;
        final Object receiver;final Method baseline,patched;
        Execution(MethodNode original)throws Exception {
            runtime=new NameEncodingRuntimeTest.Fixture(true,true,original);
            Class<?> converter=runtime.loadClass(NameParseHook.CONVERTER.replace('/','.'));
            receiver=converter.getConstructor().newInstance();
            baseline=converter.getMethod("baseline",NameEncodingHookTest.ShowEntity.class,NameEncodingHookTest.Encoder.class);
            patched=converter.getMethod("serializeShowEntity",NameEncodingHookTest.ShowEntity.class,NameEncodingHookTest.Encoder.class);
        }
        NameEncodingHookTest.Text invoke(boolean injected,NameEncodingHookTest.ShowEntity input)throws Exception {
            return (NameEncodingHookTest.Text)(injected?patched:baseline).invoke(receiver,input,
                    (NameEncodingHookTest.Encoder)value->{throw new AssertionError("unused caller Encoder invoked");});
        }
    }
    static void parity(MethodNode original)throws Exception {
        Execution e=new Execution(original);
        for(String name:new String[]{null,"","§a","\"\\","\u0000\n\t","\ud83d\ude00","\ud800","\udc00"}) {
            e.runtime.request(false);NameEncodingHookTest.ShowEntity input=NameEncodingHookTest.input("minecraft:player",name);
            NameEncodingHookTest.reset();NameEncodingHookTest.Text baseline=e.invoke(false,input);List<String> expected=new ArrayList<>(NameEncodingHookTest.events);
            NameEncodingHookTest.reset();NameEncodingHookTest.Text baselineAgain=e.invoke(false,input);
            assertEquals(expected,NameEncodingHookTest.events);assertEquals(baseline.value,baselineAgain.value);assertNotSame(baseline.value,baselineAgain.value);
            NameEncodingHookTest.reset();NameEncodingHookTest.Text off=e.invoke(true,input);assertEquals(expected,NameEncodingHookTest.events);assertEquals(baseline.value,off.value);assertNotSame(baseline.value,off.value);
            e.runtime.request(true);long[] before=e.runtime.counters();
            NameEncodingHookTest.reset();NameEncodingHookTest.Text miss=e.invoke(true,input);assertEquals(expected,NameEncodingHookTest.events);
            List<String> onHit=new ArrayList<>(expected);onHit.remove("encode");
            NameEncodingHookTest.reset();NameEncodingHookTest.Text first=e.invoke(true,input);assertEquals(onHit,NameEncodingHookTest.events);
            NameEncodingHookTest.reset();NameEncodingHookTest.Text second=e.invoke(true,input);assertEquals(onHit,NameEncodingHookTest.events);
            Set<Object> components=Collections.newSetFromMap(new IdentityHashMap<>()),contents=Collections.newSetFromMap(new IdentityHashMap<>());
            for(NameEncodingHookTest.Text text:new NameEncodingHookTest.Text[]{baseline,baselineAgain,off,miss,first,second}){assertEquals(baseline.content(),text.content());assertTrue(components.add(text));assertTrue(contents.add(text.content()),"outward content() references must stay fresh");}
            assertEquals(before[0]+2,e.runtime.counters()[0]);assertEquals(before[2]+1,e.runtime.counters()[2]);assertTrue(e.runtime.active());
        }
        NameEncodingHookTest.reset();
    }
    @Test void baselineMissAndConsecutiveHitsExposeFreshContentThroughProductionRuntime()throws Exception {parity(NameEncodingHookTest.body());}
    @Test void terminationAtEveryRuntimeBoundaryPreventsSuccessfulOutwardReturn()throws Exception {
        for(String point:new String[]{"eligible","begin","lookup","hit","wrapperAllocation","wrapper","commit"})for(Error fatal:new Error[]{new ThreadDeath(),new StackOverflowError("controlled")}) {
            Execution e=new Execution(NameEncodingHookTest.body());e.runtime.request(true);NameEncodingHookTest.ShowEntity input=NameEncodingHookTest.input("minecraft:player","§aTermination");
            if(point.equals("hit")||point.startsWith("wrapper")){NameEncodingHookTest.reset();e.invoke(true,input);}
            long[] before=e.runtime.counters();NameEncodingHookTest.reset();NameEncodingRuntimeTest.Boundary.point=point;NameEncodingRuntimeTest.Boundary.action=()->{throw fatal;};
            try {
                assertSame(fatal,assertThrows(InvocationTargetException.class,()->e.invoke(true,input)).getCause(),point);
                assertEquals(before[0],e.runtime.counters()[0]);assertEquals(before[2],e.runtime.counters()[2]);assertEquals(before[6],e.runtime.counters()[6]);
                assertEquals(point.equals("commit")?1:0,Collections.frequency(NameEncodingHookTest.events,"encode"));
                assertEquals(point.equals("commit")?1:0,Collections.frequency(NameEncodingHookTest.events,"text"));
                assertEquals(point.equals("commit")?1:0,Collections.frequency(NameEncodingHookTest.events,"warning"));
            }finally{NameEncodingRuntimeTest.Boundary.action=null;NameEncodingRuntimeTest.Boundary.point="begin";NameEncodingHookTest.reset();}
        }
    }
    @Test void recoverableWrapperFailureOrEpochChangeRejoinsOriginalEncodeExactlyOnce()throws Exception {
        for(boolean allocationFailure:new boolean[]{false,true}) {
            Execution e=new Execution(NameEncodingHookTest.body());e.runtime.request(true);NameEncodingHookTest.ShowEntity input=NameEncodingHookTest.input("minecraft:player","§aFallback");
            NameEncodingHookTest.reset();NameEncodingHookTest.Text miss=e.invoke(true,input);List<String> expected=new ArrayList<>(NameEncodingHookTest.events);long[] before=e.runtime.counters();
            NameEncodingHookTest.reset();NameEncodingRuntimeTest.Boundary.point=allocationFailure?"wrapperAllocation":"wrapper";
            NameEncodingRuntimeTest.Boundary.action=()->{if(allocationFailure)throw new OutOfMemoryError("controlled wrapper allocation");try{e.runtime.call("clear",new Class<?>[0]);}catch(Exception ex){throw new AssertionError(ex);}};
            try {
                NameEncodingHookTest.Text result=e.invoke(true,input);assertEquals(expected,NameEncodingHookTest.events);assertEquals(miss.value,result.value);assertNotSame(miss,result);assertNotSame(miss.value,result.value);
                assertEquals(before[0],e.runtime.counters()[0]);assertEquals(before[6]+(allocationFailure?1:0),e.runtime.counters()[6]);assertEquals("0",e.runtime.metadata().getProperty("encodingEntries"));
            }finally{NameEncodingRuntimeTest.Boundary.action=null;NameEncodingRuntimeTest.Boundary.point="begin";NameEncodingHookTest.reset();}
        }
    }
    @Test void recoverableCommitFailurePreservesOriginalOutwardResultAndCallbacks()throws Exception {
        Execution e=new Execution(NameEncodingHookTest.body());NameEncodingHookTest.ShowEntity input=NameEncodingHookTest.input("minecraft:player","§aRecord");
        NameEncodingHookTest.reset();NameEncodingHookTest.Text baseline=e.invoke(false,input);List<String> expected=new ArrayList<>(NameEncodingHookTest.events);e.runtime.request(true);
        NameEncodingHookTest.reset();NameEncodingRuntimeTest.Boundary.point="commit";NameEncodingRuntimeTest.Boundary.action=()->{throw new IllegalStateException("controlled record failure");};
        try{NameEncodingHookTest.Text result=e.invoke(true,input);assertEquals(baseline.value,result.value);assertNotSame(baseline.value,result.value);assertEquals(expected,NameEncodingHookTest.events);assertEquals(1,e.runtime.counters()[6]);assertEquals("0",e.runtime.metadata().getProperty("encodingEntries"));}
        finally{NameEncodingRuntimeTest.Boundary.action=null;NameEncodingRuntimeTest.Boundary.point="begin";NameEncodingHookTest.reset();}
    }
}
