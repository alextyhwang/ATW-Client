package com.atw.renderboost.terrain;

import com.atw.renderboost.RenderRuntime;
import com.atw.renderboost.FrameErrorPolicy;
import com.atw.renderboost.benchmark.TerrainBenchmarkGuard;
import java.lang.reflect.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Properties;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.client.renderer.vertex.*;
import org.lwjgl.opengl.*;

/** Dedicated terrain GL ownership; no draw, matrix, texture, program, or quality changes. */
public final class TerrainRuntime {
    private static Thread thread;
    private static Object context, world, format;
    private static TerrainPointerAccess list;
    private static int outerDepth, layerDepth;
    private static boolean active, failed;
    private static volatile boolean dirty = true;
    private static long revision = -1, standaloneDraws, submittedVertices, overrideDraws, scopes, rejectedScopes;
    private static long setupOpportunities;
    private static String reason = "not-observed";
    private static Method currentContext, shaders, regions, bridgeActive;
    private static MethodHandle bridgeGate;
    private static Field destroyed, chunks, initialized;
    private static int unverifiedAllocation;
    private static final IntBuffer ATTRIBUTE_STATE = BufferUtils.createIntBuffer(4);
    private static final TerrainVaoCache CACHE = new TerrainVaoCache(new TerrainVaoCache.Backend() {
        public int allocate() {
            try {
                int id = ARBVertexArrayObject.glGenVertexArrays(); unverifiedAllocation = id; return id;
            } catch (RuntimeException | LinkageError e) { throw new VaoFailure(e); }
        }
        public void bind(int id) {
            try {
                ARBVertexArrayObject.glBindVertexArray(id);
                if (id != 0 && id == unverifiedAllocation) {
                    unverifiedAllocation = 0;
                    if (!ARBVertexArrayObject.glIsVertexArray(id)) throw new IllegalStateException("Driver refused VAO");
                }
            } catch (RuntimeException | LinkageError e) { throw new VaoFailure(e); }
        }
        public void originalBind(Object buffer) { ((VertexBuffer)buffer).bindBuffer(); }
        public void delete(int id) {
            try { ARBVertexArrayObject.glDeleteVertexArrays(id); }
            catch (RuntimeException | LinkageError e) { throw new VaoFailure(e); }
        }
        public void originalPointers(Object buffer) { list.atwboost$originalPointers(buffer); }
        public void restoreDefaultClientSelector() {
            try { OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit); }
            catch (RuntimeException | LinkageError e) { throw new VaoFailure(e); }
        }
        public void enableBlockArrays() {
            try {
                GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY); GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
                GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            } catch (RuntimeException | LinkageError e) { throw new VaoFailure(e); }
        }
    }, TerrainControl.MAX_OWNED_VAOS);
    private static final class VaoFailure extends RuntimeException { VaoFailure(Throwable cause) { super(cause); } }
    static {
        TerrainControl.registerRuntime(new TerrainControl.RuntimeStatus() {
            public boolean active() { return TerrainRuntime.active(); }
            public String status() { return TerrainRuntime.status(); }
            public void export(Properties p) { TerrainRuntime.export(p); }
        });
    }
    private TerrainRuntime() {}
    public static void frameThread() {
        thread = Thread.currentThread();
        if (CACHE.size() == 0 || (!dirty && revision == TerrainControl.revision())) return;
        try {
            Object current = contextToken();
            if (current != context || current == null) CACHE.abandonContext();
            else if (!CACHE.inScope() && GL11.glGetInteger(GL11.GL_LIST_INDEX) == 0
                    && GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) == 0) CACHE.clear(current);
            else return;
            dirty = false; revision = TerrainControl.revision();
            RenderRuntime.cancelBenchmark("Terrain cache invalidated");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            failed = true; reason = "deferred-cleanup-failure"; CACHE.abandonContext();
        }
    }
    public static boolean active() { return TerrainBenchmarkGuard.cacheActive(benchmarkState(), 0); }
    public static void outerEnter() { if (Thread.currentThread() == thread) outerDepth++; }
    public static void outerExit() { if (Thread.currentThread() == thread && outerDepth > 0) outerDepth--; }
    public static void invalidate() { dirty = true; }

    public static void layerEnter(Object renderList, Object layer) {
        if (Thread.currentThread() != thread) return;
        if (layerDepth++ != 0) {
            failed = true; reason = "nested-layer";
            if (CACHE.inScope()) CACHE.exit(); active = false; return;
        }
        active = false; scopes++;
        if (!TerrainControl.requested()) { reason = "OFF"; return; }
        if (!TerrainControl.available()) { reject("missing-or-rejected-hooks"); return; }
        if (failed || CACHE.failed()) { reject("session-failure"); return; }
        if (FrameErrorPolicy.errorFallback()) { reject("observed-gl-error"); return; }
        if (outerDepth != 1 || renderList.getClass() != VboRenderList.class || !(renderList instanceof TerrainPointerAccess)) {
            reject("unknown-or-unscoped-container"); return;
        }
        list = (TerrainPointerAccess)renderList;
        try {
            if (currentContext == null) initializeReaders();
            if (!initialized.getBoolean(renderList)) { reject("uninitialized-container"); return; }
            if ((Boolean)regions.invoke(null)) { reject("render-regions-active"); return; }
            if ((Boolean)shaders.invoke(null)) { reject("shaders-active"); return; }
            if ((Boolean)bridgeActive.invoke(null)) { reject("unverified-nonnull-lunar-matrix-bridge"); return; }
            Object current = contextToken();
            if (current == null) { reject("no-live-context"); return; }
            ContextCapabilities caps = GLContext.getCapabilities();
            if (!caps.OpenGL21 || !(caps.OpenGL30 || caps.GL_ARB_vertex_array_object)
                    || (caps.OpenGL31 && !caps.GL_ARB_compatibility)) { reject("compatibility-vao-unavailable"); return; }
            if (!OpenGlHelper.useVbo()) { reject("effective-vbo-unavailable"); return; }
            if (OpenGlHelper.defaultTexUnit != GL13.GL_TEXTURE0 || OpenGlHelper.lightmapTexUnit != GL13.GL_TEXTURE1
                    || OpenGlHelper.GL_ARRAY_BUFFER != GL15.GL_ARRAY_BUFFER) { reject("unknown-texture-units-or-buffer-target"); return; }
            if (GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != 0
                    || GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE) != GL13.GL_TEXTURE0
                    || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0
                    || GL11.glGetInteger(GL11.GL_LIST_INDEX) != 0) { reject("foreign-vao-selector-program-or-recording"); return; }
            if (!expectedArrays(caps)) { reject("unknown-enabled-arrays"); return; }
            Object block = DefaultVertexFormats.BLOCK;
            if (!exactBlock(block)) { reject("unknown-block-format"); return; }
            if (!exactChunkBuffers(renderList, layer, block)) { reject("unknown-chunk-buffer-or-list"); return; }
            Object currentWorld = Minecraft.getMinecraft().theWorld;
            if (dirty || revision != TerrainControl.revision() || context != current || world != currentWorld || format != block) {
                CACHE.clear(current); dirty = false; revision = TerrainControl.revision();
                context = current; world = currentWorld; format = block;
                RenderRuntime.cancelBenchmark("Terrain cache/context/world/resources invalidated");
            }
            active = CACHE.enter(current, true); reason = active ? "eligible" : "cache-fallback";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            failed = true; reject("guard-failure:" + failure.getClass().getSimpleName());
        }
    }
    private static void reject(String text) { active = false; reason = text; rejectedScopes++; }
    private static void initializeReaders() throws ReflectiveOperationException {
        ClassLoader loader = VboRenderList.class.getClassLoader();
        Class<?> config = Class.forName("net.optifine.Config", false, loader);
        Class<?> ctx = Class.forName("org.lwjgl.opengl.ContextGL", false, GLContext.class.getClassLoader());
        currentContext = ctx.getDeclaredMethod("getCurrentContext"); currentContext.setAccessible(true);
        destroyed = ctx.getDeclaredField("destroyed"); destroyed.setAccessible(true);
        shaders = config.getDeclaredMethod("isShaders"); regions = config.getDeclaredMethod("isRenderRegions");
        bridgeActive = GlStateManager.class.getDeclaredMethod("atwboost$bridgeActive");
        bridgeGate = MethodHandles.lookup().unreflect(bridgeActive);
        chunks = ChunkRenderContainer.class.getDeclaredField("renderChunks"); chunks.setAccessible(true);
        initialized = ChunkRenderContainer.class.getDeclaredField("initialized"); initialized.setAccessible(true);
    }
    private static boolean exactChunkBuffers(Object renderList, Object layer, Object block) throws IllegalAccessException {
        if (layer == null || layer.getClass() != EnumWorldBlockLayer.class) return false;
        Object elements = chunks.get(renderList);
        if (elements == null || elements.getClass() != java.util.ArrayList.class) return false;
        java.util.ArrayList<?> all = (java.util.ArrayList<?>)elements;
        int index = ((EnumWorldBlockLayer)layer).ordinal();
        for (int i = 0; i < all.size(); i++) {
            Object object = all.get(i);
            if (object == null || object.getClass() != RenderChunk.class) return false;
            VertexBuffer vb = ((RenderChunk)object).getVertexBufferByLayer(index);
            if (vb == null || vb.getClass() != VertexBuffer.class || !(vb instanceof TerrainBufferAccess)) return false;
            TerrainBufferAccess b = (TerrainBufferAccess)vb;
            if (b.atwboost$region() != null || b.atwboost$vbo() <= 0 || b.atwboost$format() != block) return false;
        }
        return true;
    }
    private static Object contextToken() throws ReflectiveOperationException {
        Object token = currentContext.invoke(null);
        return token != null && !destroyed.getBoolean(token) ? token : null;
    }
    private static boolean exactBlock(Object object) {
        if (object == null || object.getClass() != VertexFormat.class) return false;
        VertexFormat f = (VertexFormat)object;
        if (f.getNextOffset() != 28 || f.getElementCount() != 4) return false;
        int[] offsets = {0, 12, 16, 24}, counts = {3, 4, 2, 2}, indices = {0, 0, 0, 1};
        VertexFormatElement.EnumUsage[] usages = {VertexFormatElement.EnumUsage.POSITION,
                VertexFormatElement.EnumUsage.COLOR, VertexFormatElement.EnumUsage.UV, VertexFormatElement.EnumUsage.UV};
        VertexFormatElement.EnumType[] types = {VertexFormatElement.EnumType.FLOAT,
                VertexFormatElement.EnumType.UBYTE, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumType.SHORT};
        for (int i = 0; i < 4; i++) {
            VertexFormatElement e = f.getElement(i);
            if (e.getClass() != VertexFormatElement.class || f.getOffset(i) != offsets[i]
                    || e.getElementCount() != counts[i] || e.getIndex() != indices[i]
                    || e.getUsage() != usages[i] || e.getType() != types[i]) return false;
        }
        return true;
    }
    private static boolean expectedArrays(ContextCapabilities caps) {
        if (!GL11.glIsEnabled(GL11.GL_VERTEX_ARRAY) || !GL11.glIsEnabled(GL11.GL_COLOR_ARRAY)
                || GL11.glIsEnabled(GL11.GL_NORMAL_ARRAY) || GL11.glIsEnabled(GL11.GL_INDEX_ARRAY)
                || GL11.glIsEnabled(GL11.GL_EDGE_FLAG_ARRAY) || GL11.glIsEnabled(GL14.GL_SECONDARY_COLOR_ARRAY)
                || GL11.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY)
                || (caps.GL_ARB_vertex_program && GL11.glIsEnabled(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB))
                || (caps.GL_ARB_fragment_program && GL11.glIsEnabled(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB))) return false;
        int attributes = GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);
        if (attributes < 1 || attributes > 64) return false;
        for (int i = 0; i < attributes; i++) {
            ATTRIBUTE_STATE.clear(); GL20.glGetVertexAttrib(i, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, ATTRIBUTE_STATE);
            if (ATTRIBUTE_STATE.get(0) != 0) return false;
        }
        int units = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS);
        if (units < 2 || units > 32) return false;
        boolean matches = true;
        try {
            for (int i = 0; i < units; i++) {
                OpenGlHelper.setClientActiveTexture(GL13.GL_TEXTURE0 + i);
                if (GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY) != (i < 2)) matches = false;
            }
        } finally { OpenGlHelper.setClientActiveTexture(GL13.GL_TEXTURE0); }
        return matches;
    }
    public static void pointers(Object renderList, Object buffer) {
        if (Thread.currentThread() == thread) setupOpportunities++;
        if (!active || Thread.currentThread() != thread || renderList != list) {
            ((TerrainPointerAccess)renderList).atwboost$originalPointers(buffer); return;
        }
        try {
            if (bridgeGateActive()) {
                CACHE.exit(); active = false; failed = true; reason = "bridge-became-nonnull-during-layer";
                RenderRuntime.cancelBenchmark("Opaque Lunar matrix bridge activated");
                ((TerrainPointerAccess)renderList).atwboost$originalPointers(buffer); return;
            }
            if (contextToken() != context) {
                CACHE.abandonContext(); active = false; failed = true; reason = "context-changed-during-layer";
                ((TerrainPointerAccess)renderList).atwboost$originalPointers(buffer); return;
            }
            if (buffer.getClass() != VertexBuffer.class || !(buffer instanceof TerrainBufferAccess)) {
                CACHE.pointers(buffer, 0, null, false); return;
            }
            TerrainBufferAccess b = (TerrainBufferAccess)buffer;
            Object vertexFormat = b.atwboost$format();
            CACHE.pointers(buffer, b.atwboost$vbo(), vertexFormat, !dirty && b.atwboost$region() == null && vertexFormat == format);
        } catch (ReflectiveOperationException e) {
            failed = true; reason = "context-or-bridge-reader-failure";
            if (CACHE.inScope()) CACHE.exit(); active = false;
            ((TerrainPointerAccess)renderList).atwboost$originalPointers(buffer);
        } catch (VaoFailure failure) {
            failed = true; reason = "vao-driver-failure";
            active = false;
            // Only our additional GL operation failed; the original bind/pointers have not run.
            CACHE.recoverAddedFailure(failure, buffer);
        }
    }
    private static boolean bridgeGateActive() throws ReflectiveOperationException {
        try { return (boolean)bridgeGate.invokeExact(); }
        catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new ReflectiveOperationException(e); }
    }
    public static void layerExit() { if (Thread.currentThread() == thread && layerDepth == 1 && CACHE.inScope()) CACHE.exit(); }
    public static void layerReturn() {
        if (Thread.currentThread() != thread || layerDepth == 0) return;
        if (--layerDepth == 0) {
            // The captured initialized=false early return never reaches the layer epilogue.
            if (CACHE.inScope()) CACHE.exit();
            active = false; list = null;
        }
    }
    public static Throwable layerAbort(Throwable original) {
        if (Thread.currentThread() == thread) {
            if (CACHE.inScope()) CACHE.abort(original);
            failed = true; reason = "original-layer-threw"; layerReturn();
        }
        return original;
    }
    public static void bufferInvalidated(Object buffer) {
        if (CACHE.size() == 0) return;
        if (Thread.currentThread() != thread) { dirty = true; return; }
        try {
            if (contextToken() != context) CACHE.abandonContext();
            else CACHE.invalidateBuffer(buffer);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            failed = true; dirty = true; reason = "invalidation-failure";
        }
    }
    public static void drawCompleted(Object buffer) {
        if (Thread.currentThread() == thread && buffer.getClass() == VertexBuffer.class && buffer instanceof TerrainBufferAccess) {
            TerrainBufferAccess b = (TerrainBufferAccess)buffer;
            if (b.atwboost$region() != null) return;
            standaloneDraws++; submittedVertices += b.atwboost$count(); if (b.atwboost$mode() > 0) overrideDraws++;
        }
    }
    public static String status() {
        return "terrainScope=" + reason + ", terrainFailed=" + (failed || CACHE.failed())
                + ", terrainCacheHits=" + CACHE.hits + ", terrainMisses=" + CACHE.misses
                + ", terrainOwnedVaos=" + CACHE.size() + "/" + TerrainControl.MAX_OWNED_VAOS
                + ", terrainStandaloneDraws=" + standaloneDraws + ", terrainSubmittedVertices=" + submittedVertices;
    }
    public static void export(Properties p) {
        p.setProperty("terrainFallback", reason); p.setProperty("terrainFailed", String.valueOf(failed || CACHE.failed()));
        p.setProperty("terrainCacheActive", String.valueOf(TerrainControl.requested() && TerrainControl.available() && active()));
        p.setProperty("terrainSetupOpportunities", String.valueOf(setupOpportunities));
        p.setProperty("terrainEligibleSetupOpportunities", String.valueOf(CACHE.opportunities));
        p.setProperty("terrainCacheHits", String.valueOf(CACHE.hits)); p.setProperty("terrainCacheMisses", String.valueOf(CACHE.misses));
        p.setProperty("terrainVaoAllocations", String.valueOf(CACHE.allocations)); p.setProperty("terrainVaoEvictions", String.valueOf(CACHE.evictions));
        p.setProperty("terrainVaoInvalidations", String.valueOf(CACHE.invalidations)); p.setProperty("terrainBaselineRestorations", String.valueOf(CACHE.restorations));
        p.setProperty("terrainOwnedVaos", String.valueOf(CACHE.size())); p.setProperty("terrainRejectedScopes", String.valueOf(rejectedScopes));
        p.setProperty("terrainScopes", String.valueOf(scopes)); p.setProperty("terrainStandaloneDraws", String.valueOf(standaloneDraws));
        p.setProperty("terrainSubmittedVertices", String.valueOf(submittedVertices)); p.setProperty("terrainOverrideDraws", String.valueOf(overrideDraws));
        p.setProperty("terrainDrawCountsObserved", String.valueOf(standaloneDraws > 0));
    }
    private static final String[] COUNTER_KEYS = {"terrainSetupOpportunities", "terrainEligibleSetupOpportunities",
            "terrainCacheHits", "terrainCacheMisses", "terrainVaoAllocations", "terrainVaoEvictions",
            "terrainVaoInvalidations", "terrainBaselineRestorations", "terrainRejectedScopes", "terrainScopes",
            "terrainStandaloneDraws", "terrainSubmittedVertices", "terrainOverrideDraws"};
    public static long[] counters() {
        return new long[]{setupOpportunities, CACHE.opportunities, CACHE.hits, CACHE.misses, CACHE.allocations,
            CACHE.evictions, CACHE.invalidations, CACHE.restorations, rejectedScopes, scopes,
            standaloneDraws, submittedVertices, overrideDraws};
    }
    public static void exportDelta(Properties properties, long[] start) {
        export(properties); long[] end = counters();
        for (int i = 0; i < COUNTER_KEYS.length; i++) properties.setProperty(COUNTER_KEYS[i], String.valueOf(end[i] - start[i]));
        properties.setProperty("terrainCacheActive", String.valueOf(TerrainBenchmarkGuard.cacheActive(benchmarkState(), start[2])));
        properties.setProperty("terrainDrawCountsObserved", String.valueOf(end[10] > start[10]));
    }
    public static TerrainBenchmarkGuard.State benchmarkState() {
        return new TerrainBenchmarkGuard.State(TerrainControl.requested(), TerrainControl.available(),
                reason.equals("eligible"), failed || CACHE.failed(), FrameErrorPolicy.errorFallback(),
                TerrainControl.revision(), rejectedScopes, CACHE.fallbacks, CACHE.hits);
    }
}
