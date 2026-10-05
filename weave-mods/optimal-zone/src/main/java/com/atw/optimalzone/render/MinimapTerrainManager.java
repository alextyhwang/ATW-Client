package com.atw.optimalzone.render;

import com.atw.optimalzone.OptimalZoneMod;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;

final class MinimapTerrainManager {
    private static final int TEXTURE_SIZE = 256;
    private static final int TEXTURE_HALF_SIZE = TEXTURE_SIZE / 2;
    private static final int NORMAL_VISIBLE_HALF_SIZE = 92;
    private static final int EXPANDED_VISIBLE_HALF_SIZE = TEXTURE_HALF_SIZE - 2;
    private static final int EXPANDED_MAP_SEGMENTS = 128;
    private static final double NORMAL_BLOCKS_PER_PIXEL = 1.5D;
    private static final double EXPANDED_BLOCKS_PER_PIXEL = 2.0D;
    private static final int NEAR_REFRESH_RADIUS = 24;
    private static final int INITIAL_CENTER_RADIUS = 16;
    private static final long SAMPLE_BUDGET_NANOS = 500000L;
    private static final int MAX_SAMPLES_PER_TICK = 768;
    private static final long BACKGROUND_PREWARM_BUDGET_NANOS = 125000L;
    private static final long BACKGROUND_PREWARM_ACTIVE_HEADROOM_NANOS = 350000L;
    private static final int BACKGROUND_PREWARM_MAX_SAMPLES_PER_TICK = 192;
    private static final int IDLE_LIGHT_SAMPLE_LIMIT = 256;
    private static final int IDLE_MEDIUM_SAMPLE_LIMIT = 128;
    private static final int IDLE_DEEP_SAMPLE_LIMIT = 64;
    private static final int IDLE_LIGHT_TICKS = 20;
    private static final int IDLE_MEDIUM_TICKS = 80;
    private static final int IDLE_DEEP_TICKS = 200;
    private static final int NEAR_REFRESH_DIVISOR = 5;
    private static final int HEIGHTMAP_FALLBACK_DEPTH = 4;
    private static final long PERFORMANCE_LOG_INTERVAL_NANOS = 10000000000L;
    private static final int VOID_COLOR = 0xF0101018;
    private static final int UNLOADED_COLOR = 0xF0181820;
    private static final int UNKNOWN_BLOCK_COLOR = 0xF05A5A5A;
    private static final byte TERRAIN_INVALID = 0;
    private static final byte TERRAIN_SOLID = 1;
    private static final byte TERRAIN_VOID = 2;
    private static final byte TERRAIN_UNLOADED = 3;
    private static final float RELIEF_SHADE_PER_BLOCK = 0.035F;
    private static final float RELIEF_SHADE_LIMIT = 0.16F;
    private static final int COLUMN_UNLOADED = 0;
    private static final int COLUMN_EMPTY = 1;
    private static final int COLUMN_SOLID = 2;
    private static final long COLUMN_UNLOADED_SAMPLE = packColumnSample(COLUMN_UNLOADED, -1, 0);
    private static final long COLUMN_EMPTY_SAMPLE = packColumnSample(COLUMN_EMPTY, -1, 0);

    private final TerrainCache normalCache = new TerrainCache(NORMAL_BLOCKS_PER_PIXEL, NORMAL_VISIBLE_HALF_SIZE);
    private final TerrainCache expandedCache = new TerrainCache(EXPANDED_BLOCKS_PER_PIXEL, EXPANDED_VISIBLE_HALF_SIZE);
    private final UploadResult uploadResult = new UploadResult();
    private final UploadResult prewarmUploadResult = new UploadResult();

    private boolean terrainEnabled = true;
    private long performanceWindowStartNanos;
    private long hudRenderTotalNanos;
    private long hudRenderMaxNanos;
    private long sampleTickTotalNanos;
    private long sampleTickMaxNanos;
    private long prepareTickTotalNanos;
    private long prepareTickMaxNanos;
    private long terrainTickTotalNanos;
    private long terrainTickMaxNanos;
    private long uploadTickTotalNanos;
    private long uploadTickMaxNanos;
    private long prewarmTickTotalNanos;
    private long prewarmTickMaxNanos;
    private long prewarmSampleTickTotalNanos;
    private long prewarmSampleTickMaxNanos;
    private long prewarmUploadTickTotalNanos;
    private long prewarmUploadTickMaxNanos;
    private long sampledPixels;
    private long changedSamples;
    private long sampleLimitTotal;
    private long uploadedPixels;
    private long uploadRectangles;
    private long fullUploads;
    private long prewarmSampledPixels;
    private long prewarmUploadedPixels;
    private long prewarmUploadRectangles;
    private long prewarmFullUploads;
    private int lastSampleLimit;
    private int lastIdleTier;
    private int maxIdleTier;
    private int lastPrewarmSampleLimit;
    private int hudFrameCount;
    private int sampleTickCount;
    private int uploadTickCount;
    private int terrainTickCount;
    private int prewarmTickCount;
    private int prewarmUploadTickCount;

    boolean isTerrainEnabled() {
        return terrainEnabled;
    }

    boolean toggleTerrain() {
        terrainEnabled = !terrainEnabled;
        return terrainEnabled;
    }

    void reset() {
        normalCache.reset();
        expandedCache.reset();
    }

    void tick(World world, double localX, double ignoredLocalY, double localZ, boolean expandedActive) {
        tick(world, localX, ignoredLocalY, localZ, expandedActive, false);
    }

