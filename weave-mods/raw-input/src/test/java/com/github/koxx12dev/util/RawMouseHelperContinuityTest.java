package com.github.koxx12dev.util;

import com.github.koxx12dev.RawInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Mouse;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class RawMouseHelperContinuityTest {
    @AfterEach void clearTransfer() {
        RawInput.mouse = null;
        RawInput.consumeDeltas();
    }

    @Test void nullSelectionUsesVanillaLwjglMovementAtRealHelper() {
        RawInput.mouse = null;
        RawMouseHelper helper = new RawMouseHelper();
        try (MockedStatic<Mouse> lwjgl = mockStatic(Mouse.class)) {
            lwjgl.when(Mouse::getDX).thenReturn(19);
            lwjgl.when(Mouse::getDY).thenReturn(-7);
            helper.mouseXYChange();
            assertEquals(19, helper.deltaX);
            assertEquals(-7, helper.deltaY);
        }
    }
}
