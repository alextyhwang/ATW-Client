package com.atw.renderboost.cache;

import java.util.LinkedHashMap;
import java.util.Map;

/** Render-thread-only bounded ownership of display lists, independently testable. */
public final class GlyphCache {
    public interface Backend {
        int allocate();
        void begin(int id);
        void end();
        void replay(int id);
        void delete(int id);
    }
    private final Backend backend;
    private final int capacity, probationCapacity, admission;
    private final LinkedHashMap<GlyphKey, Integer> lists = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<GlyphKey, Integer> probation = new LinkedHashMap<>(16, 0.75f, true);
    private GlyphKey pending;
    private int pendingId;
    public long hits, misses, compilations, evictions, invalidations;

    public GlyphCache(Backend backend, int capacity, int probationCapacity, int admission) {
        if (capacity < 1 || probationCapacity < 1 || admission < 2) throw new IllegalArgumentException();
        this.backend = backend;
        this.capacity = capacity;
        this.probationCapacity = probationCapacity;
        this.admission = admission;
    }

    /** True skips only the original primitive, false executes it (possibly recording). */
    public boolean begin(GlyphKey key) {
        if (pending != null) return false; // Never nest compilation.
        Integer id = lists.get(key);
        if (id != null) {
            backend.replay(id);
            hits++;
            return true;
        }
        misses++;
        Integer old = probation.get(key);
        int seen = old == null ? 1 : Math.min(admission + 1, old + 1);
        probation.put(key, seen);
        trimProbation();
        if (seen < admission) return false;
        // A driver allocation failure does not retry every glyph/frame.
        if (seen > admission) return false;
        if (lists.size() == capacity) {
            Map.Entry<GlyphKey, Integer> eldest = lists.entrySet().iterator().next();
            backend.delete(eldest.getValue());
            lists.remove(eldest.getKey());
            evictions++;
        }
        int next = backend.allocate();
        if (next == 0) return false;
        try {
            backend.begin(next);
        } catch (RuntimeException | LinkageError e) {
            backend.delete(next);
            throw e;
        }
        pending = key;
        pendingId = next;
        return false;
    }

    public void end() {
        if (pending == null) return;
        GlyphKey key = pending;
        int id = pendingId;
        try {
            backend.end();
        } catch (RuntimeException | LinkageError e) {
            backend.delete(id);
            pending = null;
            pendingId = 0;
            throw e;
        }
        lists.put(key, id);
        probation.remove(pending);
        pending = null;
        pendingId = 0;
        compilations++;
    }

    public void invalidate(boolean sameContext) {
        if (sameContext) {
            if (pending != null) {
                backend.end();
                backend.delete(pendingId);
            }
            for (Integer id : lists.values()) backend.delete(id);
        }
        lists.clear();
        probation.clear();
        pending = null;
        pendingId = 0;
        invalidations++;
    }

    public int size() { return lists.size(); }
    public int probationSize() { return probation.size(); }
    public boolean recording() { return pending != null; }
    public int recordingId() { return pendingId; }
    private void trimProbation() {
        while (probation.size() > probationCapacity) probation.remove(probation.keySet().iterator().next());
    }
}
