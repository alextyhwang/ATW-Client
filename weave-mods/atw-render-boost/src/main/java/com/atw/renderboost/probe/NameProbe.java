package com.atw.renderboost.probe;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Properties;

/** Fixed-size, render-thread collector. No game objects, keys, produced values or payloads. */
public final class NameProbe {
    public static final long DURATION_NS = 10_000_000_000L;
    public static final int LIMIT = 64;
    public static final String[] METRICS = {"outerName", "displayComponent", "displayName", "hoverEvent",
            "componentWidth", "componentAdventure", "styleAdventure", "livingLabel", "sneakingLabel", "drawLabel"};
    private static final String[] BUCKETS = {"player", "armorStand", "otherLiving"};
    public interface Clock {
        long wall(); long cpu();
        default String cpuSource() { return "injected test clock"; }
    }
    public static Clock systemClock() {
        // Never enable a VM facility that was disabled. Probe failure cannot escape into game code.
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        boolean supported;
        try { supported = bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled(); }
        catch (RuntimeException | LinkageError e) { supported = false; }
        final boolean available = supported;
        return new Clock() {
            public long wall() { return System.nanoTime(); }
            public String cpuSource() { return available ? "ThreadMXBean current-thread CPU; per-duration valid sample counts qualify totals"
                    : "UNAVAILABLE: unsupported or disabled; wall fallback"; }
            public long cpu() {
                if (!available) return -1;
                try { return bean.getCurrentThreadCpuTime(); }
                catch (RuntimeException | LinkageError e) { return -1; }
            }
        };
    }
    private final Clock clock;
    private final long[][] calls = new long[3][METRICS.length], samples = new long[3][METRICS.length],
            walls = new long[3][METRICS.length], cpus = new long[3][METRICS.length], cpuSamples = new long[3][METRICS.length],
            exceptions = new long[3][METRICS.length];
    private final int[] metricStack = new int[LIMIT];
    // Timing is committed only at a completed frame, so outer-name/frame ratios share a window.
    private final long[][] pendingWalls = new long[3][METRICS.length], pendingCpus = new long[3][METRICS.length],
            pendingSamples = new long[3][METRICS.length], pendingCpuSamples = new long[3][METRICS.length];
    private final long[] wallStack = new long[LIMIT], cpuStack = new long[LIMIT];
    private boolean active, timing, sampledFrame, frameOpen;
    private int depth, nameDepth, bucket, overflow;
    private long start, stopped, frames, completedFrames, sampledFrames, completedSampledFrames, abandonedFrames, dropped, abandoned, frameWall, frameCpu,
            frameCpuSamples, sampledFrameWall, totalWorkWall, intervalWall, intervals, previousFrame, frameStart;
    private String reason = "never started";
    private Properties completed;
    public NameProbe(Clock clock) { this.clock = clock; }
    public void start(boolean timing) {
        if (active) throw new IllegalStateException("Name probe already collecting");
        for (long[][] array : new long[][][] {calls,samples,walls,cpus,cpuSamples,exceptions})
            for (long[] row : array) java.util.Arrays.fill(row, 0);
        depth = nameDepth = overflow = 0;
        frames = completedFrames = sampledFrames = completedSampledFrames = abandonedFrames = dropped = abandoned = frameWall = frameCpu = frameCpuSamples = 0;
        sampledCpuTotal = sampledFrameWall = totalWorkWall = intervalWall = intervals = previousFrame = frameStart = 0;
        this.timing = timing; sampledFrame = frameOpen = false; completed = null;
        start = clock.wall(); stopped = 0; reason = "collecting"; active = true;
    }
    public boolean active() { expire(clock.wall()); return active; }
    public boolean running() { return active; }
    private boolean expire(long now) {
        if (active && now - start >= DURATION_NS) stop(now, "10-second limit");
        return !active;
    }
    public void frame(long now) {
        if (expire(now)) return;
        if (depth != 0 || overflow != 0) { stop(now, "unbalanced scope at frame boundary"); return; }
        if (frames > 0) { intervalWall += now - previousFrame; intervals++; }
        previousFrame = now; frameStart = now;
        frameOpen = true;
        sampledFrame = timing && (frames & 31) == 0;
        frames++;
        if (sampledFrame) {
            clear(pendingWalls); clear(pendingCpus); clear(pendingSamples); clear(pendingCpuSamples);
            sampledFrames++; frameWall = clock.wall(); frameCpu = clock.cpu();
        }
    }
    public void frameEnd(long now) {
        if (expire(now)) return;
        if (depth != 0 || overflow != 0) { stop(now, "unbalanced scope at frame exit"); return; }
        if (!frameOpen) return;
        frameOpen = false; completedFrames++;
        totalWorkWall += now - frameStart;
        if (sampledFrame) {
            completedSampledFrames++;
            sampledFrameWall += Math.max(0, clock.wall() - frameWall);
            long cpu = clock.cpu();
            if (cpu >= frameCpu && frameCpu >= 0) { frameCpuSamples++; frameCpu = cpu - frameCpu; }
            else frameCpu = -1;
            // Store total separately from the current-frame start value.
            if (frameCpu >= 0) sampledCpuTotal += frameCpu;
            for (int b=0;b<3;b++) for (int m=0;m<METRICS.length;m++) {
                walls[b][m] += pendingWalls[b][m]; cpus[b][m] += pendingCpus[b][m];
                samples[b][m] += pendingSamples[b][m]; cpuSamples[b][m] += pendingCpuSamples[b][m];
            }
        }
    }
    private long sampledCpuTotal;
    private static void clear(long[][] array) { for (long[] row : array) java.util.Arrays.fill(row,0); }
    /** Token pairs an accepted entry with precisely one exit; no ThreadLocal allocation. */
    public int enter(int metric, int kind) {
        if (!active || !frameOpen) return 0;
        if (metric != 0 && nameDepth == 0) return 0;
        if (expire(clock.wall())) return 0;
        if (depth == LIMIT || overflow != 0) { overflow++; dropped++; return -1; }
        if (metric == 0) {
            if (nameDepth++ == 0) bucket = kind >= 0 && kind < 3 ? kind : 2;
            else metric = -1; // overload/bridge forwarding is one outer name scope.
        }
        int index = depth++;
        metricStack[index] = metric;
        if (metric >= 0) {
            calls[bucket][metric]++;
            if (sampledFrame) { wallStack[index] = clock.wall(); cpuStack[index] = clock.cpu(); }
        }
        return index + 1;
    }
    public void exit(int token, boolean exceptional) {
        if (!active || token == 0) return;
        long now = clock.wall();
        if (expire(now)) return; // Crossing-boundary in-flight durations are omitted and marked abandoned.
        if (token == -1) { if (overflow > 0) overflow--; return; }
        if (token != depth || depth == 0) { stop(now, "unbalanced token"); return; }
        int index = --depth, metric = metricStack[index];
        if (metric >= 0) {
            if (exceptional) exceptions[bucket][metric]++;
            if (sampledFrame) {
                long cpu = clock.cpu();
                pendingWalls[bucket][metric] += Math.max(0, now - wallStack[index]); pendingSamples[bucket][metric]++;
                if (cpu >= cpuStack[index] && cpuStack[index] >= 0) {
                    pendingCpus[bucket][metric] += cpu - cpuStack[index]; pendingCpuSamples[bucket][metric]++;
                }
            }
        }
        if (metric == 0 || metric == -1) nameDepth--;
    }
    public void stop() { if (active) stop(clock.wall(), "manual stop"); }
    private void stop(long now, String reason) {
        active = false; stopped = now; this.reason = reason;
        abandoned += depth + overflow; depth = nameDepth = overflow = 0;
        if (frameOpen) abandonedFrames++; frameOpen=false;
        completed = snapshot();
    }
    public Properties takeCompleted() { Properties p = completed; completed = null; return p; }
    public Properties snapshot() {
        Properties p = new Properties();
        p.setProperty("mode", timing ? "timing" : "counters");
        p.setProperty("cpuSource", clock.cpuSource());
        p.setProperty("active", String.valueOf(active)); p.setProperty("stopReason", reason);
        p.setProperty("durationLimitNs", String.valueOf(DURATION_NS));
        p.setProperty("elapsedNs", String.valueOf(active ? clock.wall() - start : stopped - start));
        p.setProperty("frames", String.valueOf(frames)); p.setProperty("sampledFrames", String.valueOf(sampledFrames));
        p.setProperty("completedFrames", String.valueOf(completedFrames));
        p.setProperty("completedSampledFrames", String.valueOf(completedSampledFrames));
        p.setProperty("abandonedFrames", String.valueOf(abandonedFrames));
        p.setProperty("frameIntervals", String.valueOf(intervals)); p.setProperty("frameIntervalWallNs", String.valueOf(intervalWall));
        p.setProperty("frameWorkWallNs", String.valueOf(totalWorkWall));
        p.setProperty("sampledFrameWallNs", String.valueOf(sampledFrameWall));
        p.setProperty("sampledFrameCpuNs", String.valueOf(sampledCpuTotal));
        p.setProperty("sampledFrameCpuSamples", String.valueOf(frameCpuSamples));
        p.setProperty("overflowEntries", String.valueOf(dropped)); p.setProperty("abandonedEntries", String.valueOf(abandoned));
        p.setProperty("timingSemantics", "inclusive nested; overlapping metrics must not be added; unscaled 1-in-32 frame samples; CPU only when available");
        p.setProperty("redundancy", "unresolved: no semantic equality or produced-value cache");
        for (int b=0;b<3;b++) for (int m=0;m<METRICS.length;m++) {
            String key = BUCKETS[b] + "." + METRICS[m] + ".";
            p.setProperty(key+"calls", String.valueOf(calls[b][m]));
            p.setProperty(key+"timedCalls", String.valueOf(samples[b][m]));
            p.setProperty(key+"wallNs", String.valueOf(walls[b][m]));
            p.setProperty(key+"cpuNs", String.valueOf(cpus[b][m]));
            p.setProperty(key+"cpuTimedCalls", String.valueOf(cpuSamples[b][m]));
            p.setProperty(key+"exceptionalCalls", String.valueOf(exceptions[b][m]));
        }
        return p;
    }
    public int depth() { return depth; }
}
