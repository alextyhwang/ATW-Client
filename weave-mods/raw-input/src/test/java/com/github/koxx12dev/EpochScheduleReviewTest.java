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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EpochScheduleReviewTest {
    @AfterEach void reset() { RawInput.rescan(); }

    private Mouse mouse(float dx, float dy) {
        Mouse device = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(device.getType()).thenReturn(Controller.Type.MOUSE);
        when(device.poll()).thenReturn(true);
        when(device.getX()).thenReturn(x); when(device.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(dx); when(y.getPollData()).thenReturn(dy);
        return device;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateSuccessfulPollCannotPublishToRediscoveredSameObjectEpoch(boolean readyBeforePoll) throws Exception {
        Mouse device = mouse(8f, -5f);
        RawInput.mouse = device;
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class);
             MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            RawMouseHelper helper = new RawMouseHelper(); helper.mouseXYChange();
            if (readyBeforePoll) RawInput.pollInput(null); // Also retain the original stable-poll schedule.
            doAnswer(invocation -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return true; })
                    .when(device).poll();
            Future<?> oldPoll = worker.submit(() -> {
                try (MockedStatic<Minecraft> workerGame = mockStatic(Minecraft.class)) {
                    workerGame.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
                    RawInput.pollInput(null);
                }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            RawInput.rescan();
            RawInput.mouse = device;
            helper.mouseXYChange(); // New token, even though the Mouse identity matches.
            release.countDown(); oldPoll.get(5, TimeUnit.SECONDS);
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            doReturn(true).when(device).poll();
            RawInput.pollInput(null); helper.mouseXYChange(); // New epoch still requires its own flush.
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(null); helper.mouseXYChange();
            assertEquals(8, helper.deltaX); assertEquals(5, helper.deltaY);
        } finally {
            release.countDown(); worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void enumerationMayAdmitFirstMouseThenReplaceItWithoutReplayingEitherDetectionSample() {
        Mouse first = mouse(3f, -2f), last = mouse(11f, -7f);
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        when(environment.getControllers()).thenReturn(new Controller[]{first, last});
        RawInput.rescan();
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class);
             MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            RawMouseHelper helper = new RawMouseHelper();
            doAnswer(invocation -> {
                assertSame(first, RawInput.mouse);
                helper.mouseXYChange(); // Between first and last selections; no second native poll owner.
                assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
                return true;
            }).when(last).poll();
            RawInput.pollInput(environment);
            assertSame(last, RawInput.mouse); // Existing last-moving-mouse policy.
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            doReturn(true).when(last).poll();
            RawInput.pollInput(null); helper.mouseXYChange(); // Replacement epoch's flush is discarded.
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(null); helper.mouseXYChange();
            assertEquals(11, helper.deltaX); assertEquals(7, helper.deltaY);
        }
    }

    @Test void stableAdmittedEpochConservesPairsWithOnePollOwnerAndOneRealHelper() throws Exception {
        Mouse device = mouse(0f, 0f);
        AtomicInteger sample = new AtomicInteger();
        when(device.getX().getPollData()).thenAnswer(invocation -> sample.get() % 13 - 6f);
        when(device.getY().getPollData()).thenAnswer(invocation -> sample.get() % 9 - 4f);
        RawInput.mouse = device;
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class);
             MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            lwjgl.when(org.lwjgl.input.Mouse::getDX).thenReturn(999);
            lwjgl.when(org.lwjgl.input.Mouse::getDY).thenReturn(-999);
            RawMouseHelper helper = new RawMouseHelper(); helper.mouseXYChange();
            RawInput.pollInput(null); // Flush a zero interval before counting stable samples.
            doAnswer(invocation -> { sample.incrementAndGet(); return true; }).when(device).poll();
            long expectedX = 0, expectedY = 0;
            for (int i = 1; i <= 1000; i++) { expectedX += i % 13 - 6; expectedY -= i % 9 - 4; }
            Future<?> polls = worker.submit(() -> {
                // The retained worker GUI read uses a thread-local external mock.
                try (MockedStatic<Minecraft> workerGame = mockStatic(Minecraft.class)) {
                    workerGame.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
                    for (int i = 0; i < 1000; i++) RawInput.pollInput(null);
                }
            });
            long actualX = 0, actualY = 0;
            for (int i = 0; i < 100; i++) { helper.mouseXYChange(); actualX += helper.deltaX; actualY += helper.deltaY; }
            polls.get(5, TimeUnit.SECONDS);
            helper.mouseXYChange(); actualX += helper.deltaX; actualY += helper.deltaY;
            assertEquals(expectedX, actualX); assertEquals(expectedY, actualY);
            helper.mouseXYChange(); assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
        } finally {
            worker.shutdownNow(); assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
