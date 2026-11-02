package com.atw.renderboost.cache;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/** Independently requested, default OFF on every JVM startup. No Minecraft linkage here. */
public final class NamePrepRuntime {
    public static final String OWNER = "net/minecraft/scoreboard/ScorePlayerTeam";
    public static final String MARKER = "$atwNamePrepProof1";
    public static final int MARKER_VALUE = 0x41545750;
    private static volatile boolean requested, failed;
    private static volatile String marker;
    private static boolean observed;
    private static final AtomicLong generation = new AtomicLong(1), fallbacks = new AtomicLong(),
            offThread = new AtomicLong(), reentrant = new AtomicLong(), rejects = new AtomicLong(), failures = new AtomicLong();
    private static final NamePrepCache CACHE = new NamePrepCache();
    private static Thread owner;
    private static Object world;
    private static Class<?> bound;
    private static long applied;
    private static boolean busy;
    private NamePrepRuntime() {}
    public static boolean requested() { return requested; }
    public static void request(boolean on) { requested = on; clear(); }
    public static void clear() { generation.incrementAndGet(); if (Thread.currentThread() == owner && !busy) refresh(); }
    private static void refresh() {
        long now = generation.get();
        if (applied != now) { CACHE.clear(); bound = null; applied = now; }
    }
    public static void frame(Thread thread, Object currentWorld) {
        // An off-thread caller cannot transfer ownership or touch render-owned storage.
        if (thread != Thread.currentThread() || (owner != null && owner != thread)) return;
        if (owner != thread || world != currentWorld) {
            owner = thread; world = currentWorld; generation.incrementAndGet();
        }
        if (!busy) refresh();
    }
    public static synchronized void evidence(boolean accepted, String proofMarker) {
        // Multiple target definitions/transforms cannot silently replace the admitted class.
        marker = accepted && !observed && proofMarker != null ? proofMarker : null;
        observed = true; generation.incrementAndGet();
    }
    public static long epoch() { return generation.get(); }
    private static boolean eligible(Class<?> caller, Object receiver, long epoch) throws ReflectiveOperationException {
        if (!requested || failed || marker == null || world == null || epoch != generation.get()) return false;
        if (caller == null || receiver == null || receiver.getClass() != caller || !"net.minecraft.scoreboard.ScorePlayerTeam".equals(caller.getName())) {
            rejects.incrementAndGet(); return false;
        }
        refresh();
        if (bound == null) {
            Field f = caller.getDeclaredField(marker);
            if (f.getType() != int.class || f.getModifiers() != (Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL | 0x1000)
                    || f.getInt(null) != MARKER_VALUE) { rejects.incrementAndGet(); fail(); return false; }
            bound = caller;
        }
        if (bound != caller) { rejects.incrementAndGet(); fail(); return false; }
        return applied == epoch && epoch == generation.get();
    }
    private static boolean enter() {
        if (Thread.currentThread() != owner) { offThread.incrementAndGet(); fallbacks.incrementAndGet(); return false; }
        if (busy) { reentrant.incrementAndGet(); fallbacks.incrementAndGet(); return false; }
        busy = true; return true;
    }
    private static void fail() { failed = true; generation.incrementAndGet(); }
    private static void error(Throwable e) {
        if (e instanceof VirtualMachineError) throw (VirtualMachineError)e;
        if (e instanceof ThreadDeath) throw (ThreadDeath)e;
        failures.incrementAndGet(); fail();
    }
    /** Called after all original getters/appends. Null means execute original toString. */
    public static String lookup(Class<?> caller, Object receiver, String p, String n, String s, long epoch) {
        if (!enter()) return null;
        try {
            if (!eligible(caller, receiver, epoch)) { fallbacks.incrementAndGet(); return null; }
            String hit = CACHE.lookup(p, n, s);
            if (generation.get() != epoch || !requested) { refresh(); fallbacks.incrementAndGet(); return null; }
            if (!CACHE.supported(p, n, s)) fallbacks.incrementAndGet();
            return hit;
        } catch (Throwable e) { error(e); fallbacks.incrementAndGet(); return null; }
        finally { busy = false; if (applied != generation.get()) refresh(); }
    }
    public static void record(String output, Class<?> caller, Object receiver, String p, String n, String s, long epoch) {
        if (!enter()) return;
        try {
            if (!eligible(caller, receiver, epoch)) return;
            CACHE.record(p, n, s, output);
            if (generation.get() != epoch || !requested) refresh();
        } catch (Throwable e) { error(e); }
        finally { busy = false; if (applied != generation.get()) refresh(); }
    }
    public static boolean available() { return marker != null && !failed; }
    public static boolean active() { return requested && available() && Thread.currentThread() == owner && world != null && bound != null && applied == generation.get(); }
    public static String fallbackReason() {
        if (!requested) return "off";
        if (failed) return "failed";
        if (marker == null) return "unavailable";
        if (Thread.currentThread() != owner) return "off-thread";
        if (world == null) return "no-world";
        if (bound == null) return "unbound";
        return applied != generation.get() ? "invalidated" : "none";
    }
    public static long[] counters() { return new long[]{generation.get(), CACHE.hits, CACHE.misses, CACHE.admissions,
            CACHE.collisions, CACHE.evictions, CACHE.oversized, fallbacks.get(), offThread.get(), reentrant.get(), rejects.get(), failures.get()}; }
    public static boolean stable(long[] start, boolean on) {
        return start != null && start[0] == generation.get() && on == requested
                && (!on || (active() && start[7] == fallbacks.get() && start[10] == rejects.get() && start[11] == failures.get()));
    }
    public static void metadata(Properties p) {
        p.setProperty("nameprepGuardVersion", "1");
        p.setProperty("nameprepRequested", String.valueOf(requested)); p.setProperty("nameprepAvailable", String.valueOf(available()));
        p.setProperty("nameprepActive", String.valueOf(active())); p.setProperty("nameprepFallback", String.valueOf(!active()));
        p.setProperty("nameprepFallbackReason", fallbackReason());
        p.setProperty("nameprepFailed", String.valueOf(failed)); p.setProperty("nameprepEntries", String.valueOf(CACHE.size()));
        p.setProperty("nameprepCharacters", String.valueOf(CACHE.characters())); p.setProperty("nameprepEntryLimit", "512");
        p.setProperty("nameprepCharacterLimit", "262144"); p.setProperty("nameprepTextLimit", "2048");
    }
    public static void exportDelta(Properties p, long[] start) {
        metadata(p); long[] end = counters();
        String[] names = {"Invalidations", "Hits", "Misses", "Admissions", "Collisions", "Evictions", "Oversized", "Fallbacks", "OffThread", "Reentrant", "GuardRejects", "Failures"};
        for (int i = 0; i < names.length; i++) p.setProperty("nameprep" + names[i], String.valueOf(end[i] - start[i]));
        p.setProperty("nameprepCacheActive", String.valueOf(active() && end[1] > start[1]));
    }
    public static String status() {
        return "nameprepRequested=" + requested + ", nameprepAvailable=" + available() + ", nameprepActive=" + active()
                + ", nameprepFallback=" + !active() + ", nameprepFallbackReason=" + fallbackReason() + ", nameprepFailed=" + failed + ", nameprepGuard=1, nameprepEntries=" + CACHE.size()
                + "/512, nameprepCharacters=" + CACHE.characters() + "/262144, nameprepHits=" + CACHE.hits + ", nameprepMisses=" + CACHE.misses
                + ", nameprepAdmissions=" + CACHE.admissions + ", nameprepCollisions=" + CACHE.collisions
                + ", nameprepEvictions=" + CACHE.evictions + ", nameprepOversized=" + CACHE.oversized
                + ", nameprepFallbacks=" + fallbacks + ", nameprepOffThread=" + offThread + ", nameprepReentrant=" + reentrant
                + ", nameprepGuardRejects=" + rejects + ", nameprepFailures=" + failures;
    }
}
