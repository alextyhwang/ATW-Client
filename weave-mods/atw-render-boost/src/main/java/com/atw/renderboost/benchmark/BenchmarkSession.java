package com.atw.renderboost.benchmark;

/** Warm-up and duration use elapsed time, never a fabricated fixed FPS. */
public final class BenchmarkSession {
    public static final int MAX_SAMPLES = 240000;
    private final long warmupNs, durationNs;
    private long started = -1, previous = -1, samplingStarted = -1;
    private final long[] samples = new long[MAX_SAMPLES];
    private int count;
    private boolean complete;

    public BenchmarkSession(int seconds, int warmupSeconds) {
        if (seconds < 5 || seconds > 240 || warmupSeconds < 1 || warmupSeconds > 120)
            throw new IllegalArgumentException("Duration 5..240s, warm-up 1..120s");
        durationNs = seconds * 1_000_000_000L;
        warmupNs = warmupSeconds * 1_000_000_000L;
    }
    public boolean frame(long now) {
        if (complete) return true;
        if (started == -1) started = now;
        if (now < started || (previous != -1 && now <= previous))
            throw new IllegalArgumentException("Clock must advance");
        if (now - started < warmupNs) { previous = now; return false; }
        if (samplingStarted == -1) {
            samplingStarted = previous = now;
            return false; // Entire interval must be after warm-up.
        }
        if (count == samples.length) throw new IllegalStateException("Sample limit reached; run a shorter benchmark");
        samples[count++] = now - previous;
        previous = now;
        complete = now - samplingStarted >= durationNs;
        return complete;
    }
    public int count() { return count; }
    public boolean sampling() { return samplingStarted != -1; }
    public boolean complete() { return complete; }
    public long[] samples() { return java.util.Arrays.copyOf(samples, count); }
    public FrameStatistics statistics() {
        if (!complete) throw new IllegalStateException("Benchmark not completed");
        return new FrameStatistics(samples, count);
    }
}
