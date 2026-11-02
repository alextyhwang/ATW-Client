package com.github.koxx12dev;

import com.github.koxx12dev.util.RawMouseHelper;
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Mouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RawInputFlushBarrierTest {
    @AfterEach void reset() { RawInput.rescan(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedFlushInvalidatesAndRediscoveryNeedsAnotherSuccessfulFlush(boolean exception) {
        Mouse device = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(device.getType()).thenReturn(Controller.Type.MOUSE);
        when(device.getX()).thenReturn(x); when(device.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(4f); when(y.getPollData()).thenReturn(-3f);
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        when(environment.getControllers()).thenReturn(new Controller[]{device});
        RawInput.rescan();
        RawInput.mouse = device;
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class);
             MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawMouseHelper helper = new RawMouseHelper();
            helper.mouseXYChange();
            if (exception) when(device.poll()).thenThrow(new IllegalStateException("flush poll failed"));
            else when(device.poll()).thenReturn(false);
            assertDoesNotThrow(() -> RawInput.pollInput(environment));
            assertNull(RawInput.mouse);
            verify(device, never()).getX(); verify(device, never()).getY();
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            doReturn(true).when(device).poll();
            RawInput.pollInput(environment); // Rediscovery consumes its detection sample.
            assertSame(device, RawInput.mouse);
            helper.mouseXYChange();
            RawInput.pollInput(environment); // Successful flush for the new epoch.
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(environment);
            helper.mouseXYChange();
            assertEquals(4, helper.deltaX); assertEquals(3, helper.deltaY);
        }
    }

    @Test void successfulFlushInMenuDiscardsIntervalButAllowsFreshMovementAfterExit() {
        Mouse device = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(device.poll()).thenReturn(true);
        when(device.getX()).thenReturn(x); when(device.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(-5f); when(y.getPollData()).thenReturn(2f);
        Minecraft minecraft = mock(Minecraft.class);
        RawInput.rescan();
        RawInput.mouse = device;
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class);
             MockedStatic<org.lwjgl.input.Mouse> lwjgl = mockStatic(org.lwjgl.input.Mouse.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(minecraft);
            RawMouseHelper helper = new RawMouseHelper();
            helper.mouseXYChange();
            minecraft.currentScreen = mock(GuiScreen.class);
            RawInput.pollInput(null); // Native interval is flushed even in a menu.
            verify(device, never()).getX(); verify(device, never()).getY();
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            RawInput.pollInput(null); // Suppress menu movement after readiness too.
            minecraft.currentScreen = null; // No tick callback before the resumed poll.
            RawInput.pollInput(null);
            helper.mouseXYChange();
            assertEquals(-5, helper.deltaX); assertEquals(-2, helper.deltaY);
        }
    }
}
