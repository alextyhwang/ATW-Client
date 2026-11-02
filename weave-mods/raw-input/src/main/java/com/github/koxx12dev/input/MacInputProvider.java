package com.github.koxx12dev.input;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import net.weavemc.api.event.StartGameEvent;
import net.weavemc.api.event.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Experimental continuity over the installed MouseHelper, not a native raw
 * input provider. Installation owns no window, event loop or polling thread.
 */
public final class MacInputProvider {
    private boolean installationAttempted;
    private MacContinuityMouseHelper helper;

    /** Startup-only opt-in; absent, invalid or inaccessible properties keep the original path. */
    public static boolean isRequested() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            boolean mac = os.equals("mac os x") || os.equals("macos") || os.equals("darwin");
            return mac && Boolean.getBoolean("atw.input.macContinuity");
        } catch (SecurityException unavailable) {
            return false;
        }
    }

    public void onStart(StartGameEvent.Post event) {
        if (installationAttempted) return;
        installationAttempted = true;
        if (!isRequested()) {
            report("request declined: Mac startup opt-in absent");
            return;
        }
        try {
            Minecraft game = Minecraft.getMinecraft();
            if (!game.isCallingFromMinecraftThread()) {
                report("request declined: game-thread owner unavailable");
                return;
            }
            MouseHelper original = game.mouseHelper;
            // A subclass is another input owner's seam. Do not wrap it, replace
            // it, or interpret the class name as proof of its native semantics.
            if (original == null || original.getClass() != MouseHelper.class) {
                report("request declined: unknown MouseHelper owner");
                return;
            }
            if (!Display.isCreated() || !Mouse.isCreated()) {
                report("request declined: existing LWJGL input unavailable");
                return;
            }
            helper = new MacContinuityMouseHelper(game, original);
            game.mouseHelper = helper;
            report("experimental continuity installed over existing MouseHelper; native raw input unqualified");
        } catch (RuntimeException | LinkageError unavailable) {
            // No native replacement was initialized. Do not expose exception
            // messages/paths or treat a missing library as a supported source.
            report("request declined: existing input/state API unavailable");
        }
    }

    public void onTick(TickEvent.Pre event) {
        if (helper == null) return;
        if (!helper.isInstalled()) {
            // Another owner replaced us (or state failure restored original).
            // Never reassert ownership or overwrite that replacement.
            helper = null;
            return;
        }
        helper.observeState();
    }

    static void report(String status) {
        System.out.println("[RawInput] Mac " + status);
    }
}
