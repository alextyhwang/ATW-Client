package com.atw.renderboost.benchmark;

/** Game/GL-free measured-window validity, shared with live sampling and activity export. */
public final class TerrainBenchmarkGuard {
    public static final class State {
        public final boolean requested, available, eligible, failed, errorFallback;
        public final long revision, rejectedScopes, fallbacks, hits;
        public State(boolean requested, boolean available, boolean eligible, boolean failed, boolean errorFallback,
                long revision, long rejectedScopes, long fallbacks, long hits) {
            this.requested = requested; this.available = available; this.eligible = eligible;
            this.failed = failed; this.errorFallback = errorFallback; this.revision = revision;
            this.rejectedScopes = rejectedScopes; this.fallbacks = fallbacks; this.hits = hits;
        }
        private boolean usable() { return requested && available && eligible && !failed && !errorFallback; }
    }
    private final State start;
    private String invalid;

    public TerrainBenchmarkGuard(State start) { this.start = start; check(start); }

    public void check(State current) {
        if (invalid == null) {
            if (current.requested != start.requested || current.revision != start.revision)
                invalid = "Terrain mode/evidence changed during measurement";
            else if (start.requested && (!current.usable() || current.rejectedScopes != start.rejectedScopes
                    || current.fallbacks != start.fallbacks))
                invalid = "Terrain candidate lost eligibility or failed during measurement";
        }
        if (invalid != null) throw new IllegalStateException(invalid);
    }

    /** Hits alone cannot establish activity after a driver error, GL error, or rejection. */
    public static boolean cacheActive(State current, long startHits) {
        return current.usable() && current.hits > startHits;
    }
}
