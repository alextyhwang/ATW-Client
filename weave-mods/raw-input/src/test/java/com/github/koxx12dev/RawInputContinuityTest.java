package com.github.koxx12dev;

import com.github.koxx12dev.command.RescanCommand;
import com.github.koxx12dev.util.ChatUtil;
import com.github.koxx12dev.util.RawMouseHelper;
import net.java.games.input.Component;
import net.java.games.input.Mouse;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RawInputContinuityTest {
    @AfterEach void clearTransfer() {
        RawInput.rescan();
    }

    @Test void handoverChoosesOneSourceAndDiscardsOverlappingBacklogs() {
        Mouse mouse = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.poll()).thenReturn(true);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(4f); when(y.getPollData()).thenReturn(-3f);
        RawMouseHelper helper = new RawMouseHelper();
        RawInput.mouse = null;
        try (MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class);
             MockedStatic<Minecraft> game = mockStatic(Minecraft.class);
             MockedStatic<ChatUtil> chat = mockStatic(ChatUtil.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            lwjgl.when(org.lwjgl.input.Mouse::getDX).thenReturn(19, 100, 200, 250, 300, 6);
            lwjgl.when(org.lwjgl.input.Mouse::getDY).thenReturn(-7, -100, -200, -250, -300, -2);
            helper.mouseXYChange();
            assertEquals(19, helper.deltaX); assertEquals(-7, helper.deltaY);
            RawInput.mouse = mouse;
            RawInput.pollInput(null); // This interval overlaps vanilla: discard at activation.
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(null);
            helper.mouseXYChange(); // The first post-activation interval is deliberately discarded.
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(null);
            helper.mouseXYChange();
            assertEquals(4, helper.deltaX); assertEquals(3, helper.deltaY);
            RawInput.pollInput(null); // Pending raw must not survive /rescan.
            new RescanCommand().execute(new String[0]);
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            helper.mouseXYChange();
            assertEquals(6, helper.deltaX); assertEquals(-2, helper.deltaY);
            assertEquals(0L, RawInput.consumeDeltas());
            lwjgl.verify(org.lwjgl.input.Mouse::getDX, times(6));
            lwjgl.verify(org.lwjgl.input.Mouse::getDY, times(6));
            lwjgl.verifyNoMoreInteractions(); // No buttons, wheel, event queue or grab changes.
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "pollException", "axisException"})
    void failedDeviceFallsBackAndNextIterationsRecoverWithoutStaleReplay(String failure) {
        Mouse mouse = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getType()).thenReturn(Controller.Type.MOUSE);
        when(mouse.poll()).thenReturn(true);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(4f); when(y.getPollData()).thenReturn(-3f);
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        when(environment.getControllers()).thenReturn(new Controller[]{mouse});
        RawInput.mouse = mouse;
        RawMouseHelper helper = new RawMouseHelper();
        try (MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class);
             MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            helper.mouseXYChange(); // Request activation of the raw epoch.
            RawInput.pollInput(environment); // Flush before publishing stable movement.
            RawInput.pollInput(environment); // Backlog must be discarded on failure.
            clearInvocations(x, y);
            if (failure.equals("false")) when(mouse.poll()).thenReturn(false);
            if (failure.equals("pollException")) when(mouse.poll()).thenThrow(new IllegalStateException("poll failed"));
            if (failure.equals("axisException")) when(y.getPollData()).thenThrow(new IllegalStateException("axis failed"));
            assertDoesNotThrow(() -> RawInput.pollInput(environment));
            assertNull(RawInput.mouse);
            if (!failure.equals("axisException")) {
                verify(x, never()).getPollData(); verify(y, never()).getPollData();
            }
            lwjgl.when(org.lwjgl.input.Mouse::getDX).thenReturn(500, 7, 600, 700);
            lwjgl.when(org.lwjgl.input.Mouse::getDY).thenReturn(-500, -2, -600, -700);
            helper.mouseXYChange(); // Clear transition backlog; never apply stale native values.
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            helper.mouseXYChange();
            assertEquals(7, helper.deltaX); assertEquals(-2, helper.deltaY);
            doReturn(true).when(mouse).poll();
            doReturn(-3f).when(y).getPollData();
            RawInput.pollInput(environment); // Successful discovery consumes its detection sample.
            assertSame(mouse, RawInput.mouse);
            RawInput.pollInput(environment); // Not yet helper-admitted; discard overlap.
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(environment);
            helper.mouseXYChange(); // Flush cannot publish its overlapping interval.
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(environment);
            helper.mouseXYChange();
            assertEquals(4, helper.deltaX); assertEquals(3, helper.deltaY);
            assertEquals(0L, RawInput.consumeDeltas());
        }
    }

    @Test void activationDuringNativePollCannotReplayPreActivationMovement() throws Exception {
        Mouse mouse = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(4f); when(y.getPollData()).thenReturn(-3f);
        CountDownLatch polling = new CountDownLatch(1), release = new CountDownLatch(1);
        when(mouse.poll()).thenAnswer(invocation -> {
            polling.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return true;
        });
        RawInput.mouse = mouse;
        RawMouseHelper helper = new RawMouseHelper();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class);
             MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            Future<?> poll = worker.submit(() -> RawInput.pollInput(null));
            assertTrue(polling.await(5, TimeUnit.SECONDS));
            helper.mouseXYChange(); // Activation while the old native interval is in flight.
            release.countDown();
            poll.get(5, TimeUnit.SECONDS);
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(null); // The pre-activation in-flight poll cannot acknowledge the flush.
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(null); // Only the interval after the successful flush contributes.
            helper.mouseXYChange();
            assertEquals(4, helper.deltaX); assertEquals(3, helper.deltaY);
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void rescanDuringDiscoveryCannotBeOverwrittenByItsOldEnumeration() throws Exception {
        Mouse mouse = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getType()).thenReturn(Controller.Type.MOUSE);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(4f); when(y.getPollData()).thenReturn(-3f);
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        when(environment.getControllers()).thenReturn(new Controller[]{mouse});
        CountDownLatch polling = new CountDownLatch(1), release = new CountDownLatch(1);
        when(mouse.poll()).thenAnswer(invocation -> {
            polling.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return true;
        });
        RawInput.rescan();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> poll = worker.submit(() -> RawInput.pollInput(environment));
            assertTrue(polling.await(5, TimeUnit.SECONDS));
            RawInput.rescan();
            release.countDown();
            poll.get(5, TimeUnit.SECONDS);
            assertNull(RawInput.mouse);
            RawInput.pollInput(environment); // A fresh scan may select the same object.
            assertSame(mouse, RawInput.mouse);
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void sameDeviceRediscoveryBetweenHelperCallsStartsANewEpoch() {
        Mouse mouse = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getType()).thenReturn(Controller.Type.MOUSE);
        when(mouse.poll()).thenReturn(true);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(4f); when(y.getPollData()).thenReturn(-3f);
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        when(environment.getControllers()).thenReturn(new Controller[]{mouse});
        RawInput.mouse = mouse;
        RawMouseHelper helper = new RawMouseHelper();
        try (MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class);
             MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            helper.mouseXYChange();
            RawInput.pollInput(environment); // Ready the original epoch before invalidation.
            RawInput.pollInput(environment);
            RawInput.rescan();
            RawInput.pollInput(environment);
            RawInput.pollInput(environment);
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(environment);
            helper.mouseXYChange(); // Same-object rediscovery needs a new flush.
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(environment);
            helper.mouseXYChange();
            assertEquals(4, helper.deltaX); assertEquals(3, helper.deltaY);
        }
    }

    @Test void unsuccessfulDiscoveryPollCannotSelectStaleComponents() {
        Mouse mouse = mock(Mouse.class);
        when(mouse.getType()).thenReturn(Controller.Type.MOUSE);
        when(mouse.poll()).thenReturn(false);
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        when(environment.getControllers()).thenReturn(new Controller[]{mouse});
        RawInput.rescan();
        RawInput.pollInput(environment);
        assertNull(RawInput.mouse);
        verify(mouse, never()).getX(); verify(mouse, never()).getY();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void obsoletePollFailureCannotInvalidateANewEpochOfTheSameDevice(boolean exception) {
        Mouse mouse = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(4f); when(y.getPollData()).thenReturn(-3f);
        RawInput.mouse = mouse;
        RawMouseHelper helper = new RawMouseHelper();
        try (MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class);
             MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            helper.mouseXYChange();
            when(mouse.poll()).thenAnswer(invocation -> {
                RawInput.rescan();
                RawInput.mouse = mouse;
                helper.mouseXYChange(); // Activation request for a fresh epoch, still awaiting flush.
                if (exception) throw new IllegalStateException("obsolete poll failed");
                return false;
            });
            RawInput.pollInput(null);
            assertSame(mouse, RawInput.mouse);
            doReturn(true).when(mouse).poll();
            RawInput.pollInput(null); // Old failure cannot ready the replacement epoch.
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(null);
            helper.mouseXYChange();
            assertEquals(4, helper.deltaX); assertEquals(3, helper.deltaY);
        }
    }
}
