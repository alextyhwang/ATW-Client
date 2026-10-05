package com.atw.renderboost.terrain;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;

/**
 * Render-thread state machine. It never draws, stores a vertex count, or owns a VBO.
 * The live backend is admitted only after all supporting lifecycle hooks pass.
 */
public final class TerrainVaoCache {
    public interface Backend {
        int allocate();
        void bind(int vao);
        /** Preserve the global ARRAY_BUFFER association on hits, too. */
        void originalBind(Object buffer);
        void delete(int vao);
        /** The admitted existing VBO bind and pointer routine; no other state or draw. */
        void originalPointers(Object buffer);
        /** Enable position/color/UV0/UV1 on a newly bound VAO; leave selector at UV0. */
        void enableBlockArrays();
        /** Restore the admitted UV0 selector before calling any original pointer routine. */
        void restoreDefaultClientSelector();
    }

    private static final class Entry {
        final Object buffer, format;
        final int vbo, vao;
        final long resourceGeneration;
        Entry(Object buffer, Object format, int vbo, int vao, long generation) {
            this.buffer = buffer; this.format = format; this.vbo = vbo; this.vao = vao;
            this.resourceGeneration = generation;
        }
    }

    private final Backend backend;
    private final int limit;
    private final IdentityHashMap<Object, Entry> byBuffer = new IdentityHashMap<>();
    private final LinkedHashMap<Entry, Boolean> lru = new LinkedHashMap<>(16, .75f, true);
    private Object context, lastBuffer;
    private long resourceGeneration;
    private boolean scope, failed, touched;
    public long opportunities, hits, misses, allocations, evictions, invalidations, fallbacks, restorations;

    public TerrainVaoCache(Backend backend, int limit) {
        if (backend == null || limit < 1) throw new IllegalArgumentException("backend and positive limit required");
        this.backend = backend; this.limit = limit;
    }

    /**
     * Caller must establish a verified context generation and all GL/state eligibility first.
     * Context identity means an actual context lifetime token, not an integer GL name.
     */
    public boolean enter(Object currentContext, boolean eligible) {
        if (scope) { fallbacks++; return false; } // Never discard the outer scope's ownership.
        if (context != currentContext) {
            abandon(); context = currentContext; // Names are not shared across contexts.
        }
        if (!eligible || currentContext == null || failed) { fallbacks++; return false; }
        scope = true; lastBuffer = null; touched = false;
        return true;
    }

    /**
     * Executes only the original bind/pointers on fallback or miss, one VAO bind on a hit.
     * The caller executes its original draw exactly once afterwards, with its live count/mode.
     * formatToken must include the exact immutable layout signature, validated by the caller.
     */
    public void pointers(Object buffer, int liveVbo, Object formatToken, boolean bufferEligible) {
        opportunities++;
        if (!scope) { fallbacks++; backend.originalPointers(buffer); return; }
        if (failed || !bufferEligible || buffer == null || liveVbo <= 0 || formatToken == null) {
            fallback(buffer); return;
        }
        Entry found = byBuffer.get(buffer);
        if (found != null && (found.vbo != liveVbo || found.format != formatToken
                || found.resourceGeneration != resourceGeneration)) {
            remove(found); found = null;
        }
        if (found != null) {
            // Preserve the global ARRAY_BUFFER binding as well as the VAO descriptors.
            backend.bind(found.vao); touched = true; backend.originalBind(buffer);
            lru.get(found); hits++; lastBuffer = buffer; return;
        }
        misses++;
        if (byBuffer.size() == limit) {
            Entry oldest = lru.keySet().iterator().next(); remove(oldest); evictions++;
        }
        int vao;
        try { vao = backend.allocate(); }
        catch (RuntimeException | Error failure) { failed = true; throw failure; }
        if (vao <= 0) { fallback(buffer); return; } // No GL state changed by allocation refusal.
        Entry entry = new Entry(buffer, formatToken, liveVbo, vao, resourceGeneration);
        // Track ownership BEFORE any operation which can throw, so capacity remains bounded.
        byBuffer.put(buffer, entry); lru.put(entry, Boolean.TRUE); allocations++;
        touched = true;
        try {
            backend.bind(vao);
            backend.enableBlockArrays();
            backend.originalPointers(buffer);
            lastBuffer = buffer;
        } catch (RuntimeException | Error failure) {
            failed = true;
            // Do not retry a primitive or draw after partial submission. Propagate unchanged.
            throw failure;
        }
    }

    private void fallback(Object buffer) {
        fallbacks++;
        if (touched) backend.bind(0);
        if (touched) backend.restoreDefaultClientSelector();
        backend.originalPointers(buffer);
        lastBuffer = buffer;
    }

    /** Before the original ARRAY_BUFFER=0/resetColor/list-clear and outer array disables. */
    public void exit() {
        if (!scope) return;
        try {
            if (touched) {
                backend.bind(0);
                backend.restoreDefaultClientSelector();
                if (lastBuffer != null) { backend.originalPointers(lastBuffer); restorations++; }
            }
        } finally { scope = false; touched = false; lastBuffer = null; }
    }

    /** Original exception is never replaced by cleanup, and no original draw is repeated. */
    public void abort(Throwable original) {
        failed = true;
        try { exit(); } catch (RuntimeException | Error cleanup) {
            if (cleanup != original) original.addSuppressed(cleanup);
        }
        // Retain owned IDs for same-context safe cleanup, or abandon on context destruction.
    }

    /** Recover an added setup failure once; cleanup cannot mask it or permit an unproven draw. */
    public void recoverAddedFailure(RuntimeException primary, Object buffer) {
        failed = true;
        try {
            // Restore UV0 even on a first miss, where lastBuffer is still null.
            exit();
            backend.bind(0);
            backend.restoreDefaultClientSelector();
            backend.originalPointers(buffer);
        } catch (RuntimeException | Error cleanup) {
            if (cleanup != primary) primary.addSuppressed(cleanup);
            throw primary; // No original draw is safe after incomplete recovery.
        }
    }

    /** Before the admitted upload, delete, or region reassignment, on the owning thread. */
    public void invalidateBuffer(Object buffer) {
        Entry found = byBuffer.get(buffer);
        if (found != null) { remove(found); invalidations++; }
    }

    /** Only outside a scope, with the verified owning context current. */
    public void clear(Object currentContext) {
        if (scope) throw new IllegalStateException("Cannot invalidate during terrain submission");
        resourceGeneration++;
        if (context != currentContext || currentContext == null) {
            abandon(); context = currentContext; return;
        }
        for (Entry entry : new ArrayList<>(lru.keySet())) { remove(entry); invalidations++; }
    }

    private void remove(Entry entry) {
        // Deleting a currently bound owned VAO returns to VAO 0. Caller establishes pointers next.
        // Remove ownership before deletion. If a backend throws after submitting the deletion,
        // retrying this integer later could delete a name recycled for another owner.
        byBuffer.remove(entry.buffer); lru.remove(entry);
        try { backend.delete(entry.vao); } catch (RuntimeException | Error failure) {
            failed = true; throw failure; // No further allocation; uncertain names die with context.
        }
    }
    private void abandon() {
        invalidations += byBuffer.size(); byBuffer.clear(); lru.clear();
        scope = false; touched = false; lastBuffer = null;
    }
    /** Context became unavailable/replaced inside a scope: never submit old names again. */
    public void abandonContext() { abandon(); context = null; }
    public int size() { return byBuffer.size(); }
    public boolean failed() { return failed; }
    public boolean inScope() { return scope; }
}
