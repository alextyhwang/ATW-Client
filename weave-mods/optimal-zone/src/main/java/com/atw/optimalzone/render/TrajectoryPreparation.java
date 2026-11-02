package com.atw.optimalzone.render;

import net.minecraft.util.AxisAlignedBB;

import java.util.Arrays;

/** Source-only experiment. Exact classes do not qualify transformed Lunar methods. */
final class TrajectoryPreparation {
    static final int CAPACITY = 256;
    boolean requested; // Deliberately no property, file, or saved activation.
    Scratch current;
    String fallback = "off";
    boolean lastComputationActive;
    long renderCalls, requestedRenderCalls, activeRenderCalls, activeComputations, computeCalls;
    long reuses, misses, baselinePreparations, invalidations, fallbackPreparations;

    static long increment(long value) {
        return value == Long.MAX_VALUE ? value : value + 1;
    }

    void invalidate(String reason) {
        fallback = reason;
        lastComputationActive = false;
        if (current != null) current.disable();
    }

    void setRequested(boolean value) {
        invalidate(value ? "awaiting-computation" : "off");
        requested = value;
    }

    void disconnect() {
        setRequested(false);
        fallback = "disconnected";
    }

    void clear() {
        invalidate(requested ? "cleared-awaiting-computation" : "off");
        renderCalls = requestedRenderCalls = activeRenderCalls = activeComputations = computeCalls = 0;
        reuses = misses = baselinePreparations = invalidations = fallbackPreparations = 0;
    }

    String[] status() {
        return new String[]{
                "Trajectoryprep requested=" + requested + " available=true (source experiment) activeNow="
                        + (current != null && !current.disabled) + " fallback=" + fallback
                        + " liveQualified=false; default OFF each startup",
                "Trajectoryprep renderCalls=" + renderCalls + " requestedRenderCalls=" + requestedRenderCalls
                        + " activeRenderCalls=" + activeRenderCalls
                        + " computeCalls=" + computeCalls + " activeComputations=" + activeComputations
                        + " (callbacks/computations, not frames)",
                "Trajectoryprep hitboxReuse=" + reuses + " misses=" + misses
                        + " baselinePreparations=" + baselinePreparations + " fallbackPreparations="
                        + fallbackPreparations + " keyInvalidations=" + invalidations + " slots<=256; no timing"
        };
    }

    /** Ordinal index retains iteration order; identity and exact keys prevent stale slot reuse. */
    static final class Scratch {
        Object world;
        final Object[] targets = new Object[CAPACITY];
        final AxisAlignedBB[] originals = new AxisAlignedBB[CAPACITY];
        final AxisAlignedBB[] derived = new AxisAlignedBB[CAPACITY];
        final double[][] keys = new double[9][CAPACITY];
        boolean disabled;
        boolean countedActive;

        Scratch(Object world) { this.world = world; }

        boolean matches(int slot, Object target, AxisAlignedBB box, double x, double y, double z) {
            return targets[slot] == target && originals[slot] == box && derived[slot] != null
                    && same(keys[0][slot], box.minX) && same(keys[1][slot], box.minY)
                    && same(keys[2][slot], box.minZ) && same(keys[3][slot], box.maxX)
                    && same(keys[4][slot], box.maxY) && same(keys[5][slot], box.maxZ)
                    && same(keys[6][slot], x) && same(keys[7][slot], y) && same(keys[8][slot], z);
        }

        void store(int slot, Object target, AxisAlignedBB box, double x, double y, double z, AxisAlignedBB result) {
            targets[slot] = target;
            originals[slot] = box;
            derived[slot] = result;
            keys[0][slot] = box.minX; keys[1][slot] = box.minY; keys[2][slot] = box.minZ;
            keys[3][slot] = box.maxX; keys[4][slot] = box.maxY; keys[5][slot] = box.maxZ;
            keys[6][slot] = x; keys[7][slot] = y; keys[8][slot] = z;
        }

        void clearSlot(int slot) {
            targets[slot] = originals[slot] = derived[slot] = null;
        }

        void disable() {
            disabled = true;
            world = null;
            Arrays.fill(targets, null);
            Arrays.fill(originals, null);
            Arrays.fill(derived, null);
        }

        static boolean same(double a, double b) {
            return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
        }

        static boolean eligible(AxisAlignedBB box, double x, double y, double z) {
            return box != null && box.getClass() == AxisAlignedBB.class
                    && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                    && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                    && box.minX <= box.maxX && box.minY <= box.maxY && box.minZ <= box.maxZ;
        }
    }
}
