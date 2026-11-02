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
    private RawInput.RawSource previousSource;

    @Override
    public void mouseXYChange() {
        // Drain LWJGL on the game thread even while raw owns movement. Its
        // deltas would otherwise accumulate and replay on a later fallback.
        super.mouseXYChange();
        RawInput.RawSource source = RawInput.activateSource();
        if (source == null && previousSource == null) return;
        if (source != previousSource) {
            previousSource = source;
            this.deltaX = 0;
            this.deltaY = 0;
            return;
        }
        long pair = RawInput.consumeDeltas(source);
        this.deltaX = MouseDeltaAccumulator.x(pair);
        this.deltaY = -MouseDeltaAccumulator.y(pair);
    }
}
