package com.atw.renderboost;

import com.atw.renderboost.benchmark.BenchmarkSession;
import com.atw.renderboost.benchmark.FrameStatistics;
import com.atw.renderboost.benchmark.BenchmarkSceneGuard;
import com.atw.renderboost.cache.GlyphCache;
import com.atw.renderboost.cache.GlyphKey;
import com.atw.renderboost.terrain.TerrainControl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.Display;
import org.lwjgl.BufferUtils;
import java.nio.IntBuffer;

/** All GL ownership and benchmark mutation stays on Minecraft's render thread. */
public final class RenderRuntime {
    private static volatile boolean enabled;
    private static volatile boolean dirty = true;
    private static Thread renderThread;
    private static Object context;
    private static int hudDepth, glyphDepth;
    private static boolean sawGlyphHook, failed;
    private static long frameStarted, hudStarted, hudNs, lastWorkNs, lastHudNs;
    private static BenchmarkSession session;
    private static long requestedAt;
    private static Scene scene;
    private static Properties metadata;
    private static long benchHits, benchMisses, benchCompiles, benchErrorChecks, benchErrorSkips, workSum, hudSum;
    private static long benchPostChecks, benchPostSkips;
    private static boolean countersStarted;
    private static boolean benchmarkMoving;
    private static long playerCountSum, entityCountSum;
    private static int playerCountMin, playerCountMax, entityCountMin, entityCountMax;
    private static long[] benchTerrain;
    // LWJGL 2's glGetInteger(IntBuffer) validates room for its largest result.
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final ExecutorService EXPORT = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ATW benchmark export"); t.setDaemon(true); return t;
    });
    private static volatile boolean exporting;
    private static final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
    private static final GlyphCache CACHE = new GlyphCache(new GlyphCache.Backend() {
        private int recording;
        @Override public int allocate() { return GL11.glGenLists(1); }
        @Override public void begin(int id) {
            GL11.glNewList(id, GL11.GL_COMPILE_AND_EXECUTE);
            if (GL11.glGetInteger(GL11.GL_LIST_INDEX) != id)
                throw new IllegalStateException("Driver refused display-list compilation");
            recording = id;
        }
        @Override public void end() {
            int id = recording;
            GL11.glEndList();
            recording = 0;
            if (!GL11.glIsList(id)) throw new IllegalStateException("Driver did not create a display list");
        }
        @Override public void replay(int id) { GL11.glCallList(id); }
        @Override public void delete(int id) { GL11.glDeleteLists(id, 1); }
    }, 2048, 4096, 3);

    private RenderRuntime() {}
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) {
        cancelBenchmark("Optimization mode changed");
        enabled = value;
        FrameErrorPolicy.setEnabled(value);
        dirty = true;
        message(status());
    }
    public static String status() {
        return "requested=" + (enabled ? "ON (experimental)" : "OFF")
                + ", frameErrors=" + FrameErrorPolicy.mode() + ", frameErrorHookObserved=" + FrameErrorPolicy.observed()
                + ", preChecksSkipped=" + FrameErrorPolicy.skipped()
                + ", postChecksSkipped=" + FrameErrorPolicy.postSkipped()
                + ", pollIntervalMs=" + FrameErrorPolicy.POLL_INTERVAL_MS
                + ", errorFallback=" + FrameErrorPolicy.errorFallback() + ", firstGlError=" + FrameErrorPolicy.firstError()
                + ", cache=" + (enabled && !failed && sawGlyphHook ? "ON" : "OFF/fallback") + ", glyphHook="
                + (sawGlyphHook ? "available" : "not observed; inspect startup log")
                + ", failed=" + failed + ", lists=" + CACHE.size() + "/2048, hits=" + CACHE.hits
                + ", misses=" + CACHE.misses + ", compiled=" + CACHE.compilations
                + ", evicted=" + CACHE.evictions + ", " + TerrainControl.status()
                + ", benchmark=" + (session == null ? "idle" : "running");
    }
    /** Reload hooks may run off-thread: queue deletion, never issue GL calls there. */
    public static void invalidate() { dirty = true; com.atw.renderboost.terrain.TerrainRuntime.invalidate(); }

    public static boolean beginGlyph(Object font, int u, int v, int shear, float width, float x, float y) {
        if (Thread.currentThread() != renderThread) return false;
        sawGlyphHook = true;
        glyphDepth++;
        if (!enabled || failed || dirty || hudDepth != 1 || glyphDepth != 1
                || !Float.isFinite(width) || !Float.isFinite(x) || !Float.isFinite(y)) return false;
        try {
            if (GLContext.getCapabilities() != context) { dirty = true; return false; }
            // glCallList during another mod's recording could retain references to evicted IDs.
            // Query every candidate invocation, including cache hits; never record/replay there.
            if (GL11.glGetInteger(GL11.GL_LIST_INDEX) != 0) return false;
            boolean hit = CACHE.begin(new GlyphKey(font, u, v, shear, width, x, y));
            if (hit) glyphDepth--;
            return hit;
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }
    public static void endGlyph() {
        if (Thread.currentThread() != renderThread || glyphDepth == 0) return;
        if (glyphDepth == 1 && CACHE.recording()) {
            try { CACHE.end(); } catch (RuntimeException | LinkageError e) { fail(e); }
        }
        glyphDepth--;
    }
    public static void abortGlyph() {
        if (Thread.currentThread() != renderThread) return;
        // Original GL primitive threw; disable recording rather than replaying partial geometry.
        failed = true;
        dirty = false;
        // The original primitive may still be between glBegin/glEnd. Do not add GL calls.
        // Bounded existing driver objects are reclaimed with the context at shutdown.
        CACHE.invalidate(false);
        glyphDepth = Math.max(0, glyphDepth - 1);
        cancelBenchmark("Original glyph primitive threw");
    }
    private static void fail(Throwable e) {
        failed = true; dirty = false;
        // Called only before the primitive or after its glEnd, where cleanup is safe.
        try {
            boolean same = GLContext.getCapabilities() == context
                    && GL11.glGetInteger(GL11.GL_LIST_INDEX) == (CACHE.recording() ? CACHE.recordingId() : 0);
            CACHE.invalidate(same);
        } catch (RuntimeException | LinkageError cleanupFailure) { CACHE.invalidate(false); }
        cancelBenchmark("Experimental cache failed");
        messages.add("Experimental cache disabled: " + e.getClass().getSimpleName());
        System.err.println("[ATW Render Boost] Cache failure: " + e);
    }

    public static void hudEnter() {
        if (Thread.currentThread() != renderThread) return;
        if (hudDepth++ == 0) hudStarted = System.nanoTime();
    }
    public static void hudExit() {
        if (Thread.currentThread() != renderThread || hudDepth == 0) return;
        if (--hudDepth == 0) hudNs += System.nanoTime() - hudStarted;
    }
    public static void frameStart() {
        renderThread = Thread.currentThread();
        com.atw.renderboost.terrain.TerrainRuntime.frameThread();
        long now = System.nanoTime();
        frameStarted = now;
        hudDepth = glyphDepth = 0;
        hudNs = 0;
        if (!failed) {
            try {
                Object current = GLContext.getCapabilities();
                if (current != context) {
                    CACHE.invalidate(false); // Never delete names belonging to an old context.
                    context = current;
                    dirty = false;
                    cancelBenchmark("OpenGL context changed");
                } else if (dirty) {
                    // Do not close another mod's display list or delete while it is recording.
                    if (GL11.glGetInteger(GL11.GL_LIST_INDEX) == 0) {
                        CACHE.invalidate(true);
                        dirty = false;
                        cancelBenchmark("Resources/cache invalidated");
                    }
                }
            } catch (RuntimeException | LinkageError e) { fail(e); }
        }
        String text;
        while ((text = messages.poll()) != null) message(text);
        sample(now);
    }
    public static void frameEnd() {
        lastWorkNs = System.nanoTime() - frameStarted;
        lastHudNs = hudNs;
        if (hudDepth != 0 || glyphDepth != 0 || CACHE.recording()) {
            fail(new IllegalStateException("Unbalanced rendering scope"));
            hudDepth = glyphDepth = 0;
        }
    }

    public static void benchmark(int seconds, int warmupSeconds) {
        benchmark(seconds, warmupSeconds, false);
    }
    public static void benchmark(int seconds, int warmupSeconds, boolean moving) {
        if (session != null || exporting) throw new IllegalStateException("Benchmark/export already active");
        session = new BenchmarkSession(seconds, warmupSeconds);
        requestedAt = System.nanoTime();
        scene = null; metadata = null; countersStarted = false;
        benchmarkMoving = moving;
        workSum = hudSum = 0;
        playerCountSum = entityCountSum = 0;
        playerCountMin = entityCountMin = Integer.MAX_VALUE;
        playerCountMax = entityCountMax = 0;
        message("Benchmark armed: " + warmupSeconds + "s warm-up + " + seconds
                + "s samples, requested " + (enabled ? "ON" : "OFF") + ", frameErrors=" + FrameErrorPolicy.mode()
                + ", motion=" + (moving ? "moving" : "stationary")
                + (moving ? ". Close chat; follow the same manually controlled route."
                          : ". Close chat; hold camera still."));
    }
    public static void cancelBenchmark(String reason) {
        if (session == null) return;
        session = null; scene = null;
        message("Benchmark aborted: " + reason + ". No result exported.");
    }
    private static void sample(long now) {
        if (session == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (scene == null) {
            if (!ready(mc)) {
                if (now - requestedAt > 10_000_000_000L) cancelBenchmark("No active unobstructed world within 10s");
                return;
            }
            scene = new Scene(mc, benchmarkMoving);
            metadata = scene.metadata(mc);
        } else if (!ready(mc) || !scene.matches(mc)) {
            cancelBenchmark("World, focus, screen, window, tracked settings, or guarded camera changed");
            return;
        }
        try {
            int before = session.count();
            boolean done = session.frame(now, com.atw.renderboost.terrain.TerrainRuntime.benchmarkState());
            if (session.sampling() && !countersStarted) {
                benchHits = CACHE.hits; benchMisses = CACHE.misses; benchCompiles = CACHE.compilations;
                benchErrorChecks = FrameErrorPolicy.checks(); benchErrorSkips = FrameErrorPolicy.skipped();
                benchPostChecks = FrameErrorPolicy.postChecks(); benchPostSkips = FrameErrorPolicy.postSkipped();
                metadata.setProperty("frameErrorModeAtSamplingStart", FrameErrorPolicy.mode());
                metadata.setProperty("frameErrorFallbackAtSamplingStart", String.valueOf(FrameErrorPolicy.errorFallback()));
                metadata.setProperty("cameraStart", camera(mc).toString());
                recordRenderDimensions(metadata, "Start", mc);
                benchTerrain = com.atw.renderboost.terrain.TerrainRuntime.counters();
                metadata.setProperty("terrainStatusAtSamplingStart", TerrainControl.status());
                countersStarted = true;
            }
            if (session.count() != before) {
                workSum += lastWorkNs; hudSum += lastHudNs;
                // O(1) list sizes only; no entity iteration, identities, profiles, or server inputs.
                int players = mc.theWorld.playerEntities.size(), entities = mc.theWorld.loadedEntityList.size();
                playerCountSum += players; entityCountSum += entities;
                playerCountMin = Math.min(playerCountMin, players); playerCountMax = Math.max(playerCountMax, players);
                entityCountMin = Math.min(entityCountMin, entities); entityCountMax = Math.max(entityCountMax, entities);
            }
            if (done) finish();
        } catch (RuntimeException e) { cancelBenchmark(e.getMessage()); }
    }
    private static boolean ready(Minecraft mc) {
        return mc.theWorld != null && mc.thePlayer != null && mc.currentScreen == null
                && Display.isActive() && !mc.isGamePaused();
    }
    private static void finish() {
        BenchmarkSession finished = session;
        FrameStatistics s = finished.statistics();
        Properties properties = metadata;
        properties.setProperty("samples", String.valueOf(s.samples));
        properties.setProperty("medianMs", String.valueOf(s.medianMs));
        properties.setProperty("p95Ms", String.valueOf(s.p95Ms));
        properties.setProperty("p99Ms", String.valueOf(s.p99Ms));
        properties.setProperty("averageFps", String.valueOf(s.averageFps));
        properties.setProperty("meanGameLoopMs", String.valueOf(workSum / (1e6 * s.samples)));
        properties.setProperty("meanHudScopeMs", String.valueOf(hudSum / (1e6 * s.samples)));
        properties.setProperty("cameraEnd", camera(Minecraft.getMinecraft()).toString());
        recordRenderDimensions(properties, "End", Minecraft.getMinecraft());
        properties.setProperty("loadedPlayersMin", String.valueOf(playerCountMin));
        properties.setProperty("loadedPlayersMax", String.valueOf(playerCountMax));
        properties.setProperty("loadedPlayersMean", String.valueOf(playerCountSum / (double)s.samples));
        properties.setProperty("loadedEntitiesMin", String.valueOf(entityCountMin));
        properties.setProperty("loadedEntitiesMax", String.valueOf(entityCountMax));
        properties.setProperty("loadedEntitiesMean", String.valueOf(entityCountSum / (double)s.samples));
        properties.setProperty("entityCountSamples", String.valueOf(s.samples));
        properties.setProperty("cacheHits", String.valueOf(CACHE.hits - benchHits));
        properties.setProperty("cacheMisses", String.valueOf(CACHE.misses - benchMisses));
        properties.setProperty("cacheCompilations", String.valueOf(CACHE.compilations - benchCompiles));
        properties.setProperty("glyphHookObserved", String.valueOf(sawGlyphHook));
        properties.setProperty("cacheFailed", String.valueOf(failed));
        properties.setProperty("frameErrorHookObserved", String.valueOf(FrameErrorPolicy.observed()));
        properties.setProperty("frameErrorMode", FrameErrorPolicy.mode());
        properties.setProperty("preRenderCheckOpportunities", String.valueOf(FrameErrorPolicy.checks() - benchErrorChecks));
        properties.setProperty("preRenderChecksSkipped", String.valueOf(FrameErrorPolicy.skipped() - benchErrorSkips));
        properties.setProperty("postRenderCheckOpportunities", String.valueOf(FrameErrorPolicy.postChecks() - benchPostChecks));
        properties.setProperty("postRenderChecksSkipped", String.valueOf(FrameErrorPolicy.postSkipped() - benchPostSkips));
        properties.setProperty("frameErrorPollIntervalMs", String.valueOf(FrameErrorPolicy.POLL_INTERVAL_MS));
        properties.setProperty("frameErrorFallback", String.valueOf(FrameErrorPolicy.errorFallback()));
        properties.setProperty("frameErrorFirstGlError", String.valueOf(FrameErrorPolicy.firstError()));
        properties.setProperty("cacheActive", String.valueOf(enabled && !failed && CACHE.hits > benchHits));
        TerrainControl.export(properties);
        com.atw.renderboost.terrain.TerrainRuntime.exportDelta(properties, benchTerrain);
        String result = String.format(Locale.ROOT, "%d frames: median %.3fms, p95 %.3fms, p99 %.3fms, avg %.2f FPS",
                s.samples, s.medianMs, s.p95Ms, s.p99Ms, s.averageFps);
        session = null; scene = null;
        message(result);
        exporting = true;
        long[] data = finished.samples();
        EXPORT.execute(() -> {
            try {
                Path dir = Paths.get(System.getProperty("user.home"), ".weave", "atw-render-boost", "benchmarks");
                Files.createDirectories(dir);
                String id = Instant.now().toString().replace(':', '-') + ("true".equals(properties.getProperty("optimizationRequested")) ? "-on" : "-off");
                Path csv = dir.resolve(id + ".csv");
                try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                    w.write("frame,frameTimeNs\n");
                    for (int i = 0; i < data.length; i++) w.write(i + "," + data[i] + "\n");
                }
                try (OutputStream out = Files.newOutputStream(dir.resolve(id + ".properties"), StandardOpenOption.CREATE_NEW)) {
                    properties.store(out, "ATW measured frame-start intervals; no GPU timer; nearest-rank percentiles");
                }
                messages.add("Benchmark exported: " + csv);
            } catch (IOException e) { messages.add("Benchmark export failed: " + e.getMessage()); }
            finally { exporting = false; }
        });
    }

    public static void message(String text) {
        System.out.println("[ATW Render Boost] " + text);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("[ATW Render Boost] " + text));
    }

    private static final class Scene {
        final Object world;
        final double x, y, z;
        final float yaw, pitch;
        final String settings;
        final BenchmarkSceneGuard guard;
        Scene(Minecraft mc, boolean moving) {
            world = mc.theWorld;
            x = mc.thePlayer.posX; y = mc.thePlayer.posY; z = mc.thePlayer.posZ;
            yaw = mc.thePlayer.rotationYaw; pitch = mc.thePlayer.rotationPitch;
            settings = settings(mc);
            guard = new BenchmarkSceneGuard(world, camera(mc), settings, moving);
        }
        boolean matches(Minecraft mc) {
            return guard.accepts(mc.theWorld, camera(mc), settings(mc), Display.isActive(),
                    mc.isGamePaused(), mc.currentScreen != null, mc.thePlayer != null);
        }
        Properties metadata(Minecraft mc) {
            Properties p = new Properties();
            p.setProperty("modVersion", "0.1.0"); p.setProperty("minecraft", "1.8.9"); p.setProperty("weaveApi", "1.4.1");
            p.setProperty("optimizationRequested", String.valueOf(enabled));
            p.setProperty("cacheEnabled", String.valueOf(enabled && !failed && sawGlyphHook));
            TerrainControl.export(p);
            p.setProperty("glRenderer", GL11.glGetString(GL11.GL_RENDERER));
            p.setProperty("glVendor", GL11.glGetString(GL11.GL_VENDOR));
            p.setProperty("glVersion", GL11.glGetString(GL11.GL_VERSION));
            p.setProperty("javaVersion", System.getProperty("java.version"));
            p.setProperty("settings", settings);
            p.setProperty("camera", x + "," + y + "," + z + "," + yaw + "," + pitch);
            p.setProperty("benchmarkMotionMode", guard.mode());
            p.setProperty("cameraAtWarmupStart", camera(mc).toString());
            p.setProperty("worldIdentity", String.valueOf(System.identityHashCode(world)));
            p.setProperty("measurement", "CPU wall-clock frame-start intervals including limiter/presentation; no GPU timer");
            return p;
        }
        static String settings(Minecraft mc) {
            return mc.displayWidth + "x" + mc.displayHeight + ",fullscreen=" + mc.isFullScreen()
                    + ",drawable=" + Display.getWidth() + "x" + Display.getHeight()
                    + ",windowXY=" + Display.getX() + "," + Display.getY() + ",displayFullscreen=" + Display.isFullscreen()
                    + ",fpsCap=" + mc.gameSettings.limitFramerate + ",vsync=" + mc.gameSettings.enableVsync
                    + ",vbo=" + mc.gameSettings.useVbo + ",distance=" + mc.gameSettings.renderDistanceChunks
                    + ",fancy=" + mc.gameSettings.fancyGraphics + ",guiScale=" + mc.gameSettings.guiScale
                    + ",fov=" + mc.gameSettings.fovSetting + ",particles=" + mc.gameSettings.particleSetting
                    + ",anaglyph=" + mc.gameSettings.anaglyph + ",viewBobbing=" + mc.gameSettings.viewBobbing;
        }
    }
    private static BenchmarkSceneGuard.Camera camera(Minecraft mc) {
        return new BenchmarkSceneGuard.Camera(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }
    private static void recordRenderDimensions(Properties p, String suffix, Minecraft mc) {
        // Start/end only, on the render thread. This reads state without changing it or polling errors.
        p.setProperty("minecraftWidth" + suffix, String.valueOf(mc.displayWidth));
        p.setProperty("minecraftHeight" + suffix, String.valueOf(mc.displayHeight));
        p.setProperty("displayWidth" + suffix, String.valueOf(Display.getWidth()));
        p.setProperty("displayHeight" + suffix, String.valueOf(Display.getHeight()));
        p.setProperty("displayFullscreen" + suffix, String.valueOf(Display.isFullscreen()));
        VIEWPORT.clear(); GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        p.setProperty("glViewport" + suffix, VIEWPORT.get(0) + "," + VIEWPORT.get(1) + "," + VIEWPORT.get(2) + "," + VIEWPORT.get(3));
    }
}
