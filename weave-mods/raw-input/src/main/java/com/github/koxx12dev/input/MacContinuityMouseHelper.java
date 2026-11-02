package com.github.koxx12dev.input;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Uses the already-installed helper as its sole movement/grab source. Exact
 * class admission is an ownership restriction, not a transformed-bytecode pin.
 */
final class MacContinuityMouseHelper extends MouseHelper {
    private final Minecraft game;
    private final MouseHelper original;
    private final Thread ownerThread;
    private final LookInputContinuity continuity = new LookInputContinuity();

    MacContinuityMouseHelper(Minecraft game, MouseHelper original) {
        this.game = game;
        this.original = original;
        ownerThread = Thread.currentThread();
    }

    boolean isInstalled() {
        requireOwner();
        return game.mouseHelper == this;
    }

    void observeState() {
        requireOwner();
        if (!isInstalled()) return;
        try {
            boolean canLook = game.theWorld != null && game.thePlayer != null
                    && game.currentScreen == null && game.inGameHasFocus
                    && Display.isCreated() && Mouse.isCreated()
                    && Display.isActive() && Mouse.isGrabbed();
            continuity.observe(canLook, game.theWorld, game.thePlayer);
            if (!canLook) clearOutput();
        } catch (RuntimeException | LinkageError unavailable) {
            // Return to the exact object we borrowed. This does not retry a
            // movement read, change grab state or replace somebody else's helper.
            continuity.reset();
            if (game.mouseHelper == this) game.mouseHelper = original;
            MacInputProvider.report("continuity disabled: state API unavailable; original helper restored");
        }
    }

    @Override
    public void mouseXYChange() {
        requireOwner();
        clearOutput();
        if (!isInstalled()) return; // Stale references must not become a second consumer.
        // Exactly one call to the original owner, never super plus delegate or
        // direct getDX/getDY/poll/next. Original input failures still propagate.
        original.mouseXYChange();
        this.deltaX = original.deltaX;
        this.deltaY = original.deltaY;
        observeState();
        // If observation restored original, its already-read pair is the
        // fallback. Do not discard or consume it a second time.
        if (isInstalled() && !continuity.admitSample()) clearOutput();
    }

    @Override
    public void grabMouseCursor() {
        requireOwner();
        if (!isInstalled()) return;
        continuity.reset();
        clearOutput();
        original.grabMouseCursor();
    }

    @Override
    public void ungrabMouseCursor() {
        requireOwner();
        if (!isInstalled()) return;
        continuity.reset();
        clearOutput();
        original.ungrabMouseCursor();
    }

    private void clearOutput() {
        this.deltaX = 0;
        this.deltaY = 0;
    }

    private void requireOwner() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Mac continuity requires its installing game thread");
        }
    }
}
