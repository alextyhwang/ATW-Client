package com.github.koxx12dev.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Poll-thread publications and game-thread drains share one atomic pair.
 * Each axis retains Java int wrapping; packing must not carry between axes.
 */
public final class MouseDeltaAccumulator {
    private final AtomicLong pending = new AtomicLong();

    public void add(int dx, int dy) {
        long previous;
        long next;
        do {
            previous = pending.get();
            next = pack(x(previous) + dx, y(previous) + dy);
        } while (!pending.compareAndSet(previous, next));
        // The successful CAS publishes both axes, including on a drain retry.
    }

    /** The exchange is the only reset: publications fall wholly before or after it. */
    public long drain() {
        return pending.getAndSet(0L);
    }

    public static int x(long pair) {
        return (int) (pair >>> 32);
    }

    public static int y(long pair) {
        return (int) pair;
    }

    private static long pack(int dx, int dy) {
        return ((long) dx << 32) | (dy & 0xffffffffL);
    }
}
