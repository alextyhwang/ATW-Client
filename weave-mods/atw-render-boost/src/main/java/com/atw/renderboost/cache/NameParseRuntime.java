package com.atw.renderboost.cache;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.NBTTagCompound;

/** Render-thread owned, guarded parse cache. Helpers fail back to original decode. */
public final class NameParseRuntime {
    /** Startup defaults ON; -Datwboost.names=false forces OFF. Session commands never persist. */
    public static volatile boolean requested=startupRequest();
    private static boolean startupRequest() {
        try{return Boolean.parseBoolean(System.getProperty("atwboost.names","true"));}
        catch(RuntimeException | LinkageError unavailable){return false;}
    }
    private static final NameParseCache CACHE=new NameParseCache(256,4096,524288);
    private static final class Proof {
        final int bits;final boolean installed;
        Proof(int bits,boolean installed){this.bits=bits;this.installed=installed;}
    }
    private static volatile Proof proof=new Proof(0,false);
    private static Proof appliedProof;
    private static final AtomicLong invalidation=new AtomicLong();
    private static long appliedInvalidation;
    private static Thread thread;
    private static Object world,codec;
    private static ClassLoader loader;
    private static boolean failed;
    private static long failures,guardRejects;
    private static final NameParseCache.Access ACCESS=new NameParseCache.Access() {
        public String read(Object compound,String key){return ((NBTTagCompound)compound).getString(key);}
        public Object fresh(int bits,String name,String id,String type) {
            NBTTagCompound result=new NBTTagCompound();
            result.setString("name",name); result.setString("id",id);
            if((bits&4)!=0)result.setString("type",type);
            return result;
        }
    };
    private NameParseRuntime(){}
    public static void request(boolean value){requested=value;clear();}
    public static void clear(){invalidation.incrementAndGet();if(Thread.currentThread()==thread)refresh();}
    private static void refresh() {
        long version=invalidation.get();Proof p=proof;
        if(appliedInvalidation!=version || appliedProof!=p) {
            CACHE.clear();codec=null;loader=null;appliedInvalidation=version;appliedProof=p;
        }
    }
    /** Called by the existing game-loop hook; does not resolve game classes at startup. */
    public static void frame(Thread owner,Object currentWorld) {
        if(thread!=owner || world!=currentWorld){clear();thread=owner;world=currentWorld;}
        refresh();
    }
    public static synchronized void evidence(String owner,boolean accepted,boolean converterInstalled) {
        int index=java.util.Arrays.asList(com.atw.renderboost.hook.NameParseHook.TARGETS).indexOf(owner);
        if(index<0)return;
        Proof old=proof;int bit=1<<index;
        proof=new Proof(accepted?old.bits|bit:old.bits&~bit,index==0?accepted&&converterInstalled:old.installed);
        // Transformer threads never touch the render-owned map/world/codec.
        invalidation.incrementAndGet();
    }
    private static boolean eligible(Object passed) {
        if(!requested || failed || thread!=Thread.currentThread() || world==null)return false;
        refresh();Proof p=proof;
        if(!p.installed || p.bits!=(1<<com.atw.renderboost.hook.NameParseHook.TARGETS.length)-1)return false;
        if(codec==null) {
            try {
                ClassLoader candidate=passed.getClass().getClassLoader();
                if(!passed.getClass().getName().equals("net.kyori.adventure.util.Codec$1"))return reject();
                Class<?> converter=Class.forName(com.atw.renderboost.hook.NameParseHook.CONVERTER.replace('/','.'),false,candidate);
                Field f=converter.getDeclaredField(com.atw.renderboost.hook.NameParseHook.CODEC_FIELD);
                if(!Modifier.isPrivate(f.getModifiers()) || !Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers()))return reject();
                f.setAccessible(true);
                if(f.get(null)!=passed || converter.getClassLoader()!=candidate
                        || NBTTagCompound.class.getClassLoader()!=candidate)return reject();
                for(String name:com.atw.renderboost.hook.NameParseHook.TARGETS) {
                    Class<?> type=Class.forName(name.replace('/','.'),false,candidate);
                    if(type.getClassLoader()!=candidate)return reject();
                    Field marker=type.getDeclaredField(com.atw.renderboost.hook.NameParseHook.MARKER);
                    if(marker.getType()!=int.class || !marker.isSynthetic() || marker.getModifiers()!=(Modifier.PUBLIC|Modifier.STATIC|Modifier.FINAL|0x1000)
                            )return reject();
                }
                codec=passed;loader=candidate;
            }catch(Throwable ignored){return reject();}
        }
        return passed==codec && passed.getClass().getClassLoader()==loader || reject();
    }
    private static boolean reject(){clear();codec=null;loader=null;guardRejects++;return false;}
    private static void failure(){failed=true;failures++;clear();}
    public static Object lookup(Object passed,String key) {
        try {
            Proof before=proof;long version=invalidation.get();
            if(!eligible(passed))return null;
            if(!sameEpoch(before,version)){refresh();return null;}
            Object result=CACHE.lookup(key,ACCESS);
            if(!sameEpoch(before,version)){refresh();return null;}
            return result;
        }
        catch(Throwable ignored){failure();return null;}
    }
    public static void record(Object passed,String key,Object original) {
        try {
            Proof before=proof;long version=invalidation.get();
            if(eligible(passed) && sameEpoch(before,version) && original!=null && original.getClass()==NBTTagCompound.class) {
                CACHE.record(key,original,ACCESS);
                if(!sameEpoch(before,version))refresh();
            }
        }
        catch(Throwable ignored){failure();}
    }
    private static boolean sameEpoch(Proof p,long version) {
        return requested && p==proof && p==appliedProof && version==invalidation.get() && version==appliedInvalidation
                && p.installed && p.bits==(1<<com.atw.renderboost.hook.NameParseHook.TARGETS.length)-1;
    }
    public static boolean active(){Proof p=proof;return requested&&!failed&&p.installed&&codec!=null&&p==appliedProof&&appliedInvalidation==invalidation.get()&&p.bits==(1<<com.atw.renderboost.hook.NameParseHook.TARGETS.length)-1;}
    public static long[] counters(){return new long[]{CACHE.hits,CACHE.misses,CACHE.admissions,CACHE.unsupported,CACHE.oversized,CACHE.evictions,failures,guardRejects,invalidation.get()};}
    public static boolean stable(long[] start,boolean on) {
        return start!=null && start[8]==invalidation.get() && (!on || (active() && start[6]==failures && start[7]==guardRejects));
    }
    public static void exportDelta(Properties p,long[] start) {
        metadata(p);long[] end=counters();
        String[] names={"nameParseHits","nameParseMisses","nameParseAdmissions","nameParseUnsupported","nameParseOversized","nameParseEvictions","nameParseFailures","nameParseGuardRejects","nameParseInvalidations"};
        for(int i=0;i<names.length;i++)p.setProperty(names[i],String.valueOf(end[i]-start[i]));
        p.setProperty("namesCacheActive",String.valueOf(active() && end[0]>start[0]));
    }
    public static String status(){return "namesRequested="+requested+", namesActive="+active()+", namesHooks="+Integer.bitCount(proof.bits)+"/"+com.atw.renderboost.hook.NameParseHook.TARGETS.length+", namesFailed="+failed+", nameEntries="+CACHE.size()+"/256, nameCharacters="+CACHE.characters()+"/524288, nameHits="+CACHE.hits+", nameMisses="+CACHE.misses+", nameUnsupported="+CACHE.unsupported+", nameFailures="+failures;}
    public static void metadata(Properties p) {
        p.setProperty("namesRequested",String.valueOf(requested));p.setProperty("namesActive",String.valueOf(active()));
        p.setProperty("namesHooksInstalled",String.valueOf(proof.bits==(1<<com.atw.renderboost.hook.NameParseHook.TARGETS.length)-1&&proof.installed));
        p.setProperty("namesFailed",String.valueOf(failed));p.setProperty("nameEntries",String.valueOf(CACHE.size()));
        p.setProperty("nameCharacters",String.valueOf(CACHE.characters()));
    }
}
