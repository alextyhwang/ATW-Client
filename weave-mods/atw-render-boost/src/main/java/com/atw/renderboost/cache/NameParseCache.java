package com.atw.renderboost.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Retains only immutable original-decoded strings, never parser/result objects. */
public final class NameParseCache {
    public interface Access {
        String read(Object compound,String key);
        Object fresh(int bits,String name,String id,String type);
    }
    private static final class Entry {
        final int bits, characters;
        final String name,id,type;
        Entry(int bits,String key,String name,String id,String type) {
            this.bits=bits; this.name=name; this.id=id; this.type=type;
            characters=key.length()+name.length()+id.length()+type.length();
        }
    }
    private final int maximumEntries, maximumKey, maximumCharacters;
    private int characters;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,0.75f,true);
    public long hits,misses,admissions,unsupported,oversized,evictions;
    public NameParseCache(int maximumEntries,int maximumKey,int maximumCharacters) {
        if(maximumEntries<1 || maximumKey<1 || maximumCharacters<1) throw new IllegalArgumentException();
        this.maximumEntries=maximumEntries; this.maximumKey=maximumKey; this.maximumCharacters=maximumCharacters;
    }
    public Object lookup(String key,Access access) {
        if(key==null || key.length()>maximumKey){oversized++; return null;}
        Entry e=entries.get(key);
        if(e==null){misses++;return null;}
        Object result=access.fresh(e.bits,e.name,e.id,e.type); hits++; return result;
    }
    /** Called only after the original decode and original checkcast/local store succeeded. */
    public void record(String key,Object original,Access access) {
        if(key==null || key.length()>maximumKey || entries.containsKey(key)) return;
        int bits=shape(key);
        if(bits==0){unsupported++;return;}
        Entry e=new Entry(bits,key,access.read(original,"name"),access.read(original,"id"),
                (bits&4)==0 ? "" : access.read(original,"type"));
        if(e.characters>maximumCharacters){oversized++;return;}
        while(entries.size()>=maximumEntries || characters+e.characters>maximumCharacters) {
            Iterator<Map.Entry<String,Entry>> i=entries.entrySet().iterator();
            Map.Entry<String,Entry> old=i.next(); characters-=old.getValue().characters; i.remove(); evictions++;
        }
        entries.put(key,e); characters+=e.characters; admissions++;
    }
    public int size(){return entries.size();}
    public int characters(){return characters;}
    public void clear(){entries.clear();characters=0;}
    private static int ws(String s,int i) {
        while(i<s.length() && (s.charAt(i)==' ' || s.charAt(i)=='\t' || s.charAt(i)=='\r' || s.charAt(i)=='\n')) i++;
        return i;
    }
    /** Lexical whole-input validation only. No semantic unescaping or independent decoding. */
    public static int shape(String s) {
        if(s==null)return 0;
        int i=ws(s,0), bits=0;
        if(i>=s.length() || s.charAt(i++)!='{')return 0;
        for(;;) {
            i=ws(s,i); if(i>=s.length())return 0;
            int start=i;
            while(i<s.length() && s.charAt(i)>='a' && s.charAt(i)<='z')i++;
            String key=s.substring(start,i);
            int bit=key.equals("name")?1:key.equals("id")?2:key.equals("type")?4:0;
            if(bit==0 || (bits&bit)!=0)return 0;
            bits|=bit; i=ws(s,i);
            if(i>=s.length() || s.charAt(i++)!=':')return 0;
            i=ws(s,i); if(i>=s.length() || s.charAt(i++)!='"')return 0;
            boolean end=false;
            while(i<s.length()) {
                char c=s.charAt(i++);
                if(c=='"'){end=true;break;}
                if(c=='\\' || c<' ' || c==127)return 0;
            }
            if(!end)return 0;
            i=ws(s,i); if(i>=s.length())return 0;
            char separator=s.charAt(i++);
            if(separator=='}')return (bits&3)==3 && ws(s,i)==s.length()?bits:0;
            if(separator!=',')return 0;
        }
    }
}
