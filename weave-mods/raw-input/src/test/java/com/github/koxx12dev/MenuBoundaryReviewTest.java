package com.github.koxx12dev;

import com.github.koxx12dev.util.RawMouseHelper;
import java.lang.reflect.Method;
import net.java.games.input.Component;
import net.java.games.input.Mouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** Same expectations run against candidate and private task-entry source. */
class MenuBoundaryReviewTest {
    private Minecraft minecraft;
    private Mouse device;
    private Component x, y;
    private RawMouseHelper helper;
    private MockedStatic<Minecraft> game;
    private MockedStatic<org.lwjgl.input.Mouse> lwjgl;

    // The atomic baseline has no tick callback. Invoke the candidate callback,
    // if present, without introducing a mock helper or test production branch.
    private void tickSnapshot() throws Exception {
        for (Method method : RawInput.class.getDeclaredMethods()) {
            if (method.getName().equals("onClientTick")) {
                method.setAccessible(true);
                method.invoke(null, new Object[]{null});
                return;
            }
        }
    }

    @BeforeEach void prepare() throws Exception {
        RawInput.mouse = null;
        RawInput.consumeDeltas();
        minecraft = mock(Minecraft.class);
        game = mockStatic(Minecraft.class);
        game.when(Minecraft::getMinecraft).thenReturn(minecraft);
        lwjgl = mockStatic(org.lwjgl.input.Mouse.class);
        device = mock(Mouse.class);
        x = mock(Component.class); y = mock(Component.class);
        when(device.poll()).thenReturn(true);
        when(device.getX()).thenReturn(x); when(device.getY()).thenReturn(y);
        RawInput.mouse = device;
        tickSnapshot();
        helper = new RawMouseHelper();
        helper.mouseXYChange(); // Request candidate activation; baseline drains an empty pair.
        // Only epoch-aware candidates need a successful post-activation flush.
        // Keep the same cases runnable against the private task-entry producer.
        for (Method method : RawInput.class.getDeclaredMethods()) {
            if (method.getName().equals("activateSource")) {
                RawInput.pollInput(null);
                break;
            }
        }
    }

    @AfterEach void cleanup() {
        RawInput.mouse = null;
        RawInput.consumeDeltas();
        lwjgl.close(); game.close();
    }

    @Test void entryBetweenTicksMustNotReplayMenuMotionOnResume() throws Exception {
        when(x.getPollData()).thenReturn(2f); when(y.getPollData()).thenReturn(-1f);
        RawInput.pollInput(null); // Preserve the existing pre-menu backlog policy.
        minecraft.currentScreen = mock(GuiScreen.class); // GUI entry after Tick.Pre.
        when(x.getPollData()).thenReturn(7f); when(y.getPollData()).thenReturn(-3f);
        for (int i = 0; i < 3; i++) RawInput.pollInput(null); // Before the next tick.
        tickSnapshot(); // Now even the candidate knows the menu is open.
        RawInput.pollInput(null); // Long/stable menu movement stays suppressed.
        minecraft.currentScreen = null;
        tickSnapshot();
        helper.mouseXYChange();
        System.out.printf("resume pair: (%d,%d), expected pre-menu only (2,1)%n", helper.deltaX, helper.deltaY);
        assertEquals(2, helper.deltaX, "menu cursor motion must not become camera movement");
        assertEquals(1, helper.deltaY);
    }

    @Test void exitBetweenTicksMustNotDropResumedLookUntilNextTick() throws Exception {
        minecraft.currentScreen = mock(GuiScreen.class);
        tickSnapshot();
        RawInput.pollInput(null);
        minecraft.currentScreen = null; // Close menu after its Tick.Pre snapshot.
        when(x.getPollData()).thenReturn(9f); when(y.getPollData()).thenReturn(-4f);
        RawInput.pollInput(null); // Real look input, before the next tick.
        helper.mouseXYChange();
        System.out.printf("first resumed pair: (%d,%d), expected (9,4)%n", helper.deltaX, helper.deltaY);
        assertEquals(9, helper.deltaX, "resumed look must not wait for another game tick");
        assertEquals(4, helper.deltaY);
    }
}
