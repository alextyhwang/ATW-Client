package com.atw.optimalzone.render;

import com.atw.optimalzone.OverlayPlayerClassifier;
import com.atw.optimalzone.render.ProjectileTrajectoryRenderer;
import org.junit.jupiter.api.Test;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import sun.misc.Unsafe;
import com.mojang.authlib.GameProfile;

import java.lang.reflect.*;
import java.util.*;

/** Headless calls to the real production computeTrajectory; no Minecraft constructor or GL. */
public final class TrajectoryPreparationParityTest {
    static final Unsafe U;
    static final Method BASE, CAND, BASE_FIND, CAND_FIND;
    static final Object baseline = new BaselineProjectileTrajectoryRenderer(null);
    static final Object candidate = new ProjectileTrajectoryRenderer(null);
    static final Thread OWNER = Thread.currentThread();
    static volatile long sink;
    static int assertions, comparisons, fullComparisons, segmentComparisons;
    static boolean counting = true;
    static long invis, alive, boxes, offsets, expands, intercepts, uuidLookups, nameLookups, traces;
    static final List<String> calls = new ArrayList<>();
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); U = (Unsafe) f.get(null);
            BASE = method(BaselineProjectileTrajectoryRenderer.class, "computeTrajectory", Minecraft.class, EntityPlayerSP.class, float.class);
            CAND = method(ProjectileTrajectoryRenderer.class, "computeTrajectory", Minecraft.class, EntityPlayerSP.class, float.class);
            BASE_FIND = method(BaselineProjectileTrajectoryRenderer.class, "findPlayerHit", Minecraft.class, EntityPlayerSP.class, Vec3.class, Vec3.class, float.class);
            CAND_FIND = method(ProjectileTrajectoryRenderer.class, "findPlayerHit", Minecraft.class, EntityPlayerSP.class, Vec3.class, Vec3.class, float.class, TrajectoryPreparation.Scratch.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method m = type.getDeclaredMethod(name, args); m.setAccessible(true); return m;
    }
    static Object field(Object value, String name) throws Exception {
        Field f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }
    static void put(Object object, Class<?> declaring, String name, Object value) throws Exception {
        Field f = declaring.getDeclaredField(name); f.setAccessible(true); f.set(object,value);
    }
    static <T> T allocate(Class<T> type) throws Exception { return type.cast(U.allocateInstance(type)); }
    static void owner() { if (Thread.currentThread() != OWNER) throw new AssertionError("Off-owner fixture read"); }
    static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
    static void reset() { invis=alive=boxes=offsets=expands=intercepts=uuidLookups=nameLookups=traces=0; calls.clear(); }
    static String counts() {
        return "traces="+traces+",invis="+invis+",alive="+alive+",boxes="+boxes+",offsets="+offsets+
                ",expands="+expands+",intercepts="+intercepts+",uuid="+uuidLookups+",name="+nameLookups;
    }

    public static class Player extends EntityPlayerSP {
        AxisAlignedBB box;
        UUID uuid;
        String name;
        boolean invisible, living=true, using=true, changingBox;
        int draw=20, boxCalls;
        ItemStack held;
        Runnable boxHook, heldHook;
        // These constructors are never invoked; Unsafe avoids world/game/native initialization.
        public Player() { super(null,null,null,null); }
        @Override public boolean isInvisible() { owner(); calls.add("invis:"+name); if(counting) invis++; return invisible; }
        @Override public boolean isEntityAlive() { owner(); calls.add("alive:"+name); if(counting) alive++; return living; }
        @Override public AxisAlignedBB getEntityBoundingBox() {
            owner(); calls.add("box:"+name); if(counting) boxes++; boxCalls++;
            if (boxHook != null) boxHook.run();
            if(changingBox && boxCalls > 1) return new AxisAlignedBB(100,100,100,101,101,101);
            return box;
        }
        @Override public UUID getUniqueID() { owner(); calls.add("uuid:"+name); return uuid; }
        @Override public String getName() { owner(); calls.add("name:"+name); return name; }
        @Override public ItemStack getHeldItem() { owner(); if (heldHook != null) heldHook.run(); return held; }
        @Override public boolean isUsingItem() { owner(); return using; }
        @Override public int getItemInUseDuration() { owner(); return draw; }
        @Override public float getEyeHeight() { owner(); return 1.62F; }
    }
    public static class Box extends AxisAlignedBB {
        Box(double a,double b,double c,double d,double e,double f) { super(a,b,c,d,e,f); }
        Box(AxisAlignedBB b) { this(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ); }
        @Override public AxisAlignedBB offset(double x,double y,double z) {
            if(counting) offsets++; return counting ? new Box(super.offset(x,y,z)) : super.offset(x,y,z);
        }
        @Override public AxisAlignedBB expand(double x,double y,double z) {
            if(counting) expands++; return new Box(super.expand(x,y,z));
        }
        @Override public MovingObjectPosition calculateIntercept(Vec3 from,Vec3 to) {
            if(counting) intercepts++; return super.calculateIntercept(from,to);
        }
    }
    public static class Handler extends NetHandlerPlayClient {
        Set<UUID> byUuid;
        Set<String> byName;
        NetworkPlayerInfo info;
        public Handler() { super(null,null,null,null); }
        @Override public NetworkPlayerInfo getPlayerInfo(UUID id) { owner(); calls.add("tabUUID:"+id); if(counting) uuidLookups++; return super.getPlayerInfo(id); }
        @Override public NetworkPlayerInfo getPlayerInfo(String name) { owner(); calls.add("tabName:"+name); if(counting) nameLookups++; return super.getPlayerInfo(name); }
        void sync() throws Exception {
            Map<UUID,NetworkPlayerInfo> actual=new LinkedHashMap<>();
            for(UUID id:byUuid) actual.put(id,info);
            for(String name:byName) actual.put(new UUID(1,actual.size()),new NetworkPlayerInfo(new GameProfile(null,name)));
            put(this,NetHandlerPlayClient.class,"playerInfoMap",actual);
        }
    }
    public static class World extends WorldClient {
        int calls, stopAt;
        Vec3 block;
        Runnable mutation;
        public World() { super(null,null,0,null,null); }
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from,Vec3 to,boolean a,boolean b,boolean c) {
            owner(); checkFlags(a,b,c); calls++; TrajectoryPreparationParityTest.calls.add("trace:"+calls); if(counting) traces++;
            if(mutation != null) mutation.run();
            if(block != null) return new MovingObjectPosition(block, EnumFacing.UP, new BlockPos(block));
            return calls == stopAt ? new MovingObjectPosition(to,EnumFacing.UP,new BlockPos(to)) : null;
        }
        void checkFlags(boolean a,boolean b,boolean c) { if(a || !b || c) throw new AssertionError("Block trace flags changed"); }
    }
    static class Scene {
        Minecraft mc;
        Player shooter;
        World world;
        Handler handler;
        List<Player> targets = new ArrayList<>();
        Scene(int players,int segments) throws Exception {
            mc=allocate(Minecraft.class); shooter=player(0,0,64,0); shooter.held=new ItemStack(Items.ender_pearl);
            handler=allocate(Handler.class); handler.byUuid=new HashSet<>(); handler.byName=new HashSet<>();
            handler.info=new NetworkPlayerInfo(new GameProfile(new UUID(0,999),"UUID-only fixture"));
            put(shooter,EntityPlayerSP.class,"sendQueue",handler);
            world=allocate(World.class); world.stopAt=segments==160 ? 0 : segments;
            put(world,net.minecraft.world.World.class,"playerEntities",new ArrayList<EntityPlayer>());
            mc.theWorld=world; mc.thePlayer=shooter;
            put(null,Minecraft.class,"theMinecraft",mc);
            put(mc,Minecraft.class,"mcThread",OWNER);
            for(int i=0;i<players;i++) add(player(i+1,30+i*2,64,10));
        }
        void add(Player p) { targets.add(p); world.playerEntities.add(p); handler.byUuid.add(p.uuid); }
        void rewind() { world.calls=0; for(Player p:targets) p.boxCalls=0; }
    }
    static Player player(int id,double x,double y,double z) throws Exception {
        Player p=allocate(Player.class); p.uuid=new UUID(0,id); p.name="fixture"+id; p.living=p.using=true; p.draw=20;
        position(p,x,y,z); return p;
    }
    static void position(Player p,double x,double y,double z) {
        p.lastTickPosX=p.posX=x; p.lastTickPosY=p.posY=y; p.lastTickPosZ=p.posZ=z;
        p.box=new AxisAlignedBB(x-.3,y,z-.3,x+.3,y+1.8,z+.3);
    }
    static Object compute(Scene s,boolean scratch,float partial) throws Exception {
        s.rewind(); if(scratch) ((ProjectileTrajectoryRenderer) candidate).setTrajectoryPreparation(true); return (scratch ? CAND : BASE).invoke(scratch ? candidate : baseline,s.mc,s.shooter,partial);
    }
    static String point(Vec3 p) { return Long.toHexString(Double.doubleToLongBits(p.xCoord))+":"+
            Long.toHexString(Double.doubleToLongBits(p.yCoord))+":"+Long.toHexString(Double.doubleToLongBits(p.zCoord)); }
    static String hit(Object h) throws Exception {
        return h == null ? "none" : ((Player)field(h,"player")).name+"@"+point((Vec3)field(h,"hitVec"));
    }
    static String output(Object t) throws Exception {
        if(t==null) return "null";
        StringBuilder b=new StringBuilder(field(t,"hitBlock")+"/"+hit(field(t,"playerHit")));
        for(Object v:(List<?>)field(t,"points")) b.append('/').append(point((Vec3)v));
        return b.toString();
    }
    static Object compare(Scene s,float partial,String label) throws Exception {
        s.handler.sync();
        reset(); Object a=compute(s,false,partial); String originalCounts=counts(); List<String> originalCalls=new ArrayList<>(calls);
        reset(); Object b=compute(s,true,partial);
        check(originalCounts.equals(counts()), label+" original getter/network/trace counts");
        check(originalCalls.equals(calls), label+" original getter/network/trace order");
        check(output(a).equals(output(b)),label+" full output mismatch"); comparisons++; fullComparisons++; return a;
    }
    static Object segment(Scene s,Vec3 from,Vec3 to,float partial,boolean scratch) throws Exception {
        s.rewind();
        if (!scratch) return BASE_FIND.invoke(baseline,s.mc,s.shooter,from,to,partial);
        ProjectileTrajectoryRenderer renderer = (ProjectileTrajectoryRenderer) candidate;
        renderer.setTrajectoryPreparation(true);
        TrajectoryPreparation p = (TrajectoryPreparation) field(renderer, "preparation");
        TrajectoryPreparation.Scratch scope = new TrajectoryPreparation.Scratch(s.mc.theWorld);
        p.current = scope;
        try { return CAND_FIND.invoke(candidate,s.mc,s.shooter,from,to,partial,scope); }
        finally { scope.disable(); p.current = null; }
    }
    static Object segmentCompare(Scene s,Vec3 from,Vec3 to,float partial,String label) throws Exception {
        s.handler.sync();
        Object a=segment(s,from,to,partial,false),b=segment(s,from,to,partial,true);
        check(hit(a).equals(hit(b)),label+" segment mismatch"); comparisons++; segmentComparisons++; return a;
    }
    static void tests() throws Exception {
        for(int n:new int[]{0,1,8,32}) for(int segments:new int[]{1,16,160}) {
            Scene s=new Scene(n,segments);
            Object t=compare(s,.5F,"matrix "+n+"/"+segments);
            check(((List<?>)field(t,"points")).size()==segments+1,"segment matrix count");
            reset(); compute(s,false,.5F); String before=counts();
            check(boxes==(long)n*segments && traces==segments,"baseline work count");
            reset(); compute(s,true,.5F); String after=counts();
            check(boxes==(long)n*segments && traces==segments,"candidate work count");
            System.out.println("WORK players="+n+" segments="+segments+" BASE "+before+" CAND "+after);
        }
        Scene s=new Scene(0,160);
        Player far=player(1,5,64,0),near=player(2,2,64,0); s.add(far); s.add(near);
        Vec3 from=new Vec3(0,65,0),to=new Vec3(10,65,0);
        check(field(segmentCompare(s,from,to,.5F,"nearest"),"player")==near,"nearest wins over order");
        position(far,2,64,0);
        check(field(segmentCompare(s,from,to,.5F,"equal tie"),"player")==far,"first equal tie wins");
        Collections.reverse(s.world.playerEntities);
        check(field(segmentCompare(s,from,to,.5F,"reverse tie"),"player")==near,"reversed tie wins");
        s=new Scene(0,160); Player inside=player(3,0,64,0); s.add(inside);
        Object h=segmentCompare(s,from,new Vec3(.1,65,0),.5F,"inside no intercept");
        check(point((Vec3)field(h,"hitVec")).equals(point(from)),"inside fallback current");
        h=segmentCompare(s,from,to,.5F,"inside exiting");
        check(((Vec3)field(h,"hitVec")).xCoord>0,"inside exiting retains exit intercept, not current");
        Vec3 boundary=new Vec3(.48,65,0);
        check(segmentCompare(s,boundary,boundary,.5F,"strict boundary zero segment")==null,"AABB boundary is not strictly inside");
        s=new Scene(0,160); Player target=player(4,-.16,64,1); s.add(target);
        Object t=compare(s,.5F,"first player impact");
        Vec3 impact=(Vec3)field(field(t,"playerHit"),"hitVec");
        s.world.block=impact; t=compare(s,.5F,"equal block and player");
        check((boolean)field(t,"hitBlock") && field(t,"playerHit")==null,"block wins equal");
        s.world.block=new Vec3(impact.xCoord,impact.yCoord,impact.zCoord-.1);
        check((boolean)field(compare(s,.5F,"block before player"),"hitBlock"),"block before");
        s.world.block=new Vec3(impact.xCoord,impact.yCoord,impact.zCoord+.1);
        check(field(compare(s,.5F,"player before block"),"playerHit")!=null,"player before");

        s=new Scene(0,160); Player excluded=player(5,2,64,0); s.add(excluded);
        s.world.playerEntities.add(null); s.world.playerEntities.add(s.shooter);
        excluded.invisible=true; check(segmentCompare(s,from,to,.5F,"invisible")==null,"invis excluded");
        excluded.invisible=false; excluded.isDead=true; check(segmentCompare(s,from,to,.5F,"dead")==null,"dead excluded");
        excluded.isDead=false; excluded.living=false; check(segmentCompare(s,from,to,.5F,"not alive")==null,"not alive excluded");
        excluded.living=true; s.handler.byUuid.clear(); check(segmentCompare(s,from,to,.5F,"NPC")==null,"NPC excluded");
        s.handler.byName.add(excluded.name); check(segmentCompare(s,from,to,.5F,"name fallback")!=null,"name fallback included");
        excluded.uuid=null; check(segmentCompare(s,from,to,.5F,"null UUID name fallback")!=null,"null UUID fallback");
        excluded.name=null; s.handler.byName.clear(); check(segmentCompare(s,from,to,.5F,"null name and UUID")==null,"missing identities excluded");
        put(s.shooter,EntityPlayerSP.class,"sendQueue",null);
        check(segmentCompare(s,from,to,.5F,"no handler")!=null,"no-handler permissive fallback");
        put(null,Minecraft.class,"theMinecraft",null);
        check(segmentCompare(s,from,to,.5F,"no Minecraft")!=null,"no-Minecraft permissive fallback");
        check(!OverlayPlayerClassifier.shouldTreatAsRealPlayer(null),"classifier null rejected");
        s=new Scene(0,160); Player moving=player(6,4,64,0); s.add(moving);
        moving.lastTickPosX=2; moving.lastTickPosY=62; moving.lastTickPosZ=-2;
        for(float pt:new float[]{0,.25F,.5F,1,-.25F,1.25F}) {
            h=segmentCompare(s,new Vec3(-5,63+2*pt,-2+2*pt),new Vec3(10,63+2*pt,-2+2*pt),pt,"interpolation "+pt);
            check(h!=null,"interpolated hit");
            check(Math.abs(((Vec3)field(h,"hitVec")).xCoord-(2+2*pt-.3-.18))<1e-12,"interpolated x preserved");
            compare(s,pt,"trajectory partial "+pt);
        }
        s=new Scene(8,160);
        for(boolean bow:new boolean[]{false,true}) for(float yaw:new float[]{0,90,179,-135}) for(float pitch:new float[]{-80,-20,0,50}) {
            s.shooter.held=new ItemStack(bow ? Items.bow : Items.ender_pearl);
            s.shooter.rotationYaw=yaw; s.shooter.rotationPitch=pitch;
            for(int draw:new int[]{1,2,3,10,20,30}) { s.shooter.draw=draw; compare(s,.37F,"bow/pearl aim/draw"); }
        }
        s.shooter.using=false; s.shooter.held=new ItemStack(Items.bow);
        check(compare(s,.5F,"bow not drawn")==null,"inactive bow");
        s.shooter.held=null; reset(); check(compare(s,.5F,"empty held")==null,"empty held"); check(boxes==0 && traces==0,"no preparation when inactive");
        s.shooter.held=new ItemStack(Items.stick); check(compare(s,.5F,"unsupported item")==null,"unsupported item");
        s=new Scene(0,160); Player changing=player(7,-.16,64,1); s.add(changing);
        Object first=compare(s,.5F,"first render");
        position(changing,100,64,100); s.shooter.rotationYaw=90;
        Object second=compare(s,.5F,"changed aim and target between renders");
        check(!output(first).equals(output(second)),"fresh aim and target positions");
        position(changing,-.16,64,1); s.shooter.rotationYaw=0; changing.invisible=true;
        check(field(compare(s,.5F,"invisible between renders"),"playerHit")==null,"fresh eligibility");
        changing.invisible=false; s.handler.byUuid.clear();
        check(field(compare(s,.5F,"tab removal between renders"),"playerHit")==null,"fresh classifier");
        s.handler.byUuid.add(changing.uuid); s.world.playerEntities.clear();
        check(field(compare(s,.5F,"list removal between renders"),"playerHit")==null,"fresh world list");
        check(s.mc.isCallingFromMinecraftThread(),"fixture caller ownership");
        Random rng=new Random(0x189141L);
        for(int fixture=0;fixture<300;fixture++) {
            s=new Scene(0,160);
            int n=rng.nextInt(33);
            for(int i=0;i<n;i++) {
                Player p=player(i+1,rng.nextDouble()*12-6,64+rng.nextDouble()*6-3,rng.nextDouble()*12-6);
                p.lastTickPosX=p.posX+rng.nextDouble()*2-1;
                p.lastTickPosY=p.posY+rng.nextDouble()*2-1;
                p.lastTickPosZ=p.posZ+rng.nextDouble()*2-1;
                s.add(p);
            }
            float pt=rng.nextFloat();
            segmentCompare(s,new Vec3(-6,64+rng.nextDouble()*4-2,-6),new Vec3(6,64+rng.nextDouble()*4-2,6),pt,"random segment "+fixture);
            s.shooter.held=new ItemStack(fixture%2==0 ? Items.bow : Items.ender_pearl);
            s.shooter.rotationYaw=rng.nextFloat()*360-180; s.shooter.rotationPitch=rng.nextFloat()*160-80;
            s.shooter.draw=3+rng.nextInt(28);
            compare(s,pt,"random full path "+fixture);
        }
        System.out.println("PASS stable-state comparisons="+comparisons+" full-path="+fullComparisons+" segment="+segmentComparisons+" assertions="+assertions);

        // Former negative controls MUST pass with live calls retained.
        s=new Scene(0,160); changing=player(8,-.16,64,12); s.add(changing); changing.changingBox=true;
        s.handler.sync();
        String a=output(compute(s,false,.5F)),b=output(compute(s,true,.5F));
        check(a.equals(b),"stateful getter calls and output must now match");
        System.out.println("PASS former negative control: stateful bounding-box getter");
        s=new Scene(0,160); changing=player(9,-.16,64,12); s.add(changing);
        s.handler.sync();
        final Scene mutationScene=s; final Player mutationTarget=changing;
        s.world.mutation=()-> { if(mutationScene.world.calls==2) mutationTarget.invisible=true; };
        changing.invisible=false; a=output(compute(s,false,.5F));
        changing.invisible=false; b=output(compute(s,true,.5F));
        check(a.equals(b),"second trace eligibility mutation must now match");
        System.out.println("PASS former negative control: second block trace mutation");
        s=new Scene(1,160); final Scene firstTrace=s;
        s.handler.sync();
        s.world.mutation=()-> { if(firstTrace.world.calls==1) firstTrace.targets.get(0).invisible=true; };
        s.targets.get(0).invisible=false; a=output(compute(s,false,.5F));
        s.targets.get(0).invisible=false; b=output(compute(s,true,.5F));
        check(a.equals(b),"lazy preparation preserves first-block-trace ordering");
        System.out.println("PASS lazy first-block-trace ordering; total assertions="+assertions);
    }
    @Test
    void originalAndCandidateRetainCompleteTrajectoryAndLiveCalls() throws Exception {
        Bootstrap.register();
        tests();
    }
}
