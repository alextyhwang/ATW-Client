package com.atw.renderboost.cache;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForwardHoverCacheTest {
    @Test void fullTuplePresenceOrderAndHashCollisionsRemainDistinct(){
        ForwardHoverCache c=new ForwardHoverCache(8,128,128,2048);
        ForwardHoverCache.Key a=new ForwardHoverCache.Key("id",null,"Aa",39),b=new ForwardHoverCache.Key("id",null,"BB",39);
        assertEquals(a.hashCode(),b.hashCode());c.record(a,"first");c.record(b,"second");assertEquals("first",c.lookup(a));assertEquals("second",c.lookup(b));assertEquals(1,c.collisions);
        assertNull(c.lookup(new ForwardHoverCache.Key("other",null,"Aa",39)));assertNull(c.lookup(new ForwardHoverCache.Key("id","","Aa",39)));
        assertNull(c.lookup(new ForwardHoverCache.Key("id",null,"Aa",45)));assertNull(c.lookup(new ForwardHoverCache.Key("id",null,"Aa|",39)));
    }
    @Test void limitsAndLruBoundRetainedUtf16Characters(){
        ForwardHoverCache c=new ForwardHoverCache(2,8,8,20);
        ForwardHoverCache.Key a=new ForwardHoverCache.Key("id",null,"a",1),b=new ForwardHoverCache.Key("id",null,"b",1),d=new ForwardHoverCache.Key("id",null,"d",1);
        c.record(a,"1111");c.record(b,"2222");assertEquals("1111",c.lookup(a));c.record(d,"3333");assertNull(c.lookup(b));assertEquals(2,c.size());assertEquals(14,c.characters());
        assertFalse(c.supported(new ForwardHoverCache.Key("123456789",null,"",1)));c.record(a,"123456789");assertEquals(2,c.size());
        ForwardHoverCache total=new ForwardHoverCache(8,8,8,9);total.record(a,"1234567");assertEquals(0,total.size());
        c.clear();assertEquals(0,c.size());assertEquals(0,c.characters());
    }
}
