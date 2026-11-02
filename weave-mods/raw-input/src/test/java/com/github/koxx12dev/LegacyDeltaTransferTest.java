package com.github.koxx12dev;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Faithful scalar operations from the old += / read / reset handoff. */
class LegacyDeltaTransferTest {
    private static final class Legacy {
        int dx;
        int dy;
    }

    @Test void producerIncrementBetweenConsumerReadAndResetIsLost() {
        Legacy legacy = new Legacy();
        legacy.dx = 19;
        legacy.dy = -7;
        int consumedX = legacy.dx;
        legacy.dx += 4; // Poll thread runs after the helper reads X.
        legacy.dy += -3;
        legacy.dx = 0; // Helper overwrites the newly published X.
        int consumedY = -legacy.dy;
        legacy.dy = 0;
        assertEquals(19, consumedX);
        assertEquals(10, consumedY);
        assertEquals(0, legacy.dx);
        assertNotEquals(23, consumedX + legacy.dx, "four produced X units disappeared");
    }

    @Test void producerReadModifyWriteCanReplayAlreadyConsumedDelta() {
        Legacy legacy = new Legacy();
        legacy.dx = 19;
        int producerRead = legacy.dx; // First operation of += 4.
        int consumedX = legacy.dx;
        legacy.dx = 0;
        legacy.dx = producerRead + 4; // Last operation of += 4.
        int nextConsumedX = legacy.dx;
        legacy.dx = 0;
        assertEquals(42, consumedX + nextConsumedX);
        assertNotEquals(23, consumedX + nextConsumedX, "nineteen units were consumed twice");
    }

    @Test void independentAtomicAxesConserveTotalsButCanSplitOnePollSample() {
        AtomicInteger x = new AtomicInteger(), y = new AtomicInteger();
        int frameX = x.getAndSet(0);
        x.addAndGet(4); // A poll falls between two per-axis consumer exchanges.
        y.addAndGet(-3);
        int frameY = y.getAndSet(0);
        assertEquals(0, frameX);
        assertEquals(-3, frameY);
        assertEquals(4, x.getAndSet(0));
        assertEquals(0, y.getAndSet(0));
    }
}
