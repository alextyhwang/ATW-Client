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
    private static final double EXPANDED_BLOCKS_PER_PIXEL = 2.25D;
    private static final int NEAR_REFRESH_RADIUS = 24;
    private static final int INITIAL_CENTER_RADIUS = 16;
    private static final long SAMPLE_BUDGET_NANOS = 500000L;
    private static final int MAX_SAMPLES_PER_TICK = 768;
    private static final int NEAR_REFRESH_DIVISOR = 5;
    private static final int BRIDGE_SCAN_ABOVE = 4;
    private static final int BRIDGE_SCAN_BELOW = 8;
    private static final int HEIGHTMAP_FALLBACK_DEPTH = 4;
    private static final long PERFORMANCE_LOG_INTERVAL_NANOS = 10000000000L;
    private static final int VOID_COLOR = 0xF0101018;
    private static final int UNLOADED_COLOR = 0xF0181820;
    private static final int UNKNOWN_BLOCK_COLOR = 0xF05A5A5A;

    private final TerrainCache normalCache = new TerrainCache(NORMAL_BLOCKS_PER_PIXEL, NORMAL_VISIBLE_HALF_SIZE);
    private final TerrainCache expandedCache = new TerrainCache(EXPANDED_BLOCKS_PER_PIXEL, EXPANDED_VISIBLE_HALF_SIZE);

    private boolean terrainEnabled = true;
    private long performanceWindowStartNanos;
    private long hudRenderTotalNanos;
    private long hudRenderMaxNanos;
    private long sampleTickTotalNanos;
    private long sampleTickMaxNanos;
    private long uploadTickTotalNanos;
    private long uploadTickMaxNanos;
    private long sampledPixels;
    private long uploadedPixels;
    private int hudFrameCount;
    private int sampleTickCount;
    private int uploadTickCount;

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

    void tick(World world, double localX, double localY, double localZ, boolean expandedActive) {
        if (!terrainEnabled || world == null) {
            return;
        }

        TerrainCache activeCache = expandedActive ? expandedCache : normalCache;
        TerrainCache inactiveCache = expandedActive ? normalCache : expandedCache;
        activeCache.prepare(world, localX, localY, localZ);
        if (inactiveCache.isStarted()) {
            inactiveCache.prepare(world, localX, localY, localZ);
        }

        long sampleStartNanos = System.nanoTime();
        long sampleDeadlineNanos = sampleStartNanos + SAMPLE_BUDGET_NANOS;
        int samples = activeCache.sample(world, sampleDeadlineNanos, MAX_SAMPLES_PER_TICK);
        int remainingSamples = MAX_SAMPLES_PER_TICK - samples;
        if (remainingSamples > 0 && inactiveCache.isStarted() && System.nanoTime() < sampleDeadlineNanos) {
            samples += inactiveCache.sample(world, sampleDeadlineNanos, remainingSamples);
        }
        long sampleEndNanos = System.nanoTime();

        long uploadStartNanos = sampleEndNanos;
        int uploaded = activeCache.flushDirtyTexture();
        if (inactiveCache.isStarted()) {
            uploaded += inactiveCache.flushDirtyTexture();
        }
        long uploadEndNanos = System.nanoTime();

        recordTerrainTick(sampleEndNanos - sampleStartNanos, uploadEndNanos - uploadStartNanos, samples, uploaded);
        maybeLogPerformance(uploadEndNanos);
    }

    void draw(
            boolean expanded,
            float centerX,
            float centerY,
            float mapRadius,
            double localX,
            double localZ,
            double yawSin,
            double yawCos
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
            drawTerrainCircle(cache, centerX, centerY, mapRadius, localX, localZ, yawSin, yawCos);
        } else {
            drawTerrainQuad(cache, centerX, centerY, mapRadius, localX, localZ, yawSin, yawCos);
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

    private void recordTerrainTick(long sampleNanos, long uploadNanos, int samples, int uploaded) {
        long nowNanos = System.nanoTime();
        startPerformanceWindow(nowNanos);
        sampleTickCount++;
        sampleTickTotalNanos += sampleNanos;
        sampleTickMaxNanos = Math.max(sampleTickMaxNanos, sampleNanos);
        sampledPixels += samples;
        if (uploaded > 0) {
            uploadTickCount++;
            uploadTickTotalNanos += uploadNanos;
            uploadTickMaxNanos = Math.max(uploadTickMaxNanos, uploadNanos);
            uploadedPixels += uploaded;
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

        OptimalZoneMod.log(performanceSummary(true));
    }

    private String performanceSummary(boolean reset) {
        double renderAverageMillis = averageMillis(hudRenderTotalNanos, hudFrameCount);
        double sampleAverageMillis = averageMillis(sampleTickTotalNanos, sampleTickCount);
        double uploadAverageMillis = averageMillis(uploadTickTotalNanos, uploadTickCount);
        String summary = String.format(
                Locale.ROOT,
                "[Minimap Perf] frames=%d hudAvg=%.3fms hudMax=%.3fms sampleTicks=%d sampleAvg=%.3fms sampleMax=%.3fms uploadTicks=%d uploadAvg=%.3fms uploadMax=%.3fms sampled=%d uploaded=%d",
                hudFrameCount,
                renderAverageMillis,
                nanosToMillis(hudRenderMaxNanos),
                sampleTickCount,
                sampleAverageMillis,
                nanosToMillis(sampleTickMaxNanos),
                uploadTickCount,
                uploadAverageMillis,
                nanosToMillis(uploadTickMaxNanos),
                sampledPixels,
                uploadedPixels
        );

        if (reset) {
            performanceWindowStartNanos = System.nanoTime();
            hudRenderTotalNanos = 0L;
            hudRenderMaxNanos = 0L;
            sampleTickTotalNanos = 0L;
            sampleTickMaxNanos = 0L;
            uploadTickTotalNanos = 0L;
            uploadTickMaxNanos = 0L;
            sampledPixels = 0L;
            uploadedPixels = 0L;
            hudFrameCount = 0;
            sampleTickCount = 0;
            uploadTickCount = 0;
        }

        return summary;
    }

    private static double averageMillis(long totalNanos, int count) {
        return count == 0 ? 0.0D : nanosToMillis(totalNanos) / count;
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
            double yawCos
    ) {
        float left = centerX - mapRadius;
        float top = centerY - mapRadius;
        float right = centerX + mapRadius;
        float bottom = centerY + mapRadius;

        GL11.glBegin(GL11.GL_QUADS);
        setTerrainTexCoord(cache, -mapRadius, mapRadius, localX, localZ, yawSin, yawCos);
        GL11.glVertex2f(left, bottom);
        setTerrainTexCoord(cache, mapRadius, mapRadius, localX, localZ, yawSin, yawCos);
        GL11.glVertex2f(right, bottom);
        setTerrainTexCoord(cache, mapRadius, -mapRadius, localX, localZ, yawSin, yawCos);
        GL11.glVertex2f(right, top);
        setTerrainTexCoord(cache, -mapRadius, -mapRadius, localX, localZ, yawSin, yawCos);
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
            double yawCos
    ) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        setTerrainTexCoord(cache, 0.0F, 0.0F, localX, localZ, yawSin, yawCos);
        GL11.glVertex2f(centerX, centerY);
        for (int segment = 0; segment <= EXPANDED_MAP_SEGMENTS; segment++) {
            double angle = Math.PI * 2.0D * segment / EXPANDED_MAP_SEGMENTS;
            float offsetX = (float) Math.cos(angle) * mapRadius;
            float offsetY = (float) Math.sin(angle) * mapRadius;
            setTerrainTexCoord(cache, offsetX, offsetY, localX, localZ, yawSin, yawCos);
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
            double yawCos
    ) {
        double localSampleX = localX / cache.blocksPerPixel;
        double localSampleZ = localZ / cache.blocksPerPixel;
        double textureX = cache.physicalBaseX
                + TEXTURE_HALF_SIZE
                + localSampleX - cache.centerSampleX
                + (-yawCos * mapPixelX + yawSin * mapPixelY);
        double textureY = cache.physicalBaseZ
                + TEXTURE_HALF_SIZE
                + localSampleZ - cache.centerSampleZ
                + (-yawSin * mapPixelX - yawCos * mapPixelY);
        GL11.glTexCoord2d((textureX + 0.5D) / TEXTURE_SIZE, (textureY + 0.5D) / TEXTURE_SIZE);
    }

    private static final class TerrainCache {
        private static final int FOOTPRINT_CHUNK_CACHE_SIZE = 4;

        private final double blocksPerPixel;
        private final int visibleHalfSize;
        private final ArrayDeque<SampleTask> pendingTasks = new ArrayDeque<SampleTask>();
        private final boolean[] valid = new boolean[TEXTURE_SIZE * TEXTURE_SIZE];
        private final boolean[] dirty = new boolean[TEXTURE_SIZE * TEXTURE_SIZE];
        private final int[] uploadBuffer = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        private final int[] footprintChunkXs = new int[FOOTPRINT_CHUNK_CACHE_SIZE];
        private final int[] footprintChunkZs = new int[FOOTPRINT_CHUNK_CACHE_SIZE];
        private final Chunk[] footprintChunks = new Chunk[FOOTPRINT_CHUNK_CACHE_SIZE];

        private DynamicTexture texture;
        private int[] pixels;
        private World world;
        private int centerSampleX;
        private int centerSampleZ;
        private int physicalBaseX;
        private int physicalBaseZ;
        private int referenceY;
        private int nearRefreshCursor;
        private int farRefreshCursor;
        private int refreshSelector;
        private int dirtyCount;
        private int footprintChunkCount;
        private boolean needsFullUpload;
        private boolean failed;

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

        private void reset() {
            world = null;
            pendingTasks.clear();
            Arrays.fill(valid, false);
            Arrays.fill(dirty, false);
            centerSampleX = 0;
            centerSampleZ = 0;
            physicalBaseX = 0;
            physicalBaseZ = 0;
            referenceY = 64;
            nearRefreshCursor = 0;
            farRefreshCursor = 0;
            refreshSelector = 0;
            dirtyCount = 0;
            failed = false;
            if (pixels != null) {
                Arrays.fill(pixels, VOID_COLOR);
                needsFullUpload = true;
            }
        }

        private void prepare(World currentWorld, double localX, double localY, double localZ) {
            try {
                ensureTexture();
                int targetCenterX = MathHelper.floor_double(localX / blocksPerPixel);
                int targetCenterZ = MathHelper.floor_double(localZ / blocksPerPixel);
                referenceY = MathHelper.floor_double(localY);
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
            pendingTasks.clear();
            Arrays.fill(valid, false);
            Arrays.fill(dirty, false);
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
                    if (pixels[index] != VOID_COLOR) {
                        pixels[index] = VOID_COLOR;
                        markDirty(index);
                    }
                }
            }
            enqueueTask(minX, maxX, minZ, maxZ);
        }

        private void enqueueTask(int minX, int maxX, int minZ, int maxZ) {
            if (minX <= maxX && minZ <= maxZ) {
                pendingTasks.addLast(new SampleTask(minX, maxX, minZ, maxZ));
            }
        }

        private int sample(World currentWorld, long deadlineNanos, int maxSamples) {
            if (failed || world != currentWorld || maxSamples <= 0) {
                return 0;
            }

            int samples = 0;
            while (samples < maxSamples && System.nanoTime() < deadlineNanos) {
                long coordinate = nextSampleCoordinate();
                int sampleX = (int) (coordinate >> 32);
                int sampleZ = (int) coordinate;
                if (!isWithinVisibleRange(sampleX, sampleZ)) {
                    continue;
                }

                boolean bridgeScan = Math.abs(sampleX - centerSampleX) <= NEAR_REFRESH_RADIUS
                        && Math.abs(sampleZ - centerSampleZ) <= NEAR_REFRESH_RADIUS;
                int color;
                try {
                    color = terrainColor(currentWorld, sampleX, sampleZ, bridgeScan);
                } catch (Throwable throwable) {
                    fail("sample", throwable);
                    break;
                }
                setPixel(sampleX, sampleZ, color);
                samples++;
            }
            return samples;
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

        private int terrainColor(World currentWorld, int sampleX, int sampleZ, boolean bridgeScan) {
            int footprintSize = Math.max(1, (int) Math.ceil(blocksPerPixel));
            double centerBlockX = sampleX * blocksPerPixel;
            double centerBlockZ = sampleZ * blocksPerPixel;
            int startBlockX = MathHelper.floor_double(centerBlockX - (footprintSize - 1) * 0.5D);
            int startBlockZ = MathHelper.floor_double(centerBlockZ - (footprintSize - 1) * 0.5D);
            footprintChunkCount = 0;

            TerrainColumnSample bestSample = null;
            boolean sawLoadedColumn = false;
            for (int offsetZ = 0; offsetZ < footprintSize; offsetZ++) {
                for (int offsetX = 0; offsetX < footprintSize; offsetX++) {
                    TerrainColumnSample sample = terrainColumnSample(
                            currentWorld,
                            startBlockX + offsetX,
                            startBlockZ + offsetZ,
                            bridgeScan
                    );
                    if (sample.unloaded) {
                        continue;
                    }
                    sawLoadedColumn = true;
                    if (!sample.empty && (bestSample == null || sample.surfaceY > bestSample.surfaceY)) {
                        bestSample = sample;
                    }
                }
            }

            if (bestSample != null) {
                return bestSample.color;
            }
            return sawLoadedColumn ? VOID_COLOR : UNLOADED_COLOR;
        }

        private TerrainColumnSample terrainColumnSample(World currentWorld, int blockX, int blockZ, boolean bridgeScan) {
            Chunk chunk = footprintChunk(currentWorld, blockX >> 4, blockZ >> 4);
            if (chunk == null) {
                return TerrainColumnSample.unloaded();
            }

            int localX = blockX & 15;
            int localZ = blockZ & 15;
            int surfaceY = chunk.getHeightValue(localX, localZ) - 1;
            if (bridgeScan) {
                int bridgeY = findBridgeSurface(chunk, localX, localZ);
                if (bridgeY > surfaceY) {
                    surfaceY = bridgeY;
                }
            }

            surfaceY = surfaceYAtOrBelow(chunk, localX, localZ, surfaceY);
            if (surfaceY < 0) {
                return TerrainColumnSample.empty();
            }
            IBlockState state = directBlockState(chunk, localX, surfaceY, localZ);

            Block block = state.getBlock();
            Material material = block.getMaterial();
            int rgb;
            BlockPos surfacePos = new BlockPos(blockX, surfaceY, blockZ);
            if (material == Material.water
                    || material == Material.grass
                    || material == Material.leaves
                    || material == Material.plants
                    || material == Material.vine) {
                rgb = block.colorMultiplier(currentWorld, surfacePos, 0);
            } else {
                MapColor mapColor = block.getMapColor(state);
                rgb = mapColor == null || mapColor == MapColor.airColor ? UNKNOWN_BLOCK_COLOR : mapColor.colorValue;
            }

            return TerrainColumnSample.loaded(surfaceY, 0xF0000000 | shadeTerrain(rgb, surfaceY));
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

        private int findBridgeSurface(Chunk chunk, int localX, int localZ) {
            int top = MathHelper.clamp_int(referenceY + BRIDGE_SCAN_ABOVE, 0, 255);
            int bottom = MathHelper.clamp_int(referenceY - BRIDGE_SCAN_BELOW, 0, 255);
            for (int y = top; y >= bottom; y--) {
                IBlockState state = directBlockState(chunk, localX, y, localZ);
                if (state != null && state.getBlock().getMaterial() != Material.air) {
                    return y;
                }
            }
            return -1;
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

        private void setPixel(int sampleX, int sampleZ, int color) {
            int index = physicalIndex(sampleX, sampleZ);
            if (!valid[index] || pixels[index] != color) {
                pixels[index] = color;
                markDirty(index);
            }
            valid[index] = true;
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

        private int flushDirtyTexture() {
            if (failed || texture == null) {
                return 0;
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
                    return TEXTURE_SIZE * TEXTURE_SIZE;
                }
                if (dirtyCount == 0) {
                    return 0;
                }

                int uploaded = 0;
                int startIndex = firstDirtyIndex();
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
                    uploaded += width * height;
                    startIndex = firstDirtyIndex();
                }
                return uploaded;
            } catch (Throwable throwable) {
                fail("upload", throwable);
                return 0;
            }
        }

        private int firstDirtyIndex() {
            for (int index = 0; index < dirty.length; index++) {
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

    private static final class TerrainColumnSample {
        private final boolean unloaded;
        private final boolean empty;
        private final int surfaceY;
        private final int color;

        private TerrainColumnSample(boolean unloaded, boolean empty, int surfaceY, int color) {
            this.unloaded = unloaded;
            this.empty = empty;
            this.surfaceY = surfaceY;
            this.color = color;
        }

        private static TerrainColumnSample unloaded() {
            return new TerrainColumnSample(true, false, -1, UNLOADED_COLOR);
        }

        private static TerrainColumnSample empty() {
            return new TerrainColumnSample(false, true, -1, VOID_COLOR);
        }

        private static TerrainColumnSample loaded(int surfaceY, int color) {
            return new TerrainColumnSample(false, false, surfaceY, color);
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int floorMod(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }
}
