package com.atw.renderboost.probe;

import java.util.Properties;

/** Only the existing render-frame thread mutates collection; no new timer/helper thread. */
public final class NameProbeRuntime {
    public static volatile boolean collecting; // Default OFF; injected inactive path reads only this branch.
    private static NameProbe probe;
    private static Thread owner;
    private static final Properties COVERAGE = new Properties();
    private static String last = "OFF; never started";
    private NameProbeRuntime() {}
    public static synchronized void coverage(String signature, String result) {
        COVERAGE.setProperty("hook." + signature, result);
    }
    public static void start(boolean timing) {
        if (collecting) throw new IllegalStateException("Name probe already collecting");
        if (probe != null) throw new IllegalStateException("Await completed name probe export before restarting");
        probe = new NameProbe(NameProbe.systemClock());
        owner = Thread.currentThread(); probe.start(timing); collecting = true;
        last = "collecting " + (timing ? "timing (1/32 frames)" : "counters") + "; automatically stops after 10s";
    }
    public static int enter(int metric, Object entity) {
        if (!collecting || Thread.currentThread() != owner) return 0;
        try {
            int kind = metric == 0 ? classify(entity) : 2;
            int token = probe.enter(metric, kind);
            if (!probe.running()) collecting = false;
            return token;
        } catch (RuntimeException | LinkageError e) { fail(); return 0; }
    }
    // Class hierarchy only: never invoke entity getters, network lookups or equality.
    // MCP names are the same runtime class names in the actual captured inputs.
    static int classify(Object entity) {
        Class<?> type = entity == null ? null : entity.getClass();
        for (int i=0; type != null && i<32; i++,type=type.getSuperclass()) {
            if (type.getName().equals("net.minecraft.entity.player.EntityPlayer")) return 0;
            if (type.getName().equals("net.minecraft.entity.item.EntityArmorStand")) return 1;
        }
        return 2;
    }
    public static void exit(int token, boolean exceptional) {
        if (token == 0 || !collecting || Thread.currentThread() != owner) return;
        try { probe.exit(token, exceptional); if (!probe.running()) collecting = false; }
        catch (RuntimeException | LinkageError e) { fail(); }
    }
    private static void fail() { collecting = false; if (probe != null) probe.stop(); last = "OFF; collector failure"; }
    public static void frame(long now) {
        if (!collecting || Thread.currentThread() != owner) return;
        probe.frame(now); if (!probe.running()) collecting = false;
    }
    public static void frameEnd(long now) {
        if (!collecting || Thread.currentThread() != owner) return;
        probe.frameEnd(now); if (!probe.running()) collecting = false;
    }
    public static void stop() {
        if (probe != null && Thread.currentThread() == owner) probe.stop();
        collecting = false;
    }
    public static String status() {
        if (probe != null && Thread.currentThread() == owner && !probe.active()) collecting = false;
        return "nameProbe=" + (collecting ? last : "OFF; " + last) + "; use probe10 counters|timing|status|stop; "
                + "inspect name-probe hook startup log / completed coverage; no encode/decode hooks";
    }
    /** Called on the existing frame thread, and exported by the existing export executor after OFF. */
    public static Properties takeCompleted() {
        if (probe == null || Thread.currentThread() != owner) return null;
        Properties p = probe.takeCompleted();
        if (p == null) return null;
        probe = null; // Only a drained result releases the next collection.
        synchronized (NameProbeRuntime.class) { p.putAll(COVERAGE); }
        p.setProperty("hook.directShowEntityEncodeDecode", "UNAVAILABLE: implementation owners not in eleven captured inputs; original path unchanged");
        p.setProperty("hook.worldPhase", "UNAVAILABLE: RenderGlobal/EntityRenderer not captured for this probe; original path unchanged");
        p.setProperty("entityClassSemantics", "instanceof EntityPlayer (includes player-shaped NPCs), EntityArmorStand, other living; no real-player/network lookup");
        last = p.getProperty("mode") + " completed; " + p.getProperty("stopReason") + "; frames=" + p.getProperty("frames");
        return p;
    }
}
