package com.atw.renderboost;

import java.io.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FrameErrorPolicyTest {
    /** A separate session per test, with a deterministic clock and no production reset API. */
    public static Class<?> freshPolicy(LongSupplier clock) throws Exception {
        byte[] bytes;
        try (InputStream input = FrameErrorPolicy.class.getResourceAsStream("FrameErrorPolicy.class")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[2048]; int read;
            while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
            bytes = out.toByteArray();
        }
        Class<?> policy = new ClassLoader(null) {
            Class<?> define() { return defineClass(FrameErrorPolicy.class.getName(), bytes, 0, bytes.length); }
        }.define();
        java.lang.reflect.Field field = policy.getDeclaredField("clock");
        field.setAccessible(true); field.set(null, clock);
        return policy;
    }
    public static Object call(Class<?> policy, String method) throws Exception {
        return policy.getMethod(method).invoke(null);
    }
    public static void enable(Class<?> policy, boolean value) throws Exception {
        policy.getMethod("setEnabled", boolean.class).invoke(null, value);
    }
    public static void error(Class<?> policy, int value) throws Exception {
        policy.getMethod("observeError", int.class).invoke(null, value);
    }
    @Test void defaultOffAlwaysRunsBothOriginalChecksWithoutReadingClock() throws Exception {
        Class<?> p = freshPolicy(() -> { throw new AssertionError("OFF must not read clock"); });
        assertEquals("original-pre-and-post", call(p, "mode"));
        assertEquals(false, call(p, "observed"));
        for (int i = 0; i < 3; i++) {
            assertEquals(false, call(p, "skipPreRenderCheck"));
            assertEquals(false, call(p, "skipPostRenderCheck"));
        }
        assertEquals(true, call(p, "observed"));
        assertEquals(3L, call(p, "checks")); assertEquals(3L, call(p, "postChecks"));
        assertEquals(0L, call(p, "skipped")); assertEquals(0L, call(p, "postSkipped"));
    }
    @Test void onSamplesFirstPostImmediatelyThenAt250msAndCountsActualSkips() throws Exception {
        AtomicLong clock = new AtomicLong(0); Class<?> p = freshPolicy(clock::get);
        enable(p, true);
        assertEquals("fallback-hook-not-observed", call(p, "mode"));
        assertEquals(true, call(p, "skipPreRenderCheck"));
        assertEquals(false, call(p, "skipPostRenderCheck"));
        assertEquals("sampled-post-250ms", call(p, "mode"));
        clock.set(249_999_999); assertEquals(true, call(p, "skipPostRenderCheck"));
        clock.set(250_000_000); assertEquals(false, call(p, "skipPostRenderCheck"));
        enable(p, true); // Repeated ON cannot reset the cadence.
        clock.set(499_999_999); assertEquals(true, call(p, "skipPostRenderCheck"));
        clock.set(500_000_000); assertEquals(false, call(p, "skipPostRenderCheck"));
        clock.set(5_000_000_000L); assertEquals(false, call(p, "skipPostRenderCheck"));
        assertEquals(1L, call(p, "skipped")); assertEquals(2L, call(p, "postSkipped"));
        assertEquals(6L, call(p, "postChecks"));
    }
    @Test void offRestoresBothImmediatelyAndReenableStartsWithImmediatePost() throws Exception {
        Class<?> p = freshPolicy(() -> 100L); enable(p, true);
        assertEquals(true, call(p, "skipPreRenderCheck"));
        assertEquals(false, call(p, "skipPostRenderCheck"));
        assertEquals(true, call(p, "skipPostRenderCheck"));
        enable(p, false);
        assertEquals(false, call(p, "skipPreRenderCheck"));
        assertEquals(false, call(p, "skipPostRenderCheck"));
        assertEquals("original-pre-and-post", call(p, "mode"));
        enable(p, true); assertEquals(false, call(p, "skipPostRenderCheck"));
    }
    @Test void anyNonzeroErrorLocksBothOriginalChecksForSessionIncludingAcrossOffOn() throws Exception {
        Class<?> p = freshPolicy(() -> 100L); enable(p, true);
        call(p, "skipPreRenderCheck"); call(p, "skipPostRenderCheck");
        error(p, 0); assertEquals(false, call(p, "errorFallback"));
        error(p, 1282); error(p, 1280); error(p, 0);
        assertEquals(1282, call(p, "firstError"));
        assertEquals(true, call(p, "errorFallback"));
        assertEquals("fallback-gl-error-original-pre-and-post", call(p, "mode"));
        for (boolean requested : new boolean[]{true, false, true}) {
            enable(p, requested);
            assertEquals(false, call(p, "skipPreRenderCheck"));
            assertEquals(false, call(p, "skipPostRenderCheck"));
        }
        Class<?> restarted = freshPolicy(() -> 100L);
        assertEquals(false, call(restarted, "errorFallback"));
        assertEquals("original-pre-and-post", call(restarted, "mode"));
    }
    @Test void startupOrOffErrorAlsoLocksLaterSamplingRequests() throws Exception {
        Class<?> p = freshPolicy(() -> { throw new AssertionError("Fallback must not read clock"); });
        error(p, 1281); enable(p, true);
        assertEquals(false, call(p, "skipPreRenderCheck"));
        assertEquals(false, call(p, "skipPostRenderCheck"));
        assertEquals("fallback-gl-error-original-pre-and-post", call(p, "mode"));
    }
    @Test void backwardClockChecksImmediatelyAndRebasesRatherThanExtendingBlindPeriod() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000_000L); Class<?> p = freshPolicy(clock::get); enable(p, true);
        assertEquals(false, call(p, "skipPostRenderCheck"));
        clock.set(1_100_000_000L); assertEquals(true, call(p, "skipPostRenderCheck"));
        clock.set(10); assertEquals(false, call(p, "skipPostRenderCheck"));
        clock.set(250_000_009L); assertEquals(true, call(p, "skipPostRenderCheck"));
        clock.set(250_000_010L); assertEquals(false, call(p, "skipPostRenderCheck"));
        clock.set(Long.MIN_VALUE); assertEquals(false, call(p, "skipPostRenderCheck"));
        clock.set(Long.MAX_VALUE); assertEquals(false, call(p, "skipPostRenderCheck"));
    }
}
