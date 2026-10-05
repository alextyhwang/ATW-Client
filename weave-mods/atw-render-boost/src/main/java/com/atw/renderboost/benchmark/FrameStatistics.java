package com.atw.renderboost.benchmark;

import java.util.Arrays;

public final class FrameStatistics {
    public final int samples;
    public final double medianMs, p95Ms, p99Ms, averageFps;
    public FrameStatistics(long[] nanos, int count) {
        if (count < 1 || count > nanos.length) throw new IllegalArgumentException("No valid samples");
        long[] sorted = Arrays.copyOf(nanos, count);
        double total = 0;
        for (long n : sorted) {
            if (n <= 0) throw new IllegalArgumentException("Frame interval must be positive");
            total += n;
        }
        Arrays.sort(sorted);
        samples = count;
        medianMs = percentile(sorted, 0.50) / 1e6;
        p95Ms = percentile(sorted, 0.95) / 1e6;
        p99Ms = percentile(sorted, 0.99) / 1e6;
        averageFps = count * 1e9 / total;
    }
    // Nearest-rank percentiles; never average instantaneous FPS values.
    private static long percentile(long[] sorted, double q) {
        return sorted[Math.max(0, (int) Math.ceil(q * sorted.length) - 1)];
    }
}
