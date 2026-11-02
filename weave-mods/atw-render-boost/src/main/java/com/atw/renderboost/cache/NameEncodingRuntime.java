package com.atw.renderboost.cache;

import com.atw.renderboost.hook.NameEncodingHook;
import com.atw.renderboost.hook.NameParseHook;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/** Independent default-OFF cache. Transformer threads publish immutable proof only. */
public final class NameEncodingRuntime {
    public static volatile boolean requested;
    private static final NameEncodingCache CACHE=new NameEncodingCache(256,4096,8192,524288);
    private static final class Proof {
        final int bits,seen;final boolean installed;final String[] markers;
        Proof(int bits,int seen,boolean installed,String[] markers){this.bits=bits;this.seen=seen;this.installed=installed;this.markers=markers;}
    }
    private static volatile Proof proof=new Proof(0,0,false,new String[11]);
    private static final int ALL=(1<<NameEncodingHook.TARGETS.length)-1;
    private static final AtomicLong invalidation=new AtomicLong();
    private static Proof applied;
    private static long version;
    private static Thread thread;
    private static Object world,codec;
    private static ClassLoader loader,factoryLoader;
    private static boolean failed;
    private static long failures,guardRejects,fallbacks,actualHits;
    /** Invocation-local staging. Only the key and successful original String enter the LRU. */
    public static final class Ticket {
        final NameEncodingCache.Key key;final Proof proof;final long version;final String hit;
        Ticket(NameEncodingCache.Key key,Proof proof,long version,String hit){this.key=key;this.proof=proof;this.version=version;this.hit=hit;}
    }
    private NameEncodingRuntime(){}
    /** Termination and fatal VM failures never become cache fallback, even in nested
     * catches. OutOfMemoryError deliberately remains recoverable in these optional
     * helpers: discard/latch the cache and let the original encode path attempt work.
     * This policy does not promise resource-exhaustion or allocation/GC parity. */
    public static void propagateFatal(Throwable error) {
        if(error instanceof ThreadDeath)throw (ThreadDeath)error;
        if(error instanceof VirtualMachineError&&!(error instanceof OutOfMemoryError))throw (VirtualMachineError)error;
    }
    public static void request(boolean on){requested=on;clear();}
    public static void clear(){invalidation.incrementAndGet();if(Thread.currentThread()==thread)refresh();}
    private static void refresh(){Proof p=proof;long v=invalidation.get();if(applied!=p||version!=v){CACHE.clear();codec=null;loader=factoryLoader=null;applied=p;version=v;}}
    public static void frame(Thread owner,Object currentWorld){if(thread!=owner||world!=currentWorld){clear();thread=owner;world=currentWorld;}refresh();}
    public static synchronized void evidence(String owner,boolean accepted,boolean installed,String markerName) {
        int index=Arrays.asList(NameEncodingHook.TARGETS).indexOf(owner);if(index<0)return;
        int bit=1<<index;Proof old=proof;
        // Retransformation/redefinition cannot renew admission from a stale marker.
        accepted=accepted&&(old.seen&bit)==0&&markerName!=null&&markerName.startsWith(NameEncodingHook.MARKER+"$");
        String[] markers=old.markers.clone();markers[index]=accepted?markerName:null;
        proof=new Proof(accepted?old.bits|bit:old.bits&~bit,old.seen|bit,index==0?accepted&&installed:old.installed,markers);
        invalidation.incrementAndGet();
    }
    private static boolean marker(Class<?> type,String name)throws Exception {
        // The unique declaration name binds this loaded class to the published transform
        // event. Reading ConstantValue reflectively would initialize parser dependencies.
        Field f=type.getDeclaredField(name);
        return f.getType()==int.class&&f.isSynthetic()&&f.getModifiers()==(Modifier.PUBLIC|Modifier.STATIC|Modifier.FINAL|0x1000);
    }
    private static boolean eligible(Object passed) {
        if(!requested||failed||Thread.currentThread()!=thread||world==null)return false;
        refresh();if(proof.bits!=ALL||!proof.installed)return false;
        try {if(codec==null) {
            if(passed==null||!passed.getClass().getName().equals("net.kyori.adventure.util.Codec$1"))return reject();
            ClassLoader candidate=passed.getClass().getClassLoader();
            Class<?> converter=Class.forName(NameParseHook.CONVERTER.replace('/','.'),false,candidate);
            Field field=converter.getDeclaredField(NameParseHook.CODEC_FIELD);
            if(field.getModifiers()!=(Modifier.PRIVATE|Modifier.STATIC|Modifier.FINAL))return reject();
            field.setAccessible(true);if(field.get(null)!=passed)return reject();
            for(int index=0;index<NameParseHook.TARGETS.length;index++) {
                String owner=NameParseHook.TARGETS[index];
                Class<?> type=Class.forName(owner.replace('/','.'),false,candidate);
                if(type.getClassLoader()!=candidate||!marker(type,NameParseHook.MARKER)
                        ||!marker(type,proof.markers[index]))return reject();
            }
            // Guava may have a parent defining loader; prove its own marker, independently.
            Class<?> maps=Class.forName(NameEncodingHook.MAPS.replace('/','.'),false,candidate);
            if(!marker(maps,proof.markers[10]))return reject();
            factoryLoader=maps.getClassLoader();codec=passed;loader=candidate;
        }
        return passed==codec&&passed.getClass().getClassLoader()==loader||reject();
        }catch(Throwable error){propagateFatal(error);return reject();}
    }
    private static boolean reject(){guardRejects++;failed=true;clear();return false;}
    private static void failure(){failures++;failed=true;clear();}
    private static boolean epoch(Proof p,long v){return requested&&!failed&&Thread.currentThread()==thread&&world!=null
            &&codec!=null&&loader==codec.getClass().getClassLoader()&&p==proof&&p==applied&&v==version&&v==invalidation.get()&&p.bits==ALL&&p.installed;}
    public static Ticket begin(Object passed,String id,String type,boolean present,String name) {
        try {
            Proof p=proof;long v=invalidation.get();
            if(!eligible(passed)||!epoch(p,v)){fallbacks++;return null;}
            NameEncodingCache.Key key=new NameEncodingCache.Key(id,type,present,name);
            if(!CACHE.supported(key)){fallbacks++;return null;}
            String hit=CACHE.lookup(key);
            if(!epoch(p,v)){refresh();fallbacks++;return null;}
            return new Ticket(key,p,v,hit);
        }catch(Throwable error){propagateFatal(error);failure();fallbacks++;return null;}
    }
    public static String hit(Ticket ticket) {
        try {
            if(ticket!=null&&epoch(ticket.proof,ticket.version)) {
                if(ticket.hit==null)return null;
                // Keep immutable storage shared internally, but preserve the original
                // nonempty stringifier's fresh outward identity through Component.text.
                String fresh=new String(ticket.hit);
                if(epoch(ticket.proof,ticket.version)){actualHits++;return fresh;}
            }
            if(ticket!=null){fallbacks++;refresh();}return null;
        }catch(Throwable error){propagateFatal(error);failure();fallbacks++;return null;}
    }
    /** Called only after original Component.text succeeds, immediately before ARETURN. */
    public static void commit(Ticket ticket,String output) {
        try {if(ticket!=null&&ticket.hit==null&&output!=null&&epoch(ticket.proof,ticket.version)) {
            CACHE.record(ticket.key,output);if(!epoch(ticket.proof,ticket.version))refresh();
        }}catch(Throwable error){propagateFatal(error);failure();}
    }
    public static boolean active(){return codec!=null&&epoch(proof,invalidation.get());}
    public static long[] counters(){return new long[]{actualHits,CACHE.misses,CACHE.admissions,CACHE.oversized,CACHE.evictions,CACHE.collisions,failures,guardRejects,invalidation.get(),fallbacks};}
    public static boolean stable(long[] start,boolean on){return start!=null&&start[8]==invalidation.get()&&(!on||(active()&&start[6]==failures&&start[7]==guardRejects));}
    public static void metadata(Properties p){p.setProperty("encodingRequested",String.valueOf(requested));p.setProperty("encodingAdmitted",String.valueOf(proof.bits==ALL&&proof.installed));
        p.setProperty("encodingActive",String.valueOf(active()));p.setProperty("encodingFailed",String.valueOf(failed));p.setProperty("encodingFactoryAdmitted",String.valueOf((proof.bits&(1<<10))!=0));
        p.setProperty("encodingFactoryLoaderBound",String.valueOf(codec!=null));
        p.setProperty("encodingEntries",String.valueOf(CACHE.size()));p.setProperty("encodingCharacters",String.valueOf(CACHE.characters()));}
    public static void exportDelta(Properties p,long[] start){metadata(p);long[] end=counters();String[] names={"Hits","Misses","Admissions","Oversized","Evictions","Collisions","Failures","GuardRejects","Invalidations","Fallbacks"};
        for(int i=0;i<names.length;i++)p.setProperty("encoding"+names[i],String.valueOf(end[i]-start[i]));
        p.setProperty("encodingCacheActive",String.valueOf(active()&&end[0]>start[0]));p.setProperty("encodingEncodeCallsSkipped",String.valueOf(end[0]-start[0]));}
    public static String status(){return "encodingRequested="+requested+", encodingActive="+active()+", encodingHooks="+Integer.bitCount(proof.bits)+"/11, encodingFactoryAdmitted="+((proof.bits&(1<<10))!=0)
            +", encodingFailed="+failed+", encodingEntries="+CACHE.size()+"/256, encodingCharacters="+CACHE.characters()+"/524288, encodingHits="+actualHits+", encodingMisses="+CACHE.misses+", encodingFailures="+failures;}
}
