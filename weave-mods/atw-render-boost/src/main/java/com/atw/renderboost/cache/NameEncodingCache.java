package com.atw.renderboost.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Render-owned immutable tuple/String LRU; never retains native objects. */
public final class NameEncodingCache {
    public static final class Key {
        public final String id,type,name;
        public final boolean present;
        public Key(String id,String type,boolean present,String name) {
            this.id=Objects.requireNonNull(id);this.type=Objects.requireNonNull(type);
            this.present=present;this.name=present?Objects.requireNonNull(name):null;
        }
        long characters(){return (long)id.length()+type.length()+(present?name.length():0);}
        @Override public int hashCode(){
            // Same Objects.hash tuple order/seed, without its per-call varargs array.
            int hash=1;
            hash=31*hash+id.hashCode();
            hash=31*hash+type.hashCode();
            hash=31*hash+Boolean.hashCode(present);
            return 31*hash+Objects.hashCode(name);
        }
        @Override public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;
            return present==k.present&&id.equals(k.id)&&type.equals(k.type)&&Objects.equals(name,k.name);}
    }
    private final int entries,inputLimit,outputLimit;
    private final long totalLimit;
    private long characters;
    private final LinkedHashMap<Key,String> values=new LinkedHashMap<>(16,.75f,true);
    public long hits,misses,admissions,oversized,evictions,collisions;
    public NameEncodingCache(int entries,int inputLimit,int outputLimit,long totalLimit) {
        if(entries<1||inputLimit<1||outputLimit<1||totalLimit<1)throw new IllegalArgumentException();
        this.entries=entries;this.inputLimit=inputLimit;this.outputLimit=outputLimit;this.totalLimit=totalLimit;
    }
    public boolean supported(Key key){if(key.characters()>inputLimit){oversized++;return false;}return true;}
    public String lookup(Key key){String result=values.get(key);if(result==null)misses++;else hits++;return result;}
    public void record(Key key,String output) {
        long cost=key.characters()+(long)output.length();
        if(key.characters()>inputLimit||output.length()>outputLimit||cost>totalLimit){oversized++;return;}
        if(values.containsKey(key))return;
        // Equality remains authoritative even when two immutable tuples share a hash.
        for(Key existing:values.keySet())if(existing.hashCode()==key.hashCode()&&!existing.equals(key))collisions++;
        while(values.size()>=entries||characters+cost>totalLimit){Map.Entry<Key,String> oldest=values.entrySet().iterator().next();
            characters-=oldest.getKey().characters()+(long)oldest.getValue().length();values.remove(oldest.getKey());evictions++;}
        values.put(key,output);characters+=cost;admissions++;
    }
    public void clear(){values.clear();characters=0;}
    public int size(){return values.size();}
    public long characters(){return characters;}
}
