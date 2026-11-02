package com.github.koxx12dev;

import com.github.koxx12dev.command.RescanCommand;
import com.github.koxx12dev.util.ChatUtil;
import com.github.koxx12dev.util.RawMouseHelper;
import com.github.koxx12dev.util.MouseDeltaAccumulator;
import com.github.koxx12dev.util.WeaveUtil;
import net.java.games.input.Mouse;
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.EnumChatFormatting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RawInputTest {
    private MockedStatic<org.lwjgl.input.Mouse> lwjgl;

    @BeforeEach void prepare() {
        RawInput.rescan();
        lwjgl = mockStatic(org.lwjgl.input.Mouse.class);
    }

    @AfterEach void clearTransfer() {
        RawInput.rescan();
        lwjgl.close();
    }

    @Test void discoversMovingMouseAndRescanReentersDiscovery() {
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        Mouse mouse = mock(Mouse.class);
        when(mouse.poll()).thenReturn(true);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getType()).thenReturn(Controller.Type.MOUSE);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(environment.getControllers()).thenReturn(new Controller[]{mouse});
        RawInput.mouse = null;
        Minecraft minecraft = mock(Minecraft.class);
        minecraft.thePlayer = mock(EntityPlayerSP.class);
        try (MockedStatic<ChatUtil> chat = mockStatic(ChatUtil.class);
             MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(minecraft);
            when(x.getPollData()).thenReturn(0.1f);
            RawInput.pollInput(environment);
            assertNull(RawInput.mouse, "original epsilon boundary should not select a stationary mouse");
            when(x.getPollData()).thenReturn(0.2f);
            RawInput.pollInput(environment);
            assertSame(mouse, RawInput.mouse);
            chat.verifyNoInteractions();
            RawInput.onClientTick(null);
            chat.verify(() -> ChatUtil.addMessage(EnumChatFormatting.GREEN, "[RawInput] Found mouse"));
            new RescanCommand().execute(new String[]{"rescan"});
            assertNull(RawInput.mouse);
            RawInput.pollInput(environment);
            assertSame(mouse, RawInput.mouse);
        } finally {
            RawInput.mouse = null;
        }
    }

    @Test void pollsWorkerDeltasOnlyWhenNoMenuIsOpen() {
        Mouse mouse = mock(Mouse.class);
        when(mouse.poll()).thenReturn(true);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(4.9f); when(y.getPollData()).thenReturn(-3.9f);
        Minecraft minecraft = mock(Minecraft.class);
        RawInput.mouse = mouse;
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(minecraft);
            RawInput.onClientTick(null);
            RawInput.activateSource();
            RawInput.pollInput(null); // Successful post-activation flush is discarded.
            RawInput.pollInput(null); // Retain a fresh pre-menu sample.
            minecraft.currentScreen = mock(GuiScreen.class);
            RawInput.onClientTick(null);
            RawInput.pollInput(null);
            long pair = RawInput.consumeDeltas();
            assertEquals(4, MouseDeltaAccumulator.x(pair));
            assertEquals(-3, MouseDeltaAccumulator.y(pair));
            verify(mouse, times(3)).poll();
        } finally {
            RawInput.rescan();
        }
    }

    @Test void mouseHelperConsumesDeltasAndPreservesYAxisSign() {
        Mouse mouse = mock(Mouse.class);
        when(mouse.poll()).thenReturn(true);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(19f); when(y.getPollData()).thenReturn(-7f);
        RawInput.mouse = mouse;
        RawMouseHelper helper = new RawMouseHelper();
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            helper.mouseXYChange();
            RawInput.pollInput(null); // Successful post-activation flush is discarded.
            RawInput.pollInput(null);
        }
        helper.mouseXYChange();
        assertEquals(19, helper.deltaX);
        assertEquals(7, helper.deltaY);
        helper.mouseXYChange();
        assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
    }

    @Test void rescanKeepsOriginalMouseResetAndFeedback() {
        RawInput.mouse = mock(Mouse.class);
        try (MockedStatic<ChatUtil> chat = mockStatic(ChatUtil.class)) {
            RescanCommand command = new RescanCommand();
            assertTrue(command.matches("rescan"));
            command.execute(new String[]{"rescan"});
            assertNull(RawInput.mouse);
            chat.verify(() -> ChatUtil.addMessage(EnumChatFormatting.GOLD, "[RawInput] Rescanning input devices..."));
        }
    }

    @Test void dormantPlatformModFolderPathMatchesInstalledOriginal() {
        assertEquals(java.nio.file.Paths.get(System.getProperty("user.home"), ".lunarclient", "mods"),
                WeaveUtil.getModFolder());
    }

    @Test void discoveryFeedbackWaitsForPlayerAndRunsOnceOnClientTick() {
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        Mouse mouse = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getType()).thenReturn(Controller.Type.MOUSE);
        when(mouse.poll()).thenReturn(true);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(1f);
        when(environment.getControllers()).thenReturn(new Controller[]{mouse});
        Minecraft minecraft = mock(Minecraft.class);
        try (MockedStatic<ChatUtil> chat = mockStatic(ChatUtil.class);
             MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(minecraft);
            RawInput.pollInput(environment);
            game.verifyNoInteractions(); // Discovery stays game-state-free; selected polling retains its GUI read.
            chat.verifyNoInteractions();
            RawInput.onClientTick(null);
            chat.verifyNoInteractions(); // Startup/disconnected player is absent.
            minecraft.thePlayer = mock(EntityPlayerSP.class);
            RawInput.onClientTick(null);
            RawInput.onClientTick(null);
            chat.verify(() -> ChatUtil.addMessage(EnumChatFormatting.GREEN, "[RawInput] Found mouse"), times(1));
            chat.verifyNoMoreInteractions();
        }
    }

    @Test void rescanDuringNativePollDiscardsOldEpochAndPendingDeltas() {
        Mouse mouse = mock(Mouse.class);
        when(mouse.poll()).thenReturn(true);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(-8.9f); when(y.getPollData()).thenReturn(6.9f);
        Minecraft minecraft = mock(Minecraft.class);
        RawInput.mouse = mouse;
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class);
             MockedStatic<ChatUtil> chat = mockStatic(ChatUtil.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(minecraft);
            RawInput.onClientTick(null);
            RawMouseHelper helper = new RawMouseHelper();
            helper.mouseXYChange();
            RawInput.pollInput(null); // Flush before retaining the backlog to invalidate.
            RawInput.pollInput(null);
            doAnswer(invocation -> {
                new RescanCommand().execute(new String[0]);
                return true;
            }).when(mouse).poll();
            RawInput.pollInput(null);
            assertNull(RawInput.mouse);
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX);
            assertEquals(0, helper.deltaY);
            assertEquals(0L, RawInput.consumeDeltas());
        }
    }

    @Test void deviceReplacementBetweenAxisReadsCannotMixDevices() {
        Mouse original = mock(Mouse.class), replacement = mock(Mouse.class);
        when(original.poll()).thenReturn(true); when(replacement.poll()).thenReturn(true);
        Component x = mock(Component.class), y = mock(Component.class);
        Component nextX = mock(Component.class), nextY = mock(Component.class);
        when(original.getX()).thenReturn(x); when(original.getY()).thenReturn(y);
        when(replacement.getX()).thenReturn(nextX); when(replacement.getY()).thenReturn(nextY);
        when(x.getPollData()).thenAnswer(invocation -> {
            RawInput.mouse = null; // /rescan publication, followed by a new selection.
            RawInput.mouse = replacement;
            return 4.9f;
        });
        when(y.getPollData()).thenReturn(-3.9f);
        when(nextX.getPollData()).thenReturn(-2.9f); when(nextY.getPollData()).thenReturn(9.9f);
        RawInput.mouse = original;
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            RawInput.activateSource();
            RawInput.pollInput(null); // Flush without reading components.
            RawInput.pollInput(null);
            long first = RawInput.consumeDeltas();
            assertEquals(0L, first, "replacement discards the invalidated epoch");
            verify(replacement, never()).poll();
            verify(replacement, never()).getY();
            RawInput.activateSource();
            RawInput.pollInput(null); // Replacement needs its own flush.
            RawInput.pollInput(null);
            long second = RawInput.consumeDeltas();
            assertEquals(-2, MouseDeltaAccumulator.x(second));
            assertEquals(9, MouseDeltaAccumulator.y(second));
        }
    }

    @Test void menuEntryAndExitRetainBacklogButDoNotAccumulateMenuMovement() {
        Mouse mouse = mock(Mouse.class);
        when(mouse.poll()).thenReturn(true);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(-4.9f); when(y.getPollData()).thenReturn(3.9f);
        Minecraft minecraft = mock(Minecraft.class);
        RawInput.mouse = mouse;
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(minecraft);
            RawInput.onClientTick(null);
            RawMouseHelper helper = new RawMouseHelper();
            helper.mouseXYChange();
            RawInput.pollInput(null); // Flush before the pre-menu sample.
            RawInput.pollInput(null);
            minecraft.currentScreen = mock(GuiScreen.class);
            RawInput.onClientTick(null);
            RawInput.pollInput(null);
            RawInput.pollInput(null);
            minecraft.currentScreen = null;
            RawInput.onClientTick(null);
            RawInput.pollInput(null);
            helper.mouseXYChange();
            assertEquals(-8, helper.deltaX);
            assertEquals(-6, helper.deltaY);
            helper.mouseXYChange();
            assertEquals(0, helper.deltaX); assertEquals(0, helper.deltaY);
            verify(mouse, times(5)).poll();
            verify(x, times(2)).getPollData(); verify(y, times(2)).getPollData();
        }
    }

    @Test void unsuccessfulPollInvalidatesDeviceWithoutReadingStaleComponents() {
        Mouse mouse = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.poll()).thenReturn(false);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(2f); when(y.getPollData()).thenReturn(-5f);
        RawInput.mouse = mouse;
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            RawInput.activateSource();
            RawInput.pollInput(null);
            assertNull(RawInput.mouse);
            long pair = RawInput.consumeDeltas();
            assertEquals(0L, pair);
            verify(mouse, never()).getX(); verify(mouse, never()).getY();
        }
    }

    @Test void failedSecondAxisReadDiscardsIncompleteSampleAndFailedEpochBacklog() {
        Mouse mouse = mock(Mouse.class);
        when(mouse.poll()).thenReturn(true);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(2f); when(y.getPollData()).thenReturn(-5f);
        RawInput.mouse = mouse;
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(mock(Minecraft.class));
            RawInput.onClientTick(null);
            RawInput.activateSource();
            RawInput.pollInput(null); // Flush before retaining the sample that must be invalidated.
            RawInput.pollInput(null);
            when(y.getPollData()).thenThrow(new IllegalStateException("device axis unavailable"));
            assertDoesNotThrow(() -> RawInput.pollInput(null));
            assertNull(RawInput.mouse);
            long pair = RawInput.consumeDeltas();
            assertEquals(0L, pair);
            assertEquals(0L, RawInput.consumeDeltas());
        }
    }
}
