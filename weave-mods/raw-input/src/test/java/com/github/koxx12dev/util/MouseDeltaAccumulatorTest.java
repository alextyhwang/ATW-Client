package com.github.koxx12dev.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MouseDeltaAccumulatorTest {
    private static void assertPair(long pair, int x, int y) {
        assertEquals(x, MouseDeltaAccumulator.x(pair));
        assertEquals(y, MouseDeltaAccumulator.y(pair));
    }

    @Test void emptyAndRepeatedDrainsReturnZero() {
        MouseDeltaAccumulator deltas = new MouseDeltaAccumulator();
        assertPair(deltas.drain(), 0, 0);
        deltas.add(0, 0);
        assertPair(deltas.drain(), 0, 0);
        assertPair(deltas.drain(), 0, 0);
    }

    @Test void signedAxesAccumulateIndependentlyAndDrainExactlyOnce() {
        MouseDeltaAccumulator deltas = new MouseDeltaAccumulator();
        deltas.add(19, -7);
        deltas.add(-23, 4);
        deltas.add(0, -8);
        assertPair(deltas.drain(), -4, -11);
        assertPair(deltas.drain(), 0, 0);
        deltas.add(-4, 11);
        assertPair(deltas.drain(), -4, 11);
    }

    @Test void publicationBetweenDrainsBelongsWhollyToNextDrain() throws Exception {
        MouseDeltaAccumulator deltas = new MouseDeltaAccumulator();
        deltas.add(19, -7);
        CountDownLatch firstDrain = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> publication = worker.submit(() -> {
                await(firstDrain);
                deltas.add(4, -3);
            });
            assertPair(deltas.drain(), 19, -7);
            firstDrain.countDown();
            publication.get(10, TimeUnit.SECONDS);
            assertPair(deltas.drain(), 4, -3);
            assertPair(deltas.drain(), 0, 0);
        } finally {
            firstDrain.countDown();
            worker.shutdownNow();
        }
    }

    @Test void publicationBeforeDrainIsConsumedInTheSamePair() throws Exception {
        MouseDeltaAccumulator deltas = new MouseDeltaAccumulator();
        deltas.add(19, -7);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> deltas.add(4, -3)).get(10, TimeUnit.SECONDS);
            assertPair(deltas.drain(), 23, -10);
            assertPair(deltas.drain(), 0, 0);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test void integerWrappingCannotCarryBetweenPackedAxes() {
        MouseDeltaAccumulator deltas = new MouseDeltaAccumulator();
        deltas.add(Integer.MAX_VALUE, Integer.MAX_VALUE);
        deltas.add(1, 1);
        assertPair(deltas.drain(), Integer.MIN_VALUE, Integer.MIN_VALUE);
        deltas.add(Integer.MIN_VALUE, Integer.MIN_VALUE);
        deltas.add(-1, -1);
        assertPair(deltas.drain(), Integer.MAX_VALUE, Integer.MAX_VALUE);
        deltas.add(12, -1);
        deltas.add(0, 1);
        assertPair(deltas.drain(), 12, 0);
    }

    @Test void fullSignedRangeSurvivesPackingAndLegacyYNegation() {
        MouseDeltaAccumulator deltas = new MouseDeltaAccumulator();
        deltas.add(Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertPair(deltas.drain(), Integer.MIN_VALUE, Integer.MAX_VALUE);
        deltas.add(Integer.MAX_VALUE, Integer.MIN_VALUE);
        long pair = deltas.drain();
        assertPair(pair, Integer.MAX_VALUE, Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, -MouseDeltaAccumulator.y(pair));
    }

    @Test void concurrentProducerAndConsumerConserveEverySignedSampleAsAPair() throws Exception {
        MouseDeltaAccumulator deltas = new MouseDeltaAccumulator();
        AtomicBoolean finished = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        final int samples = 100_000;
        long expectedX = 0;
        for (int i = 0; i < samples; i++) expectedX += i % 7 - 2;
        try {
            Future<?> producer = workers.submit(() -> {
                await(start);
                try {
                    for (int i = 0; i < samples; i++) {
                        int x = i % 7 - 2;
                        deltas.add(x, -3 * x);
                    }
                } finally {
                    finished.set(true);
                }
            });
            Future<long[]> consumer = workers.submit(() -> {
                await(start);
                long[] total = new long[2];
                while (!finished.get()) {
                    collectPaired(deltas.drain(), total);
                }
                collectPaired(deltas.drain(), total);
                return total;
            });
            start.countDown();
            producer.get(10, TimeUnit.SECONDS);
            long[] total = consumer.get(10, TimeUnit.SECONDS);
            assertEquals(expectedX, total[0]);
            assertEquals(-3 * expectedX, total[1]);
            assertPair(deltas.drain(), 0, 0);
        } finally {
            finished.set(true);
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test void competingPublicationsAndDrainsConserveIndependentAxes() throws Exception {
        MouseDeltaAccumulator deltas = new MouseDeltaAccumulator();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(3);
        final int samples = 50_000;
        try {
            Future<?> first = workers.submit(() -> {
                await(start);
                for (int i = 0; i < samples; i++) deltas.add(-2, 5);
            });
            Future<?> second = workers.submit(() -> {
                await(start);
                for (int i = 0; i < samples; i++) deltas.add(7, -3);
            });
            Future<long[]> consumer = workers.submit(() -> {
                await(start);
                long[] total = new long[2];
                for (int i = 0; i < samples; i++) {
                    long pair = deltas.drain();
                    total[0] += MouseDeltaAccumulator.x(pair);
                    total[1] += MouseDeltaAccumulator.y(pair);
                }
                return total;
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
            long[] total = consumer.get(10, TimeUnit.SECONDS);
            long pending = deltas.drain();
            assertEquals(5L * samples, total[0] + MouseDeltaAccumulator.x(pending));
            assertEquals(2L * samples, total[1] + MouseDeltaAccumulator.y(pending));
            assertPair(deltas.drain(), 0, 0);
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private static void collectPaired(long pair, long[] total) {
        int x = MouseDeltaAccumulator.x(pair), y = MouseDeltaAccumulator.y(pair);
        assertEquals(-3 * x, y, "a single native sample must not straddle helper drains");
        total[0] += x;
        total[1] += y;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("worker handoff timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