    void tick(World world, double localX, double ignoredLocalY, double localZ, boolean expandedActive, boolean prewarmExpanded) {
        if (!terrainEnabled || world == null) {
            return;
        }

        long terrainTickStartNanos = System.nanoTime();
        TerrainCache activeCache = expandedActive ? expandedCache : normalCache;
        long prepareStartNanos = terrainTickStartNanos;
        activeCache.prepare(world, localX, localZ);
        long prepareEndNanos = System.nanoTime();

        long sampleStartNanos = prepareEndNanos;
        long sampleDeadlineNanos = sampleStartNanos + SAMPLE_BUDGET_NANOS;
        int sampleLimit = activeCache.sampleLimit(MAX_SAMPLES_PER_TICK);
        long sampleResult = activeCache.sample(world, sampleDeadlineNanos, sampleLimit);
        int samples = sampleResultSamples(sampleResult);
        int changed = sampleResultChanges(sampleResult);
        long sampleEndNanos = System.nanoTime();

        long uploadStartNanos = sampleEndNanos;
        uploadResult.reset();
        activeCache.flushDirtyTexture(uploadResult);
        long uploadEndNanos = System.nanoTime();
        activeCache.finishTick(changed, uploadResult.uploadedPixels);
        long tickEndNanos = uploadEndNanos;

        recordTerrainTick(
                prepareEndNanos - prepareStartNanos,
                sampleEndNanos - sampleStartNanos,
                uploadEndNanos - uploadStartNanos,
                uploadEndNanos - terrainTickStartNanos,
                samples,
                changed,
                sampleLimit,
                activeCache.idleBackoffTier(),
                uploadResult
        );
        if (prewarmExpanded
                && !expandedActive
                && uploadEndNanos - terrainTickStartNanos <= BACKGROUND_PREWARM_ACTIVE_HEADROOM_NANOS) {
            tickEndNanos = prewarmExpandedCache(world, localX, localZ);
        }
        maybeLogPerformance(tickEndNanos);
    }

