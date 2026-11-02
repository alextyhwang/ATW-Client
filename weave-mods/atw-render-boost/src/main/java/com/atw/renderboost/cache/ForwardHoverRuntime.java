package com.atw.renderboost.cache;

import com.atw.renderboost.hook.ForwardHoverHook;
import com.atw.renderboost.hook.NameParseHook;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;

/** Independent default-OFF render-owned cache. No entities/native objects in tickets. */
public final class ForwardHoverRuntime {
    public static volatile boolean requested;
    private static final ForwardHoverCache CACHE=new ForwardHoverCache(256,4096,8192,524288);
    private static final class Proof {
        final int bits,seen;final String[] markers;
        Proof(int bits,int seen,String[] markers){this.bits=bits;this.seen=seen;this.markers=markers;}
    }
    private static volatile Proof proof=new Proof(0,0,new String[5]);
    private static final int ALL=31;
    private static final AtomicLong invalidation=new AtomicLong();
    private static Proof applied;
    private static long version;
    private static Thread thread;
    private static Object world;
    private static Class<?> entity,compound,tag,factory;
    private static Field mapField,dataField;
    private static boolean failed;
    private static long failures,guardRejects,fallbacks,actualHits,completedHits;
    public static final class Ticket {
        final ForwardHoverCache.Key key;final Proof proof;final long version;final String hit;
        private boolean skipped;
        Ticket(ForwardHoverCache.Key key,Proof proof,long version,String hit){this.key=key;this.proof=proof;this.version=version;this.hit=hit;}
    }
    private ForwardHoverRuntime(){}
    public static void request(boolean on){requested=on;clear();}
    public static void clear(){invalidation.incrementAndGet();if(Thread.currentThread()==thread)refresh();}
    private static void refresh(){Proof p=proof;long v=invalidation.get();if(applied!=p||version!=v){
        CACHE.clear();entity=compound=tag=factory=null;mapField=dataField=null;applied=p;version=v;}}
    public static void frame(Thread owner,Object currentWorld){if(thread!=owner||world!=currentWorld){clear();thread=owner;world=currentWorld;}refresh();}
    public static synchronized void evidence(String owner,boolean accepted,String marker){
        int index=Arrays.asList(ForwardHoverHook.TARGETS).indexOf(owner);if(index<0)return;
        Proof old=proof;int bit=1<<index;
        accepted=accepted&&(old.seen&bit)==0&&marker!=null;
        String[] markers=old.markers.clone();markers[index]=accepted?marker:null;
        proof=new Proof(accepted?old.bits|bit:old.bits&~bit,old.seen|bit,markers);invalidation.incrementAndGet();
    }
    private static boolean marker(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);
        return f.getType()==int.class&&f.isSynthetic()&&f.getModifiers()==(Modifier.PUBLIC|Modifier.STATIC|Modifier.FINAL|0x1000);
    }
    private static Field field(Class<?> owner,String name,Class<?> type)throws Exception {
        Field f=owner.getDeclaredField(name);
        if(f.getDeclaringClass()!=owner||f.getType()!=type||f.getModifiers()!=Modifier.PUBLIC)throw new IllegalArgumentException("Unsupported field ownership");
        return f;
    }
    private static boolean eligible(Class<?> caller,Object value)throws Exception {
        if(!requested||failed||Thread.currentThread()!=thread||world==null)return false;
        refresh();if(proof.bits!=ALL)return false;
        if(entity==null){
            if(caller==null||!caller.getName().equals(ForwardHoverHook.ENTITY.replace('/','.')))return reject();
            ClassLoader loader=caller.getClassLoader();Class<?>[] resolved=new Class<?>[5];
            for(int i=0;i<5;i++){
                resolved[i]=Class.forName(ForwardHoverHook.TARGETS[i].replace('/','.'),false,loader);
                if((i<4&&resolved[i].getClassLoader()!=loader)||!marker(resolved[i],proof.markers[i]))return reject();
                if(i>0&&i<4&&!marker(resolved[i],NameParseHook.MARKER))return reject();
            }
            if(resolved[0]!=caller||caller!=Entity.class||resolved[1]!=NBTBase.class||resolved[2]!=NBTTagCompound.class||resolved[3]!=NBTTagString.class
                ||resolved[2].getSuperclass()!=resolved[1]||resolved[3].getSuperclass()!=resolved[1])return reject();
            Field map=field(resolved[2],"tagMap",Map.class),data=field(resolved[3],"data",String.class);
            // Maps is resolved through the compound's actual defining loader, including parent delegation.
            if(Class.forName(ForwardHoverHook.MAPS.replace('/','.'),false,resolved[2].getClassLoader())!=resolved[4])return reject();
            entity=caller;compound=resolved[2];tag=resolved[3];factory=resolved[4];mapField=map;dataField=data;
        }
        return caller==entity&&value!=null&&value.getClass()==compound||reject();
    }
    private static boolean reject(){guardRejects++;failed=true;clear();return false;}
    private static void failure(){failures++;failed=true;clear();}
    private static boolean epoch(Proof p,long v){return requested&&!failed&&Thread.currentThread()==thread&&world!=null
        &&entity!=null&&factory!=null&&p==proof&&p==applied&&v==version&&v==invalidation.get()&&p.bits==ALL;}
    /** Exact bootstrap map/tag classes are checked before traversal/field reads. No getters/serialization. */
    public static int inspect(Object value,String id,String type,String name)throws Exception {
        if(value==null||value.getClass()!=NBTTagCompound.class||mapField==null||dataField==null)return 0;
        // The compile API marks these fields private; captured Lunar fields are public.
        // Read the exact bound declarations, with no NBT getters or accessibility override.
        Object storage=mapField.get(value);if(storage==null||storage.getClass()!=HashMap.class||id==null||name==null)return 0;
        Map<?,?> entries=(Map<?,?>)storage;int count=type==null?2:3;if(entries.size()!=count)return 0;
        int seen=0,order=count,index=0;
        for(Map.Entry<?,?> entry:entries.entrySet()){
            if(index++>=count||entry.getKey()==null||entry.getKey().getClass()!=String.class)return 0;
            String key=(String)entry.getKey(),expected;int code;
            if(key.equals("id")){code=1;expected=id;}else if(key.equals("type")&&type!=null){code=2;expected=type;}
            else if(key.equals("name")){code=3;expected=name;}else return 0;
            if((seen&(1<<code))!=0||entry.getValue()==null||entry.getValue().getClass()!=NBTTagString.class||dataField.get(entry.getValue())!=expected)return 0;
            seen|=1<<code;order=(order<<2)|code;
        }
        return index==count&&seen==(type==null?10:14)?order:0;
    }
    public static Ticket begin(Class<?> caller,Object value,String id,String type,String name){
        try{Proof p=proof;long v=invalidation.get();if(!eligible(caller,value)||!epoch(p,v)){fallbacks++;return null;}
            int order=inspect(value,id,type,name);if(order==0){fallbacks++;return null;}
            ForwardHoverCache.Key key=new ForwardHoverCache.Key(id,type,name,order);if(!CACHE.supported(key)){fallbacks++;return null;}
            String hit=CACHE.lookup(key);if(!epoch(p,v)){refresh();fallbacks++;return null;}return new Ticket(key,p,v,hit);
        }catch(Throwable error){NameEncodingRuntime.propagateFatal(error);failure();fallbacks++;return null;}
    }
    public static String hit(Ticket ticket){
        try{if(ticket!=null&&ticket.hit!=null&&epoch(ticket.proof,ticket.version)){
            String fresh=new String(ticket.hit);if(epoch(ticket.proof,ticket.version))return fresh;}
            if(ticket!=null&&ticket.hit!=null){fallbacks++;refresh();}return null;
        }catch(Throwable error){NameEncodingRuntime.propagateFatal(error);failure();fallbacks++;return null;}
    }
    /** Count only the actual serialization bypass, after original outward NEW instructions. */
    public static String accept(Ticket ticket,String prepared){
        if(ticket==null||prepared==null)return null;
        try{if(epoch(ticket.proof,ticket.version)){ticket.skipped=true;actualHits++;return prepared;}
            fallbacks++;refresh();return null;
        }catch(Throwable error){NameEncodingRuntime.propagateFatal(error);failure();fallbacks++;return null;}
    }
    /** After BOTH outward constructors and probe normal cleanup, outside its catch-all region. */
    public static void commit(Ticket ticket,String output){
        try{if(ticket!=null&&output!=null&&epoch(ticket.proof,ticket.version)){
            if(ticket.skipped)completedHits++;else if(ticket.hit==null)CACHE.record(ticket.key,output);
            if(!epoch(ticket.proof,ticket.version)){CACHE.clear();refresh();}
        }}catch(Throwable error){NameEncodingRuntime.propagateFatal(error);failure();}
    }
    public static boolean active(){return epoch(proof,invalidation.get());}
    public static long[] counters(){return new long[]{actualHits,CACHE.misses,CACHE.admissions,CACHE.oversized,CACHE.evictions,CACHE.collisions,failures,guardRejects,invalidation.get(),fallbacks,completedHits};}
    public static boolean stable(long[] start,boolean on){return start!=null&&start[8]==invalidation.get()&&(!on||(active()&&start[6]==failures&&start[7]==guardRejects));}
    public static void metadata(Properties p){p.setProperty("forwardRequested",String.valueOf(requested));p.setProperty("forwardAdmitted",String.valueOf(proof.bits==ALL));
        p.setProperty("forwardActive",String.valueOf(active()));p.setProperty("forwardFailed",String.valueOf(failed));
        p.setProperty("forwardFactoryLoaderBound",String.valueOf(factory!=null));p.setProperty("forwardEntries",String.valueOf(CACHE.size()));p.setProperty("forwardCharacters",String.valueOf(CACHE.characters()));}
    public static void exportDelta(Properties p,long[] start){metadata(p);long[] end=counters();String[] names={"Hits","Misses","Admissions","Oversized","Evictions","Collisions","Failures","GuardRejects","Invalidations","Fallbacks","CompletedHits"};
        for(int i=0;i<names.length;i++)p.setProperty("forward"+names[i],String.valueOf(end[i]-start[i]));
        p.setProperty("forwardCacheActive",String.valueOf(active()&&end[0]>start[0]&&end[10]>start[10]));p.setProperty("forwardStringifyCallsSkipped",String.valueOf(end[0]-start[0]));}
    public static String status(){return "forwardRequested="+requested+", forwardActive="+active()+", forwardHooks="+Integer.bitCount(proof.bits)+"/5, forwardFailed="+failed
        +", forwardEntries="+CACHE.size()+"/256, forwardCharacters="+CACHE.characters()+"/524288, forwardHits="+actualHits+", forwardCompletedHits="+completedHits+", forwardFailures="+failures;}
}
