package com.atw.optimalzone;

import net.weavemc.api.ModInitializer;

/** Defer the game-dependent class itself until game transformation is ready. */
public final class OverlayInitializer implements ModInitializer {
    @Override public void init() {
        new OptimalZoneMod().init();
    }
}
