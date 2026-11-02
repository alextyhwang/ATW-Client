package com.atw.renderboost.cache;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NameEncodingCacheTest {
    private static NameEncodingCache.Key key(String id,String type,boolean present,String name){return new NameEncodingCache.Key(id,type,present,name);}
    @Test void tupleEqualityIncludesPresenceAndEachStringWithoutDelimiterOrHashAmbiguity() {
        NameEncodingCache c=new NameEncodingCache(256,4096,8192,524288);
        NameEncodingCache.Key a=key("a|b","c",false,null),b=key("a","b|c",false,null);
        c.record(a,"first");assertNull(c.lookup(b));assertNull(c.lookup(key("a|b","c",true,"")));
        NameEncodingCache.Key collisionA=key("Aa","t",true,"n"),collisionB=key("BB","t",true,"n");
        assertEquals(collisionA.hashCode(),collisionB.hashCode());c.record(collisionA,"A");c.record(collisionB,"B");
        assertEquals("A",c.lookup(collisionA));assertEquals("B",c.lookup(collisionB));assertEquals(1,c.collisions);
        assertNull(c.lookup(key("Aa","changed",true,"n")));assertNull(c.lookup(key("Aa","t",true,"changed")));
    }
    @Test void accessOrderEvictionAndAllCharacterBoundsAreAccountedWithWideArithmetic() {
        NameEncodingCache c=new NameEncodingCache(2,10,10,18);NameEncodingCache.Key a=key("a","t",false,null),b=key("b","t",false,null),d=key("d","t",false,null);
        c.record(a,"1234");c.record(b,"1234");assertEquals(12,c.characters());c.lookup(a);c.record(d,"1234");
        assertEquals(12,c.characters());assertNull(c.lookup(b));assertEquals("1234",c.lookup(a));assertEquals(1,c.evictions);
        assertFalse(c.supported(key("1234567890","t",false,null)));c.record(a,"12345678901");assertEquals(2,c.oversized);
        NameEncodingCache small=new NameEncodingCache(3,20,20,3);small.record(a,"12");assertEquals(0,small.size());
        c.clear();assertEquals(0,c.size());assertEquals(0,c.characters());
        assertThrows(IllegalArgumentException.class,()->new NameEncodingCache(0,1,1,1));
    }
    @Test void exactOriginalTextIncludingUnpairedSurrogatesIsRetainedUnchanged() {
        NameEncodingCache c=new NameEncodingCache(256,4096,8192,524288);
        for(String s:new String[]{"", "§a", "\"\\", "\u0000\n\t", "\ud83d\ude00", "\ud800", "\udc00"}) {
            NameEncodingCache.Key k=key("id","type",true,s);String output=new String(("{original:"+s+"}").toCharArray());c.record(k,output);assertSame(output,c.lookup(k));
        }
        assertEquals(7,c.size());
    }
}