    void draw(
            boolean expanded,
            float centerX,
            float centerY,
            float mapRadius,
            double localX,
            double localZ,
            double yawSin,
            double yawCos,
            double displayBlocksPerPixel
    ) {
        if (!terrainEnabled) {
            return;
        }

        TerrainCache cache = expanded ? expandedCache : normalCache;
        if (!cache.canDraw()) {
            return;
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, cache.texture.getGlTextureId());
        if (expanded) {
            drawTerrainCircle(cache, centerX, centerY, mapRadius, localX, localZ, yawSin, yawCos, displayBlocksPerPixel);
        } else {
            drawTerrainQuad(cache, centerX, centerY, mapRadius, localX, localZ, yawSin, yawCos, displayBlocksPerPixel);
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    void recordHudRender(long renderNanos) {
        long nowNanos = System.nanoTime();
        startPerformanceWindow(nowNanos);
        hudFrameCount++;
        hudRenderTotalNanos += renderNanos;
        hudRenderMaxNanos = Math.max(hudRenderMaxNanos, renderNanos);
        maybeLogPerformance(nowNanos);
    }

    String performanceSummary() {
        return performanceSummary(false);
    }

    void resetPerformance() {
        performanceSummary(true);
    }

    private void recordTerrainTick(
            long prepareNanos,
            long sampleNanos,
            long uploadNanos,
            long totalNanos,
            int samples,
            int changed,
            int sampleLimit,
            int idleTier,
            UploadResult uploadResult
    ) {
        long nowNanos = System.nanoTime();
        startPerformanceWindow(nowNanos);
        terrainTickCount++;
        terrainTickTotalNanos += totalNanos;
        terrainTickMaxNanos = Math.max(terrainTickMaxNanos, totalNanos);
        prepareTickTotalNanos += prepareNanos;
        prepareTickMaxNanos = Math.max(prepareTickMaxNanos, prepareNanos);
        sampleTickCount++;
        sampleTickTotalNanos += sampleNanos;
        sampleTickMaxNanos = Math.max(sampleTickMaxNanos, sampleNanos);
        sampledPixels += samples;
        changedSamples += changed;
        sampleLimitTotal += sampleLimit;
        lastSampleLimit = sampleLimit;
        lastIdleTier = idleTier;
        maxIdleTier = Math.max(maxIdleTier, idleTier);
        if (uploadResult.uploadedPixels > 0) {
            uploadTickCount++;
            uploadTickTotalNanos += uploadNanos;
            uploadTickMaxNanos = Math.max(uploadTickMaxNanos, uploadNanos);
            uploadedPixels += uploadResult.uploadedPixels;
            uploadRectangles += uploadResult.rectangles;
            fullUploads += uploadResult.fullUploads;
        }
    }

    private long prewarmExpandedCache(World world, double localX, double localZ) {
        long prewarmStartNanos = System.nanoTime();
        expandedCache.prepare(world, localX, localZ);
        long prepareEndNanos = System.nanoTime();

        int sampleLimit = expandedCache.sampleLimit(BACKGROUND_PREWARM_MAX_SAMPLES_PER_TICK);
        long sampleDeadlineNanos = prepareEndNanos + BACKGROUND_PREWARM_BUDGET_NANOS;
        long sampleResult = expandedCache.sample(world, sampleDeadlineNanos, sampleLimit);
        int samples = sampleResultSamples(sampleResult);
        int changed = sampleResultChanges(sampleResult);
        long sampleEndNanos = System.nanoTime();

        prewarmUploadResult.reset();
        expandedCache.flushDirtyTexture(prewarmUploadResult);
        long uploadEndNanos = System.nanoTime();
        expandedCache.finishTick(changed, prewarmUploadResult.uploadedPixels);

        recordPrewarmTick(
                sampleEndNanos - prepareEndNanos,
                uploadEndNanos - sampleEndNanos,
                uploadEndNanos - prewarmStartNanos,
                samples,
                sampleLimit,
                prewarmUploadResult
        );
        return uploadEndNanos;
    }

    private void recordPrewarmTick(
            long sampleNanos,
            long uploadNanos,
            long totalNanos,
            int samples,
            int sampleLimit,
            UploadResult uploadResult
    ) {
        prewarmTickCount++;
        prewarmTickTotalNanos += totalNanos;
        prewarmTickMaxNanos = Math.max(prewarmTickMaxNanos, totalNanos);
        prewarmSampleTickTotalNanos += sampleNanos;
        prewarmSampleTickMaxNanos = Math.max(prewarmSampleTickMaxNanos, sampleNanos);
        prewarmSampledPixels += samples;
        lastPrewarmSampleLimit = sampleLimit;
        if (uploadResult.uploadedPixels > 0) {
            prewarmUploadTickCount++;
            prewarmUploadTickTotalNanos += uploadNanos;
            prewarmUploadTickMaxNanos = Math.max(prewarmUploadTickMaxNanos, uploadNanos);
            prewarmUploadedPixels += uploadResult.uploadedPixels;
            prewarmUploadRectangles += uploadResult.rectangles;
            prewarmFullUploads += uploadResult.fullUploads;
        }
    }

    private void startPerformanceWindow(long nowNanos) {
        if (performanceWindowStartNanos == 0L) {
            performanceWindowStartNanos = nowNanos;
        }
    }

    private void maybeLogPerformance(long nowNanos) {
        startPerformanceWindow(nowNanos);
        if (nowNanos - performanceWindowStartNanos < PERFORMANCE_LOG_INTERVAL_NANOS) {
            return;
        }

        OptimalZoneMod.log(performanceSummary(false));
        performanceWindowStartNanos = nowNanos;
    }

    private String performanceSummary(boolean reset) {
        double renderAverageMillis = averageMillis(hudRenderTotalNanos, hudFrameCount);
        double terrainTickAverageMillis = averageMillis(terrainTickTotalNanos, terrainTickCount);
        double prepareAverageMillis = averageMillis(prepareTickTotalNanos, terrainTickCount);
        double sampleAverageMillis = averageMillis(sampleTickTotalNanos, sampleTickCount);
        double sampleLimitAverage = average(sampleLimitTotal, sampleTickCount);
        double uploadAverageMillis = averageMillis(uploadTickTotalNanos, uploadTickCount);
        double prewarmAverageMillis = averageMillis(prewarmTickTotalNanos, prewarmTickCount);
        double prewarmSampleAverageMillis = averageMillis(prewarmSampleTickTotalNanos, prewarmTickCount);
        double prewarmUploadAverageMillis = averageMillis(prewarmUploadTickTotalNanos, prewarmUploadTickCount);
        String summary = String.format(
                Locale.ROOT,
                "[Minimap Perf] frames=%d hudAvg=%.3fms hudMax=%.3fms terrainTicks=%d terrainAvg=%.3fms terrainMax=%.3fms prepareAvg=%.3fms prepareMax=%.3fms sampleTicks=%d sampleAvg=%.3fms sampleMax=%.3fms sampleCapAvg=%.1f sampleCapLast=%d idleNow=%d idleMax=%d changed=%d uploadTicks=%d uploadAvg=%.3fms uploadMax=%.3fms uploadRects=%d fullUploads=%d sampled=%d uploaded=%d prewarmTicks=%d prewarmAvg=%.3fms prewarmMax=%.3fms prewarmSampleAvg=%.3fms prewarmSampleMax=%.3fms prewarmCapLast=%d prewarmUploadTicks=%d prewarmUploadAvg=%.3fms prewarmUploadMax=%.3fms prewarmRects=%d prewarmFullUploads=%d prewarmSampled=%d prewarmUploaded=%d expandedWarm=%d",
                hudFrameCount,
                renderAverageMillis,
                nanosToMillis(hudRenderMaxNanos),
                terrainTickCount,
                terrainTickAverageMillis,
                nanosToMillis(terrainTickMaxNanos),
                prepareAverageMillis,
                nanosToMillis(prepareTickMaxNanos),
                sampleTickCount,
                sampleAverageMillis,
                nanosToMillis(sampleTickMaxNanos),
                sampleLimitAverage,
                lastSampleLimit,
                lastIdleTier,
                maxIdleTier,
                changedSamples,
                uploadTickCount,
                uploadAverageMillis,
                nanosToMillis(uploadTickMaxNanos),
                uploadRectangles,
                fullUploads,
                sampledPixels,
                uploadedPixels,
                prewarmTickCount,
                prewarmAverageMillis,
                nanosToMillis(prewarmTickMaxNanos),
                prewarmSampleAverageMillis,
                nanosToMillis(prewarmSampleTickMaxNanos),
                lastPrewarmSampleLimit,
                prewarmUploadTickCount,
                prewarmUploadAverageMillis,
                nanosToMillis(prewarmUploadTickMaxNanos),
                prewarmUploadRectangles,
                prewarmFullUploads,
                prewarmSampledPixels,
                prewarmUploadedPixels,
                expandedCache.isWarm() ? 1 : 0
        );

        if (reset) {
            performanceWindowStartNanos = System.nanoTime();
            hudRenderTotalNanos = 0L;
            hudRenderMaxNanos = 0L;
            sampleTickTotalNanos = 0L;
            sampleTickMaxNanos = 0L;
            prepareTickTotalNanos = 0L;
            prepareTickMaxNanos = 0L;
            terrainTickTotalNanos = 0L;
            terrainTickMaxNanos = 0L;
            uploadTickTotalNanos = 0L;
            uploadTickMaxNanos = 0L;
            prewarmTickTotalNanos = 0L;
            prewarmTickMaxNanos = 0L;
            prewarmSampleTickTotalNanos = 0L;
            prewarmSampleTickMaxNanos = 0L;
            prewarmUploadTickTotalNanos = 0L;
            prewarmUploadTickMaxNanos = 0L;
            sampledPixels = 0L;
            changedSamples = 0L;
            sampleLimitTotal = 0L;
            uploadedPixels = 0L;
            uploadRectangles = 0L;
            fullUploads = 0L;
            prewarmSampledPixels = 0L;
            prewarmUploadedPixels = 0L;
            prewarmUploadRectangles = 0L;
            prewarmFullUploads = 0L;
            lastSampleLimit = 0;
            lastIdleTier = 0;
            maxIdleTier = 0;
            lastPrewarmSampleLimit = 0;
            hudFrameCount = 0;
            sampleTickCount = 0;
            uploadTickCount = 0;
            terrainTickCount = 0;
            prewarmTickCount = 0;
            prewarmUploadTickCount = 0;
        }

        return summary;
    }

    private static double averageMillis(long totalNanos, int count) {
        return count == 0 ? 0.0D : nanosToMillis(totalNanos) / count;
    }

    private static double average(long total, int count) {
        return count == 0 ? 0.0D : (double) total / (double) count;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1000000.0D;
    }

    private void drawTerrainQuad(
            TerrainCache cache,
            float centerX,
            float centerY,
            float mapRadius,
            double localX,
            double localZ,
            double yawSin,
            double yawCos,
            double displayBlocksPerPixel
    ) {
        float left = centerX - mapRadius;
        float top = centerY - mapRadius;
        float right = centerX + mapRadius;
        float bottom = centerY + mapRadius;

        GL11.glBegin(GL11.GL_QUADS);
        setTerrainTexCoord(cache, -mapRadius, mapRadius, localX, localZ, yawSin, yawCos, displayBlocksPerPixel);
        GL11.glVertex2f(left, bottom);
        setTerrainTexCoord(cache, mapRadius, mapRadius, localX, localZ, yawSin, yawCos, displayBlocksPerPixel);
        GL11.glVertex2f(right, bottom);
        setTerrainTexCoord(cache, mapRadius, -mapRadius, localX, localZ, yawSin, yawCos, displayBlocksPerPixel);
        GL11.glVertex2f(right, top);
        setTerrainTexCoord(cache, -mapRadius, -mapRadius, localX, localZ, yawSin, yawCos, displayBlocksPerPixel);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
    }

    private void drawTerrainCircle(
            TerrainCache cache,
            float centerX,
            float centerY,
            float mapRadius,
            double localX,
            double localZ,
            double yawSin,
            double yawCos,
            double displayBlocksPerPixel
    ) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        setTerrainTexCoord(cache, 0.0F, 0.0F, localX, localZ, yawSin, yawCos, displayBlocksPerPixel);
        GL11.glVertex2f(centerX, centerY);
        for (int segment = 0; segment <= EXPANDED_MAP_SEGMENTS; segment++) {
            double angle = Math.PI * 2.0D * segment / EXPANDED_MAP_SEGMENTS;
            float offsetX = (float) Math.cos(angle) * mapRadius;
            float offsetY = (float) Math.sin(angle) * mapRadius;
            setTerrainTexCoord(cache, offsetX, offsetY, localX, localZ, yawSin, yawCos, displayBlocksPerPixel);
            GL11.glVertex2f(centerX + offsetX, centerY + offsetY);
        }
        GL11.glEnd();
    }

    private void setTerrainTexCoord(
            TerrainCache cache,
            float mapPixelX,
            float mapPixelY,
            double localX,
            double localZ,
            double yawSin,
            double yawCos,
            double displayBlocksPerPixel
    ) {
        double localSampleX = localX / cache.blocksPerPixel;
        double localSampleZ = localZ / cache.blocksPerPixel;
        double texturePixelsPerMapPixel = displayBlocksPerPixel / cache.blocksPerPixel;
        double samplePixelX = mapPixelX * texturePixelsPerMapPixel;
        double samplePixelY = mapPixelY * texturePixelsPerMapPixel;
        double textureX = cache.physicalBaseX
                + TEXTURE_HALF_SIZE
                + localSampleX - cache.centerSampleX
                + (-yawCos * samplePixelX + yawSin * samplePixelY);
        double textureY = cache.physicalBaseZ
                + TEXTURE_HALF_SIZE
                + localSampleZ - cache.centerSampleZ
                + (-yawSin * samplePixelX - yawCos * samplePixelY);
        GL11.glTexCoord2d((textureX + 0.5D) / TEXTURE_SIZE, (textureY + 0.5D) / TEXTURE_SIZE);
    }

    private static final class TerrainCache {
        private static final int FOOTPRINT_CHUNK_CACHE_SIZE = 4;

        private final double blocksPerPixel;
        private final int visibleHalfSize;
        private final ArrayDeque<SampleTask> pendingTasks = new ArrayDeque<SampleTask>();
        private final boolean[] valid = new boolean[TEXTURE_SIZE * TEXTURE_SIZE];
        private final boolean[] dirty = new boolean[TEXTURE_SIZE * TEXTURE_SIZE];
        private final byte[] sampleKinds = new byte[TEXTURE_SIZE * TEXTURE_SIZE];
        private final int[] surfaceHeights = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        private final int[] baseRgbs = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        private final int[] uploadBuffer = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        private final int[] footprintChunkXs = new int[FOOTPRINT_CHUNK_CACHE_SIZE];
        private final int[] footprintChunkZs = new int[FOOTPRINT_CHUNK_CACHE_SIZE];
        private final Chunk[] footprintChunks = new Chunk[FOOTPRINT_CHUNK_CACHE_SIZE];
        private final BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();

        private DynamicTexture texture;
        private int[] pixels;
        private World world;
        private int centerSampleX;
        private int centerSampleZ;
        private int physicalBaseX;
        private int physicalBaseZ;
        private int nearRefreshCursor;
        private int farRefreshCursor;
        private int refreshSelector;
        private int dirtyCount;
        private int footprintChunkCount;
        private int stableRefreshTicks;
        private boolean needsFullUpload;
        private boolean failed;
        private boolean movedThisTick;

        private TerrainCache(double blocksPerPixel, int visibleHalfSize) {
            this.blocksPerPixel = blocksPerPixel;
            this.visibleHalfSize = visibleHalfSize;
        }

        private boolean isStarted() {
            return texture != null;
        }

        private boolean canDraw() {
            return texture != null && !failed;
        }

        private boolean isWarm() {
            return canDraw() && world != null && pendingTasks.isEmpty() && !needsFullUpload && dirtyCount == 0;
        }

        private void reset() {
            world = null;
            pendingTasks.clear();
            Arrays.fill(valid, false);
            Arrays.fill(dirty, false);
            Arrays.fill(sampleKinds, TERRAIN_INVALID);
            Arrays.fill(surfaceHeights, 0);
            Arrays.fill(baseRgbs, 0);
            centerSampleX = 0;
            centerSampleZ = 0;
            physicalBaseX = 0;
            physicalBaseZ = 0;
            nearRefreshCursor = 0;
            farRefreshCursor = 0;
            refreshSelector = 0;
            dirtyCount = 0;
            stableRefreshTicks = 0;
            movedThisTick = false;
            failed = false;
            if (pixels != null) {
                Arrays.fill(pixels, VOID_COLOR);
                needsFullUpload = true;
            }
        }

        private void prepare(World currentWorld, double localX, double localZ) {
            try {
                ensureTexture();
                int targetCenterX = MathHelper.floor_double(localX / blocksPerPixel);
                int targetCenterZ = MathHelper.floor_double(localZ / blocksPerPixel);
                movedThisTick = false;
                if (world != currentWorld) {
                    initializeForWorld(currentWorld, targetCenterX, targetCenterZ);
                    return;
                }

                moveCenter(targetCenterX, targetCenterZ);
            } catch (Throwable throwable) {
                fail("prepare", throwable);
            }
        }

        private void ensureTexture() {
            if (texture != null) {
                return;
            }

            texture = new DynamicTexture(TEXTURE_SIZE, TEXTURE_SIZE);
            pixels = texture.getTextureData();
            Arrays.fill(pixels, VOID_COLOR);
            needsFullUpload = true;
        }

        private void initializeForWorld(World currentWorld, int targetCenterX, int targetCenterZ) {
            world = currentWorld;
            centerSampleX = targetCenterX;
            centerSampleZ = targetCenterZ;
            physicalBaseX = 0;
            physicalBaseZ = 0;
            nearRefreshCursor = 0;
            farRefreshCursor = 0;
            refreshSelector = 0;
            stableRefreshTicks = 0;
            movedThisTick = true;
            pendingTasks.clear();
            Arrays.fill(valid, false);
            Arrays.fill(dirty, false);
            Arrays.fill(sampleKinds, TERRAIN_INVALID);
            Arrays.fill(surfaceHeights, 0);
            Arrays.fill(baseRgbs, 0);
            dirtyCount = 0;
            Arrays.fill(pixels, VOID_COLOR);
            needsFullUpload = true;
            enqueueInitialization();
        }

        private void enqueueInitialization() {
            int centerRadius = Math.min(INITIAL_CENTER_RADIUS, visibleHalfSize);
            enqueueTask(
                    centerSampleX - centerRadius,
                    centerSampleX + centerRadius,
                    centerSampleZ - centerRadius,
                    centerSampleZ + centerRadius
            );
            if (centerRadius >= visibleHalfSize) {
                return;
            }

            enqueueTask(
                    centerSampleX - visibleHalfSize,
                    centerSampleX + visibleHalfSize,
                    centerSampleZ - visibleHalfSize,
                    centerSampleZ - centerRadius - 1
            );
            enqueueTask(
                    centerSampleX - visibleHalfSize,
                    centerSampleX + visibleHalfSize,
                    centerSampleZ + centerRadius + 1,
                    centerSampleZ + visibleHalfSize
            );
            enqueueTask(
                    centerSampleX - visibleHalfSize,
                    centerSampleX - centerRadius - 1,
                    centerSampleZ - centerRadius,
                    centerSampleZ + centerRadius
            );
            enqueueTask(
                    centerSampleX + centerRadius + 1,
                    centerSampleX + visibleHalfSize,
                    centerSampleZ - centerRadius,
                    centerSampleZ + centerRadius
            );
        }

        private void moveCenter(int targetCenterX, int targetCenterZ) {
            int deltaX = targetCenterX - centerSampleX;
            int deltaZ = targetCenterZ - centerSampleZ;
            if (deltaX == 0 && deltaZ == 0) {
                return;
            }

            movedThisTick = true;
            if (Math.abs(deltaX) > visibleHalfSize || Math.abs(deltaZ) > visibleHalfSize) {
                initializeForWorld(world, targetCenterX, targetCenterZ);
                return;
            }

            int previousCenterX = centerSampleX;
            int previousCenterZ = centerSampleZ;
            physicalBaseX = floorMod(physicalBaseX + deltaX, TEXTURE_SIZE);
            physicalBaseZ = floorMod(physicalBaseZ + deltaZ, TEXTURE_SIZE);
            centerSampleX = targetCenterX;
            centerSampleZ = targetCenterZ;

            if (deltaX > 0) {
                invalidateAndEnqueue(
                        previousCenterX + visibleHalfSize + 1,
                        centerSampleX + visibleHalfSize,
                        centerSampleZ - visibleHalfSize,
                        centerSampleZ + visibleHalfSize
                );
            } else if (deltaX < 0) {
                invalidateAndEnqueue(
                        centerSampleX - visibleHalfSize,
                        previousCenterX - visibleHalfSize - 1,
                        centerSampleZ - visibleHalfSize,
                        centerSampleZ + visibleHalfSize
                );
            }

            if (deltaZ > 0) {
                invalidateAndEnqueue(
                        centerSampleX - visibleHalfSize,
                        centerSampleX + visibleHalfSize,
                        previousCenterZ + visibleHalfSize + 1,
                        centerSampleZ + visibleHalfSize
                );
            } else if (deltaZ < 0) {
                invalidateAndEnqueue(
                        centerSampleX - visibleHalfSize,
                        centerSampleX + visibleHalfSize,
                        centerSampleZ - visibleHalfSize,
                        previousCenterZ - visibleHalfSize - 1
                );
            }

            if (pendingTasks.size() > 128) {
                pendingTasks.clear();
                enqueueInitialization();
            }
        }

        private void invalidateAndEnqueue(int minX, int maxX, int minZ, int maxZ) {
            if (minX > maxX || minZ > maxZ) {
                return;
            }

            for (int sampleZ = minZ; sampleZ <= maxZ; sampleZ++) {
                for (int sampleX = minX; sampleX <= maxX; sampleX++) {
                    if (!isWithinVisibleRange(sampleX, sampleZ)) {
                        continue;
                    }
                    int index = physicalIndex(sampleX, sampleZ);
                    valid[index] = false;
                    sampleKinds[index] = TERRAIN_INVALID;
                    surfaceHeights[index] = 0;
                    baseRgbs[index] = 0;
                    if (pixels[index] != VOID_COLOR) {
                        pixels[index] = VOID_COLOR;
                        markDirty(index);
                    }
                    recomposeDependentPixels(sampleX, sampleZ);
                }
            }
            enqueueTask(minX, maxX, minZ, maxZ);
        }

        private void enqueueTask(int minX, int maxX, int minZ, int maxZ) {
            if (minX <= maxX && minZ <= maxZ) {
                pendingTasks.addLast(new SampleTask(minX, maxX, minZ, maxZ));
            }
        }

        private int sampleLimit(int maxSamples) {
            if (needsFullSpeedRefresh()) {
                return maxSamples;
            }

            if (stableRefreshTicks >= IDLE_DEEP_TICKS) {
                return Math.min(maxSamples, IDLE_DEEP_SAMPLE_LIMIT);
            }
            if (stableRefreshTicks >= IDLE_MEDIUM_TICKS) {
                return Math.min(maxSamples, IDLE_MEDIUM_SAMPLE_LIMIT);
            }
            if (stableRefreshTicks >= IDLE_LIGHT_TICKS) {
                return Math.min(maxSamples, IDLE_LIGHT_SAMPLE_LIMIT);
            }
            return maxSamples;
        }

        private boolean needsFullSpeedRefresh() {
            return movedThisTick || needsFullUpload || dirtyCount > 0 || !pendingTasks.isEmpty();
        }

        private int idleBackoffTier() {
            if (needsFullSpeedRefresh()) {
                return 0;
            }
            if (stableRefreshTicks >= IDLE_DEEP_TICKS) {
                return 3;
            }
            if (stableRefreshTicks >= IDLE_MEDIUM_TICKS) {
                return 2;
            }
            if (stableRefreshTicks >= IDLE_LIGHT_TICKS) {
                return 1;
            }
            return 0;
        }

        private void finishTick(int changedSamples, int uploadedPixels) {
            if (movedThisTick
                    || changedSamples > 0
                    || uploadedPixels > 0
                    || needsFullUpload
                    || dirtyCount > 0
                    || !pendingTasks.isEmpty()) {
                stableRefreshTicks = 0;
            } else if (stableRefreshTicks < IDLE_DEEP_TICKS + 1200) {
                stableRefreshTicks++;
            }
            movedThisTick = false;
        }

        private long sample(World currentWorld, long deadlineNanos, int maxSamples) {
            if (failed || world != currentWorld || maxSamples <= 0) {
                return packSampleResult(0, 0);
            }

            int samples = 0;
            int changed = 0;
            while (samples < maxSamples && System.nanoTime() < deadlineNanos) {
                long coordinate = nextSampleCoordinate();
                int sampleX = (int) (coordinate >> 32);
                int sampleZ = (int) coordinate;
                if (!isWithinVisibleRange(sampleX, sampleZ)) {
                    continue;
                }

                boolean topRepairScan = Math.abs(sampleX - centerSampleX) <= NEAR_REFRESH_RADIUS
                        && Math.abs(sampleZ - centerSampleZ) <= NEAR_REFRESH_RADIUS;
                try {
                    if (sampleTerrain(currentWorld, sampleX, sampleZ, topRepairScan)) {
                        changed++;
                    }
                } catch (Throwable throwable) {
                    fail("sample", throwable);
                    break;
                }
                samples++;
            }
            return packSampleResult(samples, changed);
        }

        private long nextSampleCoordinate() {
            while (!pendingTasks.isEmpty()) {
                SampleTask task = pendingTasks.peekFirst();
                long coordinate = task.next();
                if (task.isDone()) {
                    pendingTasks.removeFirst();
                }
                return coordinate;
            }

            if (refreshSelector++ % NEAR_REFRESH_DIVISOR == 0) {
                return nextNearRefreshCoordinate();
            }
            return nextFarRefreshCoordinate();
        }

        private long nextNearRefreshCoordinate() {
            int width = NEAR_REFRESH_RADIUS * 2 + 1;
            int index = nearRefreshCursor++;
            if (nearRefreshCursor >= width * width) {
                nearRefreshCursor = 0;
            }
            int offsetX = index % width - NEAR_REFRESH_RADIUS;
            int offsetZ = index / width - NEAR_REFRESH_RADIUS;
            return pack(centerSampleX + offsetX, centerSampleZ + offsetZ);
        }

        private long nextFarRefreshCoordinate() {
            int width = visibleHalfSize * 2 + 1;
            int index = farRefreshCursor++;
            if (farRefreshCursor >= width * width) {
                farRefreshCursor = 0;
            }
            int offsetX = index % width - visibleHalfSize;
            int offsetZ = index / width - visibleHalfSize;
            return pack(centerSampleX + offsetX, centerSampleZ + offsetZ);
        }

        private boolean sampleTerrain(World currentWorld, int sampleX, int sampleZ, boolean topRepairScan) {
            int footprintSize = Math.max(1, (int) Math.ceil(blocksPerPixel));
            double centerBlockX = sampleX * blocksPerPixel;
            double centerBlockZ = sampleZ * blocksPerPixel;
            int startBlockX = MathHelper.floor_double(centerBlockX - (footprintSize - 1) * 0.5D);
            int startBlockZ = MathHelper.floor_double(centerBlockZ - (footprintSize - 1) * 0.5D);
            footprintChunkCount = 0;

            boolean hasBestSample = false;
            int bestSurfaceY = -1;
            int bestBaseRgb = 0;
            boolean sawLoadedColumn = false;
            for (int offsetZ = 0; offsetZ < footprintSize; offsetZ++) {
                for (int offsetX = 0; offsetX < footprintSize; offsetX++) {
                    long sample = terrainColumnSample(
                            currentWorld,
                            startBlockX + offsetX,
                            startBlockZ + offsetZ,
                            topRepairScan
                    );
                    int kind = columnKind(sample);
                    if (kind == COLUMN_UNLOADED) {
                        continue;
                    }
                    sawLoadedColumn = true;
                    if (kind == COLUMN_SOLID) {
                        int surfaceY = columnSurfaceY(sample);
                        if (!hasBestSample || surfaceY > bestSurfaceY) {
                            hasBestSample = true;
                            bestSurfaceY = surfaceY;
                            bestBaseRgb = columnBaseRgb(sample);
                        }
                    }
                }
            }

            if (hasBestSample) {
                return setSample(sampleX, sampleZ, TERRAIN_SOLID, bestSurfaceY, bestBaseRgb);
            } else if (sawLoadedColumn) {
                return setSample(sampleX, sampleZ, TERRAIN_VOID, -1, 0);
            } else {
                return setSample(sampleX, sampleZ, TERRAIN_UNLOADED, -1, 0);
            }
        }

        private long terrainColumnSample(World currentWorld, int blockX, int blockZ, boolean topRepairScan) {
            Chunk chunk = footprintChunk(currentWorld, blockX >> 4, blockZ >> 4);
            if (chunk == null) {
                return COLUMN_UNLOADED_SAMPLE;
            }

            int localX = blockX & 15;
            int localZ = blockZ & 15;
            int surfaceY = chunk.getHeightValue(localX, localZ) - 1;
            surfaceY = surfaceYAtOrBelow(chunk, localX, localZ, surfaceY);
            if (surfaceY < 0 && topRepairScan) {
                surfaceY = highestStoredSurfaceY(chunk, localX, localZ);
            }
            if (surfaceY < 0) {
                return COLUMN_EMPTY_SAMPLE;
            }
            IBlockState state = directBlockState(chunk, localX, surfaceY, localZ);

            Block block = state.getBlock();
            Material material = block.getMaterial();
            int rgb;
            if (material == Material.water
                    || material == Material.grass
                    || material == Material.leaves
                    || material == Material.plants
                    || material == Material.vine) {
                rgb = block.colorMultiplier(currentWorld, surfacePos.set(blockX, surfaceY, blockZ), 0);
            } else {
                MapColor mapColor = block.getMapColor(state);
                rgb = mapColor == null || mapColor == MapColor.airColor ? UNKNOWN_BLOCK_COLOR : mapColor.colorValue;
            }

            return packColumnSample(COLUMN_SOLID, surfaceY, shadeTerrain(rgb, surfaceY));
        }

        private int highestStoredSurfaceY(Chunk chunk, int localX, int localZ) {
            ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
            for (int section = storages.length - 1; section >= 0; section--) {
                ExtendedBlockStorage storage = storages[section];
                if (storage == null) {
                    continue;
                }

                int sectionBaseY = section << 4;
                for (int localY = 15; localY >= 0; localY--) {
                    IBlockState state = storage.get(localX, localY, localZ);
                    if (state != null && state.getBlock().getMaterial() != Material.air) {
                        return sectionBaseY + localY;
                    }
                }
            }
            return -1;
        }

        private Chunk footprintChunk(World currentWorld, int chunkX, int chunkZ) {
            for (int index = 0; index < footprintChunkCount; index++) {
                if (footprintChunkXs[index] == chunkX && footprintChunkZs[index] == chunkZ) {
                    return footprintChunks[index];
                }
            }

            Chunk chunk = currentWorld.isChunkLoaded(chunkX, chunkZ, false)
                    ? currentWorld.getChunkFromChunkCoords(chunkX, chunkZ)
                    : null;
            if (footprintChunkCount < FOOTPRINT_CHUNK_CACHE_SIZE) {
                footprintChunkXs[footprintChunkCount] = chunkX;
                footprintChunkZs[footprintChunkCount] = chunkZ;
                footprintChunks[footprintChunkCount] = chunk;
                footprintChunkCount++;
            }
            return chunk;
        }

        private int surfaceYAtOrBelow(Chunk chunk, int localX, int localZ, int surfaceY) {
            for (int depth = 0; depth <= HEIGHTMAP_FALLBACK_DEPTH; depth++) {
                int y = surfaceY - depth;
                if (y < 0) {
                    break;
                }
                IBlockState state = directBlockState(chunk, localX, y, localZ);
                if (state != null && state.getBlock().getMaterial() != Material.air) {
                    return y;
                }
            }
            return -1;
        }

        private IBlockState directBlockState(Chunk chunk, int localX, int y, int localZ) {
            if (y < 0 || y > 255) {
                return null;
            }
            ExtendedBlockStorage storage = chunk.getBlockStorageArray()[y >> 4];
            return storage == null ? null : storage.get(localX, y & 15, localZ);
        }

        private int shadeTerrain(int rgb, int y) {
            int red = (rgb >> 16) & 255;
            int green = (rgb >> 8) & 255;
            int blue = rgb & 255;
            float shade = 0.78F + MathHelper.clamp_float((y - 48.0F) / 160.0F, 0.0F, 0.22F);
            red = MathHelper.clamp_int((int) (red * shade), 0, 255);
            green = MathHelper.clamp_int((int) (green * shade), 0, 255);
            blue = MathHelper.clamp_int((int) (blue * shade), 0, 255);
            return red << 16 | green << 8 | blue;
        }

        private boolean setSample(int sampleX, int sampleZ, byte kind, int surfaceY, int baseRgb) {
            int index = physicalIndex(sampleX, sampleZ);
            boolean changed = !valid[index]
                    || sampleKinds[index] != kind
                    || surfaceHeights[index] != surfaceY
                    || baseRgbs[index] != baseRgb;

            if (!changed) {
                valid[index] = true;
                return false;
            }

            sampleKinds[index] = kind;
            surfaceHeights[index] = surfaceY;
            baseRgbs[index] = baseRgb;
            valid[index] = true;
            recomposePixel(sampleX, sampleZ);
            recomposeDependentPixels(sampleX, sampleZ);
            return true;
        }

        private void recomposeDependentPixels(int sampleX, int sampleZ) {
            recomposePixel(sampleX - 1, sampleZ);
            recomposePixel(sampleX, sampleZ - 1);
        }

        private void recomposePixel(int sampleX, int sampleZ) {
            if (!isWithinVisibleRange(sampleX, sampleZ)) {
                return;
            }

            int index = physicalIndex(sampleX, sampleZ);
            int color = composedColor(sampleX, sampleZ, index);
            if (pixels[index] != color) {
                pixels[index] = color;
                markDirty(index);
            }
        }

        private int composedColor(int sampleX, int sampleZ, int index) {
            if (!valid[index]) {
                return VOID_COLOR;
            }

            byte kind = sampleKinds[index];
            if (kind == TERRAIN_VOID) {
                return VOID_COLOR;
            }
            if (kind == TERRAIN_UNLOADED) {
                return UNLOADED_COLOR;
            }
            if (kind != TERRAIN_SOLID) {
                return VOID_COLOR;
            }

            float relief = terrainRelief(sampleX, sampleZ, surfaceHeights[index]);
            return 0xF0000000 | applyRelief(baseRgbs[index], relief);
        }

        private float terrainRelief(int sampleX, int sampleZ, int surfaceY) {
            float heightDelta = 0.0F;
            if (isSolidSample(sampleX + 1, sampleZ)) {
                heightDelta += surfaceY - surfaceHeights[physicalIndex(sampleX + 1, sampleZ)];
            }
            if (isSolidSample(sampleX, sampleZ + 1)) {
                heightDelta += surfaceY - surfaceHeights[physicalIndex(sampleX, sampleZ + 1)];
            }

            return MathHelper.clamp_float(
                    heightDelta * 0.5F * RELIEF_SHADE_PER_BLOCK,
                    -RELIEF_SHADE_LIMIT,
                    RELIEF_SHADE_LIMIT
            );
        }

        private boolean isSolidSample(int sampleX, int sampleZ) {
            if (!isWithinVisibleRange(sampleX, sampleZ)) {
                return false;
            }

            int index = physicalIndex(sampleX, sampleZ);
            return valid[index] && sampleKinds[index] == TERRAIN_SOLID;
        }

        private int applyRelief(int rgb, float relief) {
            float shade = 1.0F + relief;
            int red = (rgb >> 16) & 255;
            int green = (rgb >> 8) & 255;
            int blue = rgb & 255;
            red = MathHelper.clamp_int((int) (red * shade), 0, 255);
            green = MathHelper.clamp_int((int) (green * shade), 0, 255);
            blue = MathHelper.clamp_int((int) (blue * shade), 0, 255);
            return red << 16 | green << 8 | blue;
        }

        private void markDirty(int index) {
            if (!dirty[index]) {
                dirty[index] = true;
                dirtyCount++;
            }
        }

        private int physicalIndex(int sampleX, int sampleZ) {
            int textureX = floorMod(
                    physicalBaseX + sampleX - (centerSampleX - TEXTURE_HALF_SIZE),
                    TEXTURE_SIZE
            );
            int textureZ = floorMod(
                    physicalBaseZ + sampleZ - (centerSampleZ - TEXTURE_HALF_SIZE),
                    TEXTURE_SIZE
            );
            return textureZ * TEXTURE_SIZE + textureX;
        }

        private boolean isWithinVisibleRange(int sampleX, int sampleZ) {
            return Math.abs(sampleX - centerSampleX) <= visibleHalfSize
                    && Math.abs(sampleZ - centerSampleZ) <= visibleHalfSize;
        }

        private void flushDirtyTexture(UploadResult result) {
            if (failed || texture == null) {
                return;
            }

            try {
                if (needsFullUpload) {
                    texture.updateDynamicTexture();
                    GlStateManager.bindTexture(texture.getGlTextureId());
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                    Arrays.fill(dirty, false);
                    dirtyCount = 0;
                    needsFullUpload = false;
                    result.uploadedPixels += TEXTURE_SIZE * TEXTURE_SIZE;
                    result.rectangles++;
                    result.fullUploads++;
                    return;
                }
                if (dirtyCount == 0) {
                    return;
                }

                int startIndex = firstDirtyIndex(0);
                while (startIndex >= 0) {
                    int startY = startIndex / TEXTURE_SIZE;
                    int startX = startIndex - startY * TEXTURE_SIZE;
                    int endX = startX;
                    while (endX + 1 < TEXTURE_SIZE && dirty[startY * TEXTURE_SIZE + endX + 1]) {
                        endX++;
                    }

                    int endY = startY;
                    while (endY + 1 < TEXTURE_SIZE && rowRangeIsDirty(endY + 1, startX, endX)) {
                        endY++;
                    }

                    int width = endX - startX + 1;
                    int height = endY - startY + 1;
                    int uploadIndex = 0;
                    for (int y = startY; y <= endY; y++) {
                        int sourceIndex = y * TEXTURE_SIZE + startX;
                        System.arraycopy(pixels, sourceIndex, uploadBuffer, uploadIndex, width);
                        Arrays.fill(dirty, sourceIndex, sourceIndex + width, false);
                        dirtyCount -= width;
                        uploadIndex += width;
                    }

                    TextureUtil.bindTexture(texture.getGlTextureId());
                    TextureUtil.uploadTextureSub(0, uploadBuffer, width, height, startX, startY, false, false, false);
                    result.uploadedPixels += width * height;
                    result.rectangles++;
                    startIndex = dirtyCount == 0 ? -1 : firstDirtyIndex(startIndex + 1);
                }
            } catch (Throwable throwable) {
                fail("upload", throwable);
            }
        }

        private int firstDirtyIndex(int startAt) {
            for (int index = Math.max(0, startAt); index < dirty.length; index++) {
                if (dirty[index]) {
                    return index;
                }
            }
            return -1;
        }

        private boolean rowRangeIsDirty(int row, int startX, int endX) {
            int rowStart = row * TEXTURE_SIZE;
            for (int x = startX; x <= endX; x++) {
                if (!dirty[rowStart + x]) {
                    return false;
                }
            }
            return true;
        }

        private void fail(String phase, Throwable throwable) {
            if (failed) {
                return;
            }
            failed = true;
            OptimalZoneMod.log("Minimap terrain disabled after " + phase + " error: "
                    + throwable.getClass().getName() + ": " + throwable.getMessage());
        }
    }

    private static final class SampleTask {
        private final int minX;
        private final int maxX;
        private final int maxZ;
        private int currentX;
        private int currentZ;
        private boolean done;

        private SampleTask(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.maxZ = maxZ;
            currentX = minX;
            currentZ = minZ;
        }

        private long next() {
            long coordinate = pack(currentX, currentZ);
            currentX++;
            if (currentX > maxX) {
                currentX = minX;
                currentZ++;
                if (currentZ > maxZ) {
                    done = true;
                }
            }
            return coordinate;
        }

        private boolean isDone() {
            return done;
        }
    }

    private static final class UploadResult {
        private int uploadedPixels;
        private int rectangles;
        private int fullUploads;

        private void reset() {
            uploadedPixels = 0;
            rectangles = 0;
            fullUploads = 0;
        }
    }

    private static long packColumnSample(int kind, int surfaceY, int baseRgb) {
        return ((long) kind << 40)
                | ((long) (surfaceY & 0xFFFF) << 24)
                | (baseRgb & 0xFFFFFFL);
    }

    private static int columnKind(long sample) {
        return (int) (sample >>> 40);
    }

    private static int columnSurfaceY(long sample) {
        return (int) ((sample >>> 24) & 0xFFFFL);
    }

    private static int columnBaseRgb(long sample) {
        return (int) (sample & 0xFFFFFFL);
    }

    private static long packSampleResult(int samples, int changed) {
        return ((long) changed << 32) | (samples & 0xFFFFFFFFL);
    }

    private static int sampleResultSamples(long result) {
        return (int) result;
    }

    private static int sampleResultChanges(long result) {
        return (int) (result >>> 32);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int floorMod(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }
}
