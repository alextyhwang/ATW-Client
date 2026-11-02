package com.atw.renderboost.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable full-input/order keys and original successful strings only. */
public final class ForwardHoverCache {
    public static final class Key {
        public final String id,type,name;
        public final int order;
        public Key(String id,String type,String name,int order) {
            this.id=Objects.requireNonNull(id);this.type=type;this.name=Objects.requireNonNull(name);this.order=order;
        }
        long characters(){return (long)id.length()+name.length()+(type==null?0:type.length());}
        @Override public int hashCode(){int h=id.hashCode();h=31*h+(type==null?0:type.hashCode());h=31*h+name.hashCode();return 31*h+order;}
        @Override public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;
            return order==k.order&&id.equals(k.id)&&Objects.equals(type,k.type)&&name.equals(k.name);}
    }
    private final int entries,inputLimit,outputLimit;
    private final long totalLimit;
    private long characters;
    private final LinkedHashMap<Key,String> values=new LinkedHashMap<>(16,.75f,true);
    public long misses,admissions,oversized,evictions,collisions;
    public ForwardHoverCache(int entries,int inputLimit,int outputLimit,long totalLimit){
        if(entries<1||inputLimit<1||outputLimit<1||totalLimit<1)throw new IllegalArgumentException();
        this.entries=entries;this.inputLimit=inputLimit;this.outputLimit=outputLimit;this.totalLimit=totalLimit;
    }
    public boolean supported(Key key){if(key.characters()>inputLimit){oversized++;return false;}return true;}
    public String lookup(Key key){String result=values.get(key);if(result==null)misses++;return result;}
    public void record(Key key,String output){
        long cost=key.characters()+(long)output.length();
        if(key.characters()>inputLimit||output.length()>outputLimit||cost>totalLimit){oversized++;return;}
        if(values.containsKey(key))return;
        for(Key old:values.keySet())if(old.hashCode()==key.hashCode()&&!old.equals(key))collisions++;
        while(values.size()>=entries||characters+cost>totalLimit){Map.Entry<Key,String> oldest=values.entrySet().iterator().next();
            characters-=oldest.getKey().characters()+(long)oldest.getValue().length();values.remove(oldest.getKey());evictions++;}
        values.put(key,output);characters+=cost;admissions++;
    }
    public void clear(){values.clear();characters=0;}
    public int size(){return values.size();}
    public long characters(){return characters;}
}
