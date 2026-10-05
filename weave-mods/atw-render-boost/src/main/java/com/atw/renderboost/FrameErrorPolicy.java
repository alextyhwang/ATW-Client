package com.atw.renderboost;

import java.util.function.LongSupplier;

/** Game/GL-free policy. Glyph-cache failures never affect this switch. */
public final class FrameErrorPolicy {
    public static final long POLL_INTERVAL_MS = 250;
    private static final long POLL_INTERVAL_NS = POLL_INTERVAL_MS * 1_000_000L;
    private static LongSupplier clock = System::nanoTime;
    private static volatile boolean enabled;
    private static volatile int firstError;
    private static boolean preObserved, postObserved, postPolled;
    private static long checks, skipped, postChecks, postSkipped, lastPostPoll;

    private FrameErrorPolicy() {}
    public static void setEnabled(boolean value) {
        if (enabled != value) postPolled = false;
        enabled = value;
    }
    public static boolean observed() { return preObserved && postObserved; }
    public static long checks() { return checks; }
    public static long skipped() { return skipped; }
    public static long postChecks() { return postChecks; }
    public static long postSkipped() { return postSkipped; }
    public static int firstError() { return firstError; }
    public static boolean errorFallback() { return firstError != 0; }
    /** Called only at the verified pre-render site, before its operands are pushed. */
    public static boolean skipPreRenderCheck() {
        preObserved = true;
        checks++;
        if (!enabled || errorFallback()) return false;
        skipped++;
        return true;
    }
    /** First post checks immediately; a clock regression also checks and rebases immediately. */
    public static boolean skipPostRenderCheck() {
        postObserved = true;
        postChecks++;
        if (!enabled || errorFallback()) return false;
        long now = clock.getAsLong();
        long elapsed = now - lastPostPoll;
        if (!postPolled || now < lastPostPoll || elapsed < 0 || elapsed >= POLL_INTERVAL_NS) {
            postPolled = true;
            lastPostPoll = now;
            return false;
        }
        postSkipped++;
        return true;
    }
    /** Observes a duplicate of the original diagnostic result, without consuming another GL error. */
    public static void observeError(int error) {
        if (error != 0 && firstError == 0) firstError = error;
    }
    public static String mode() {
        if (errorFallback()) return "fallback-gl-error-original-pre-and-post";
        if (!enabled) return "original-pre-and-post";
        return observed() ? "sampled-post-250ms" : "fallback-hook-not-observed";
    }
}
