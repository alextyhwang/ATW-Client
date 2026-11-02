package com.github.koxx12dev;

import com.github.koxx12dev.util.RawMouseHelper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Mouse;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeIntervalReviewTest {
    @AfterEach void reset() { RawInput.rescan(); }

    @Test void firstPollStartedAfterActivationMustNotReplayMovementSinceLastNativePoll() throws Exception {
        Mouse device = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        when(environment.getControllers()).thenReturn(new Controller[]{device});
        when(device.getType()).thenReturn(Controller.Type.MOUSE);
        when(device.getX()).thenReturn(x); when(device.getY()).thenReturn(y);
        // Mock only external APIs: independent read-and-clear native and LWJGL
        // intervals, as opposed to injecting or editing production deltas.
        AtomicInteger nativePending = new AtomicInteger(), lwjglPending = new AtomicInteger();
        AtomicInteger sampledX = new AtomicInteger(), pollCount = new AtomicInteger();
        doAnswer(invocation -> {
            pollCount.incrementAndGet();
            sampledX.set(nativePending.getAndSet(0));
            return true;
        }).when(device).poll();
        when(x.getPollData()).thenAnswer(invocation -> (float) sampledX.get());
        CountDownLatch discoveryRead = new CountDownLatch(1), release = new CountDownLatch(1);
        when(y.getPollData()).thenAnswer(invocation -> {
            if (pollCount.get() == 1) {
                discoveryRead.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return 0f;
        });
        RawInput.rescan();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class);
             MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            lwjgl.when(org.lwjgl.input.Mouse::getDX).thenAnswer(invocation -> lwjglPending.getAndSet(0));
            RawMouseHelper helper = new RawMouseHelper();
            helper.mouseXYChange(); assertEquals(0, helper.deltaX);
            nativePending.addAndGet(1); lwjglPending.addAndGet(1); // Movement selecting the device.
            Future<?> scan = worker.submit(() -> RawInput.pollInput(environment));
            assertTrue(discoveryRead.await(5, TimeUnit.SECONDS)); // Native detection poll already drained.
            nativePending.addAndGet(19); lwjglPending.addAndGet(19); // Movement after that poll.
            helper.mouseXYChange(); // Source has not been published: vanilla owns this call.
            assertEquals(20, helper.deltaX);
            release.countDown(); scan.get(5, TimeUnit.SECONDS);
            assertSame(device, RawInput.mouse);
            helper.mouseXYChange(); // Explicit transition-discard/admission call, no new motion.
            assertEquals(0, helper.deltaX);
            worker.submit(() -> RawInput.pollInput(environment)).get(5, TimeUnit.SECONDS);
            // This flush poll starts after activation, but its native sample
            // covers the earlier interval already consumed through vanilla.
            helper.mouseXYChange();
            System.out.printf("raw after vanilla (20,0) and transition (0,0): (%d,%d), expected (0,0)%n",
                    helper.deltaX, helper.deltaY);
            assertEquals(0, helper.deltaX, "first post-activation native interval must not replay vanilla-consumed input");
            assertEquals(0, helper.deltaY);
            nativePending.addAndGet(-6); lwjglPending.addAndGet(-6);
            worker.submit(() -> {
                // Mockito static mocks are thread-local. Retaining the baseline
                // worker GUI read requires its real external seam on this owner.
                try (MockedStatic<Minecraft> workerGame = mockStatic(Minecraft.class)) {
                    workerGame.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
                    RawInput.pollInput(environment);
                }
            }).get(5, TimeUnit.SECONDS);
            helper.mouseXYChange();
            assertEquals(-6, helper.deltaX, "the interval after the flush must publish exactly once");
            assertEquals(0, helper.deltaY);
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX);
        } finally {
            release.countDown(); worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
