package com.atw.renderboost.hook;

import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual captured producer/NBT bodies execute; consumer/getter/factory stubs isolate the JVM test.
 * This is offline equivalence, never live loaded-factory admission. */
@Tag("private-name-capture") @Tag("private-name-codec-capture")
class ForwardHoverNativeCaptureTest {
    static Map<String,ClassNode> bodies()throws Exception{
        Map<String,ClassNode> result=new HashMap<>();
        for(ClassNode captured:NameParseCaptureTest.captures())if(Arrays.asList(ForwardHoverHook.TARGETS).contains(captured.name)){
            ClassNode c=new ClassNode();c.version=captured.version;c.access=captured.access;c.name=captured.name;c.superName=captured.superName;c.fields=captured.fields;
            for(MethodNode m:captured.methods)if(m.name.equals("<init>")||m.name.equals("toString")||m.name.equals("quoteAndEscape")||m.name.equals("setString")||m.name.equals("getId"))c.methods.add(m);
            result.put(c.name,c);
        }assertEquals(3,result.size());return result;
    }
    @Test void exactCapturedProducerAndNativeStringifierHaveEqualValuesAndFreshStringsOnOffMissAndConsecutiveHits()throws Exception{
        ForwardHoverExecutionTest.Fixture f=new ForwardHoverExecutionTest.Fixture(true,true,ForwardHoverCaptureTest.hover(ForwardHoverCaptureTest.entity()),bodies());
        try{for(String type:new String[]{null,"","minecraft:player"})for(String name:new String[]{"","Aa","BB","§a","\"\\,{}:","\u0000\n\t","\ud83d\ude00","\ud800","\udc00"}){
            ForwardHoverExecutionTest.Effects.type=type;ForwardHoverExecutionTest.Effects.name=name;f.request(false);
            Object baseline=f.invoke(false),again=f.invoke(false),off=f.invoke(true);f.request(true);Object miss=f.invoke(true),hit=f.invoke(true),second=f.invoke(true);
            Set<Object> strings=Collections.newSetFromMap(new IdentityHashMap<>());for(Object value:new Object[]{baseline,again,off,miss,hit,second}){assertEquals(f.content(baseline),f.content(value));assertTrue(strings.add(f.content(value)));}
            assertEquals("1",f.metadata().getProperty("forwardEntries"));assertEquals(f.counters()[0],f.counters()[10]);
        }assertTrue(f.counters()[0]>0);}finally{new ForwardHoverExecutionTest().reset();}
    }
}
