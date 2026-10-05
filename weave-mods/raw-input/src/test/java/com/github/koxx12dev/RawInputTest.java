package com.github.koxx12dev;

import com.github.koxx12dev.command.RescanCommand;
import com.github.koxx12dev.util.ChatUtil;
import com.github.koxx12dev.util.RawMouseHelper;
import com.github.koxx12dev.util.WeaveUtil;
import net.java.games.input.Mouse;
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RawInputTest {
    @Test void discoversMovingMouseAndRescanReentersDiscovery() {
        ControllerEnvironment environment = mock(ControllerEnvironment.class);
        Mouse mouse = mock(Mouse.class);
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getType()).thenReturn(Controller.Type.MOUSE);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(environment.getControllers()).thenReturn(new Controller[]{mouse});
        RawInput.mouse = null;
        try (MockedStatic<ChatUtil> chat = mockStatic(ChatUtil.class)) {
            when(x.getPollData()).thenReturn(0.1f);
            RawInput.pollInput(environment);
            assertNull(RawInput.mouse, "original epsilon boundary should not select a stationary mouse");
            when(x.getPollData()).thenReturn(0.2f);
            RawInput.pollInput(environment);
            assertSame(mouse, RawInput.mouse);
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
        Component x = mock(Component.class), y = mock(Component.class);
        when(mouse.getX()).thenReturn(x); when(mouse.getY()).thenReturn(y);
        when(x.getPollData()).thenReturn(4.9f); when(y.getPollData()).thenReturn(-3.9f);
        Minecraft minecraft = mock(Minecraft.class);
        RawInput.mouse = mouse; RawInput.dx = 1; RawInput.dy = 2;
        try (MockedStatic<Minecraft> game = mockStatic(Minecraft.class)) {
            game.when(Minecraft::getMinecraft).thenReturn(minecraft);
            RawInput.pollInput(null);
            assertEquals(5, RawInput.dx); assertEquals(-1, RawInput.dy);
            minecraft.currentScreen = mock(GuiScreen.class);
            RawInput.pollInput(null);
            assertEquals(5, RawInput.dx); assertEquals(-1, RawInput.dy);
            verify(mouse, times(2)).poll();
        } finally {
            RawInput.mouse = null; RawInput.dx = 0; RawInput.dy = 0;
        }
    }

    @Test void mouseHelperConsumesDeltasAndPreservesYAxisSign() {
        RawInput.dx = 19; RawInput.dy = -7;
        RawMouseHelper helper = new RawMouseHelper();
        helper.mouseXYChange();
        assertEquals(19, helper.deltaX);
        assertEquals(7, helper.deltaY);
        assertEquals(0, RawInput.dx); assertEquals(0, RawInput.dy);
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
}
