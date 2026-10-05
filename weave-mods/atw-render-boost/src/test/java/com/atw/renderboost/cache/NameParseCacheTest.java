package com.atw.renderboost.cache;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NameParseCacheTest {
    @Test void wholeInputGrammarAcceptsOnlyUniqueFlatStringFields() {
        assertEquals(3,NameParseCache.shape("{name:\"§aLabel\",id:\"uuid\"}"));
        assertEquals(7,NameParseCache.shape(" { type : \"minecraft:player\", id:\"uuid\", name:\"\" } "));
        for(String s:new String[]{null,"", "{}", "{id:\"a\"}", "{name:\"n\",id:1}",
                "{name:n,id:\"a\"}","{name:\"n\",id:\"a\",extra:\"e\"}",
                "{name:\"n\",id:\"a\",id:\"b\"}","{name:[1,\"x\"],id:\"a\"}",
                "{name:{x:\"v\"},id:\"a\"}","{name:\"n\\n\",id:\"a\"}",
                "{name:\"n\\\"\",id:\"a\"}","{name:\"n\",id:\"a\",}",
                "{name:\"n\",id:\"a\"} trailing","{name:'n',id:\"a\"}","{\"name\":\"n\",id:\"a\"}"})
            assertEquals(0,NameParseCache.shape(s));
    }
    static final class Tuple { final String[] fields; Tuple(String... f){fields=f;} }
    static final NameParseCache.Access ACCESS=new NameParseCache.Access() {
        public String read(Object compound,String key){return ((Tuple)compound).fields[key.equals("name")?0:key.equals("id")?1:2];}
        public Object fresh(int bits,String name,String id,String type){return new Tuple(name,id,type);}
    };
    @Test void exactEqualityHitsRetainOriginalFieldsAndReturnFreshObjects() {
        NameParseCache c=new NameParseCache(2,100,300);
        String key="{name:\"Aa\",id:\"u\"}";
        Tuple original=new Tuple("decoded","uuid","");
        c.record(key,original,ACCESS); Object a=c.lookup(new String(key),ACCESS), b=c.lookup(key,ACCESS);
        assertNotSame(original,a); assertNotSame(a,b); assertEquals("decoded",((Tuple)a).fields[0]);
        assertNull(c.lookup("{name:\"BB\",id:\"u\"}",ACCESS));
    }
    @Test void boundedStorageRejectsOversizeEvictsAndClearReleasesStrings() {
        NameParseCache c=new NameParseCache(2,50,100);
        for(int i=0;i<5;i++) c.record("{name:\""+i+"\",id:\"u\"}",new Tuple("n","u",""),ACCESS);
        assertTrue(c.size()<=2); assertTrue(c.characters()<=100);
        c.record("{name:\""+new String(new char[100]).replace('\0','x')+"\",id:\"u\"}",new Tuple("n","u",""),ACCESS);
        assertTrue(c.size()<=2); c.clear(); assertEquals(0,c.size()); assertEquals(0,c.characters());
    }
}
