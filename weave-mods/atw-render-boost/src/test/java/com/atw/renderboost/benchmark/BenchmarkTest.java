package com.atw.renderboost.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkTest {
    @Test void percentilesUseNearestRankAndFpsUsesTotalTime() {
        long[] data = new long[100];
        for (int i = 0; i < 100; i++) data[i] = (100 - i) * 1_000_000L;
        FrameStatistics s = new FrameStatistics(data, data.length);
        assertEquals(50, s.medianMs); assertEquals(95, s.p95Ms); assertEquals(99, s.p99Ms);
        assertEquals(100 * 1e9 / 5_050_000_000L, s.averageFps, 1e-12);
        assertEquals(100_000_000, data[0], "Do not mutate caller's samples");
    }
    @Test void tinySamplesAndInvalidSamples() {
        FrameStatistics s = new FrameStatistics(new long[]{10_000_000}, 1);
        assertEquals(10, s.medianMs); assertEquals(10, s.p99Ms); assertEquals(100, s.averageFps);
        assertThrows(IllegalArgumentException.class, () -> new FrameStatistics(new long[0], 0));
        assertThrows(IllegalArgumentException.class, () -> new FrameStatistics(new long[]{0}, 1));
        assertThrows(IllegalArgumentException.class, () -> new FrameStatistics(new long[]{-2}, 1));
    }
    @Test void warmupAndItsCrossingIntervalAreExcluded() {
        BenchmarkSession b = new BenchmarkSession(5, 2);
        assertFalse(b.frame(100)); assertFalse(b.frame(1_000_000_100L));
        assertFalse(b.frame(2_100_000_100L)); assertEquals(0, b.count());
        assertFalse(b.frame(3_100_000_100L)); assertEquals(1, b.count());
        assertTrue(b.frame(7_100_000_100L)); assertEquals(2, b.count());
        assertArrayEquals(new long[]{1_000_000_000L, 4_000_000_000L}, b.samples());
        assertEquals(0.4, b.statistics().averageFps, 1e-12);
        assertTrue(b.frame(8_100_000_100L)); assertEquals(2, b.count());
    }
    @Test void clockAndBoundsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkSession(4, 10));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkSession(241, 10));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkSession(30, 0));
        BenchmarkSession b = new BenchmarkSession(5, 1); b.frame(100);
        assertThrows(IllegalArgumentException.class, () -> b.frame(50));
        assertThrows(IllegalStateException.class, b::statistics);
    }
    @Test void sampleStorageCannotGrowWithoutBound() {
        BenchmarkSession b = new BenchmarkSession(240, 1); b.frame(0); b.frame(1_000_000_000);
        for (int i = 1; i <= BenchmarkSession.MAX_SAMPLES; i++) b.frame(1_000_000_000L + i);
        assertEquals(BenchmarkSession.MAX_SAMPLES, b.count());
        assertThrows(IllegalStateException.class, () -> b.frame(1_000_000_000L + BenchmarkSession.MAX_SAMPLES + 1));
    }
}
