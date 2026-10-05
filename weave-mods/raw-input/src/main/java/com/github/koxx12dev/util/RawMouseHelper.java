/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.util.MouseHelper
 */
package com.github.koxx12dev.util;

import com.github.koxx12dev.RawInput;
import net.minecraft.util.MouseHelper;

public class RawMouseHelper
extends MouseHelper {
    public void mouseXYChange() {
        this.deltaX = RawInput.dx;
        RawInput.dx = 0;
        this.deltaY = -RawInput.dy;
        RawInput.dy = 0;
    }
}
