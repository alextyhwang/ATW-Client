package com.atw.levelhead;

import net.weavemc.api.ModInitializer;

/** The loader constructs this before game transforms: defer all game references. */
public final class LevelHeadInitializer implements ModInitializer {
    @Override public void init() {
        new ATWLevelHead().init();
    }
}
