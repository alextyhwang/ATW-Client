package com.atw.renderboost.terrain;

import com.atw.renderboost.FrameErrorPolicyTest;
import com.atw.renderboost.benchmark.BenchmarkSession;
import com.atw.renderboost.benchmark.TerrainBenchmarkGuard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainBenchmarkTest {
    private static TerrainBenchmarkGuard.State state(TerrainVaoCache cache, boolean error) {
        return new TerrainBenchmarkGuard.State(true, true, true, cache.failed(), error, 4, 0, cache.fallbacks, cache.hits);
    }
    private static BenchmarkSession sampling(TerrainBenchmarkGuard.State start) {
        BenchmarkSession session = new BenchmarkSession(5, 1);
        assertFalse(session.frame(0, start)); assertFalse(session.frame(1_000_000_000L, start));
        assertTrue(session.sampling()); assertEquals(0, session.count()); return session;
    }
    private static void rejectsCompletion(BenchmarkSession session, TerrainBenchmarkGuard.State state) {
        int before = session.count();
        assertThrows(IllegalStateException.class, () -> session.frame(6_000_000_000L, state));
        assertEquals(before, session.count()); assertFalse(session.complete());
        assertThrows(IllegalStateException.class, session::statistics);
    }
    @Test void sampledCandidateWithRealCacheHitsThenDriverFailureCannotFinishOrReportActive() {
        TerrainVaoCacheTest.Driver driver = new TerrainVaoCacheTest.Driver();
        TerrainVaoCache cache = new TerrainVaoCache(driver, 4); Object context = new Object();
        TerrainVaoCacheTest.Buffer a = new TerrainVaoCacheTest.Buffer(5), b = new TerrainVaoCacheTest.Buffer(7);
        cache.enter(context, true); TerrainVaoCacheTest.submit(cache, driver, a, "warmup"); cache.exit();
        BenchmarkSession session = sampling(state(cache, false)); long startHits = cache.hits;
        cache.enter(context, true); TerrainVaoCacheTest.submit(cache, driver, a, "sampled-hit"); cache.exit();
        assertFalse(session.frame(2_000_000_000L, state(cache, false)));
        assertTrue(TerrainBenchmarkGuard.cacheActive(state(cache, false), startHits));
        RuntimeException primary = new RuntimeException("driver enable failed"); driver.uv1EnableFailure = primary;
        cache.enter(context, true);
        assertSame(primary, assertThrows(RuntimeException.class, () -> cache.pointers(b, b.name, b.format, true)));
        cache.recoverAddedFailure(primary, b); driver.draw(b, "original-fallback");
        rejectsCompletion(session, state(cache, false));
        assertFalse(TerrainBenchmarkGuard.cacheActive(state(cache, false), startHits));
        assertEquals(3, driver.draws.size()); // Warmup, cached sample, single original fallback.
    }
    @Test void sampledCandidateWithRealHitsThenObservedGlErrorCannotFinishOrReportActive() throws Exception {
        Class<?> policy = FrameErrorPolicyTest.freshPolicy(() -> 0);
        TerrainVaoCacheTest.Driver driver = new TerrainVaoCacheTest.Driver(); TerrainVaoCache cache = new TerrainVaoCache(driver, 4);
        Object context = new Object(); TerrainVaoCacheTest.Buffer a = new TerrainVaoCacheTest.Buffer(5);
        cache.enter(context, true); TerrainVaoCacheTest.submit(cache, driver, a, "warmup"); cache.exit();
        BenchmarkSession session = sampling(state(cache, false)); long startHits = cache.hits;
        cache.enter(context, true); TerrainVaoCacheTest.submit(cache, driver, a, "sampled-hit"); cache.exit();
        assertFalse(session.frame(2_000_000_000L, state(cache, false)));
        assertTrue(TerrainBenchmarkGuard.cacheActive(state(cache, false), startHits));
        FrameErrorPolicyTest.error(policy, 1282);
        boolean error = (Boolean)FrameErrorPolicyTest.call(policy, "errorFallback");
        assertTrue(error); assertFalse(cache.failed()); // Error policy alone invalidates the candidate.
        rejectsCompletion(session, state(cache, error));
        assertFalse(TerrainBenchmarkGuard.cacheActive(state(cache, error), startHits));
    }
    @Test void uninterruptedOnMeasurementWithHitsCompletes() {
        TerrainBenchmarkGuard.State start = new TerrainBenchmarkGuard.State(true, true, true, false, false, 4, 0, 0, 10);
        BenchmarkSession session = sampling(start);
        TerrainBenchmarkGuard.State end = new TerrainBenchmarkGuard.State(true, true, true, false, false, 4, 0, 0, 20);
        assertTrue(session.frame(6_000_000_000L, end)); assertEquals(1, session.statistics().samples);
        assertTrue(TerrainBenchmarkGuard.cacheActive(end, start.hits));
    }
    @Test void offBaselineCompletesWithoutHooksEligibilityOrHits() {
        TerrainBenchmarkGuard.State off = new TerrainBenchmarkGuard.State(false, false, false, true, true, 4, 9, 7, 0);
        BenchmarkSession session = sampling(off);
        assertTrue(session.frame(6_000_000_000L, off)); assertEquals(1, session.statistics().samples);
        assertFalse(TerrainBenchmarkGuard.cacheActive(off, 0));
    }
    @Test void readerBridgeRejectionEvidenceAndBufferFallbackTransitionsAreLatchedEvenAfterReadmission() {
        TerrainBenchmarkGuard.State start = new TerrainBenchmarkGuard.State(true, true, true, false, false, 4, 0, 0, 10);
        TerrainBenchmarkGuard.State[] transitions = {
            new TerrainBenchmarkGuard.State(true, true, false, true, false, 4, 0, 0, 11), // Reader failure.
            new TerrainBenchmarkGuard.State(true, true, false, false, false, 4, 1, 0, 11), // Active bridge / guard rejection.
            new TerrainBenchmarkGuard.State(true, true, true, false, false, 4, 1, 0, 11), // Rejected then admitted between samples.
            new TerrainBenchmarkGuard.State(true, true, true, false, false, 4, 0, 1, 11), // Added allocation / buffer fallback.
            new TerrainBenchmarkGuard.State(true, false, true, false, false, 5, 0, 0, 11), // Evidence rejected.
            new TerrainBenchmarkGuard.State(false, true, false, false, false, 5, 0, 0, 11) // Toggle.
        };
        for (TerrainBenchmarkGuard.State transition : transitions) {
            BenchmarkSession session = sampling(start); session.frame(2_000_000_000L, start);
            rejectsCompletion(session, transition);
            rejectsCompletion(session, start); // Recovery cannot resurrect a mixed-mode result.
        }
        assertFalse(TerrainBenchmarkGuard.cacheActive(transitions[1], 10));
    }
    @Test void alreadyIneligibleOnCandidateIsRejectedAtSamplingStartWithoutRequiringHits() {
        TerrainBenchmarkGuard.State rejected = new TerrainBenchmarkGuard.State(true, true, false, false, false, 4, 0, 0, 100);
        BenchmarkSession session = new BenchmarkSession(5, 1); session.frame(0, rejected);
        assertThrows(IllegalStateException.class, () -> session.frame(1_000_000_000L, rejected));
        assertFalse(TerrainBenchmarkGuard.cacheActive(rejected, 0)); assertEquals(0, session.count());
    }
}
