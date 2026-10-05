package com.github.koxx12dev;

import net.weavemc.api.ModInitializer;

/** Minecraft and JInput construction belongs after Weave's game transforms. */
public final class RawInputInitializer implements ModInitializer {
    @Override public void init() {
        new RawInput().init();
    }
}
