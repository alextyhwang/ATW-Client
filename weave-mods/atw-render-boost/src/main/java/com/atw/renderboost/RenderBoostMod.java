package com.atw.renderboost;

import java.lang.instrument.Instrumentation;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.command.CommandBus;

public final class RenderBoostMod implements ModInitializer {
    @Override public void preInit(Instrumentation instrumentation) {
        // Do not load Minecraft classes during preInit.
        System.out.println("[ATW Render Boost] Minecraft 1.8.9; experimental frame error policy and glyph cache default OFF; no performance claim");
    }
    @Override public void init() {
        CommandBus.register(new BoostCommand());
    }
}
