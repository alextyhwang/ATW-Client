package com.github.koxx12dev.input;

/** Game-thread state only; no native reads, accumulation, timers or worker snapshots. */
final class LookInputContinuity {
    private Object world;
    private Object player;
    private boolean eligible;
    private boolean ready;

    void observe(boolean canLook, Object currentWorld, Object currentPlayer) {
        if (!canLook || world != currentWorld || player != currentPlayer) ready = false;
        world = currentWorld;
        player = currentPlayer;
        eligible = canLook;
    }

    /** Call only after the original owner has consumed one complete movement sample. */
    boolean admitSample() {
        if (!eligible) return false;
        if (!ready) {
            // Drain-and-discard the interval spanning startup/resume/recenter.
            // Observation alone must never acknowledge this boundary.
            ready = true;
            return false;
        }
        return true;
    }

    void reset() {
        world = null;
        player = null;
        eligible = false;
        ready = false;
    }
}
