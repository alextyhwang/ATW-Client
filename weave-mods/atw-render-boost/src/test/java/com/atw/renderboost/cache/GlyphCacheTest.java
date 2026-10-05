package com.atw.renderboost.cache;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlyphCacheTest {
    static final class Driver implements GlyphCache.Backend {
        int next = 1, replays, starts, ends;
        boolean outOfMemory, failBegin, failEnd;
        Set<Integer> live = new HashSet<>();
        List<Integer> deleted = new ArrayList<>();
        @Override public int allocate() { if (outOfMemory) return 0; live.add(next); return next++; }
        @Override public void begin(int id) { assertTrue(live.contains(id)); starts++; if (failBegin) throw new IllegalStateException("refused"); }
        @Override public void end() { ends++; if (failEnd) throw new IllegalStateException("refused"); }
        @Override public void replay(int id) { assertTrue(live.contains(id), "never replay freed IDs"); replays++; }
        @Override public void delete(int id) { assertTrue(live.remove(id), "delete exactly once"); deleted.add(id); }
    }
    private GlyphKey key(Object font, float x) { return new GlyphKey(font, 16, 32, 0, 5.99f, x, 10); }
    private void admit(GlyphCache c, GlyphKey k) {
        assertFalse(c.begin(k)); c.end(); assertFalse(c.begin(k)); c.end(); assertFalse(c.begin(k));
        assertTrue(c.recording()); c.end();
    }
    @Test void admitsOnlyRepeatedPrimitivesThenReplays() {
        Driver d = new Driver(); GlyphCache c = new GlyphCache(d, 2, 4, 3);
        GlyphKey k = key(new Object(), 0);
        admit(c, k);
        assertEquals(1, d.starts); assertEquals(1, d.ends);
        assertTrue(c.begin(k)); c.end();
        assertEquals(1, c.hits); assertEquals(3, c.misses); assertEquals(1, c.compilations);
        assertEquals(1, d.replays); assertFalse(c.recording());
    }
    @Test void evictionUsesAccessOrderAndRespectsBothBounds() {
        Driver d = new Driver(); GlyphCache c = new GlyphCache(d, 2, 4, 3); Object font = new Object();
        GlyphKey a = key(font, 1), b = key(font, 2), e = key(font, 3);
        admit(c, a); admit(c, b); assertTrue(c.begin(a)); admit(c, e);
        assertEquals(Collections.singletonList(2), d.deleted);
        assertEquals(2, c.size()); assertEquals(2, d.live.size());
        for (int i = 0; i < 1000; i++) { c.begin(key(font, 100 + i)); c.end(); }
        assertEquals(4, c.probationSize()); assertEquals(2, c.size());
    }
    @Test void reloadReleasesCommittedAndInProgressListsAndAdmissionHistory() {
        Driver d = new Driver(); GlyphCache c = new GlyphCache(d, 2, 4, 3); Object font = new Object();
        admit(c, key(font, 1)); GlyphKey b = key(font, 2);
        c.begin(b); c.end(); c.begin(b); c.end(); c.begin(b);
        c.invalidate(true);
        assertEquals(0, d.live.size()); assertEquals(2, d.ends);
        assertEquals(0, c.size()); assertEquals(0, c.probationSize()); assertFalse(c.recording());
        assertFalse(c.begin(b)); assertFalse(c.recording());
    }
    @Test void lostContextForgetsIdsWithoutDeletingNamesInNewContext() {
        Driver d = new Driver(); GlyphCache c = new GlyphCache(d, 2, 4, 3);
        admit(c, key(new Object(), 1)); c.invalidate(false);
        assertTrue(d.deleted.isEmpty()); assertEquals(0, c.size()); assertFalse(c.recording());
    }
    @Test void nestedAttemptCannotReplayOrStartAnotherList() {
        Driver d = new Driver(); GlyphCache c = new GlyphCache(d, 2, 4, 3); Object font = new Object();
        GlyphKey a = key(font, 1), b = key(font, 2); admit(c, a);
        c.begin(b); c.end(); c.begin(b); c.end(); c.begin(b);
        assertFalse(c.begin(a)); assertEquals(0, d.replays); assertEquals(2, d.starts);
        c.end(); assertTrue(c.begin(a));
    }
    @Test void allocationFailureFallsBackWithoutPerFrameRetry() {
        Driver d = new Driver(); d.outOfMemory = true;
        GlyphCache c = new GlyphCache(d, 2, 4, 3); GlyphKey k = key(new Object(), 1);
        for (int i = 0; i < 100; i++) { assertFalse(c.begin(k)); c.end(); }
        assertEquals(0, d.starts); assertFalse(c.recording()); assertEquals(0, c.size());
    }
    @Test void refusedRecordingDoesNotLeakOrCommitAnEmptyList() {
        for (boolean atBegin : new boolean[]{true, false}) {
            Driver d = new Driver(); GlyphCache c = new GlyphCache(d, 2, 4, 3);
            GlyphKey k = key(new Object(), 1); c.begin(k); c.end(); c.begin(k); c.end();
            d.failBegin = atBegin; d.failEnd = !atBegin;
            if (atBegin) assertThrows(IllegalStateException.class, () -> c.begin(k));
            else { c.begin(k); assertThrows(IllegalStateException.class, c::end); }
            assertEquals(0, c.size()); assertFalse(c.recording()); assertTrue(d.live.isEmpty());
            c.invalidate(true); assertEquals(1, d.deleted.size());
        }
    }
    @Test void allGeometryInputsAndFontIdentityMatter() {
        Object font = new Object(); GlyphKey k = new GlyphKey(font, 16, 32, 0, 5.99f, 0, 10);
        assertEquals(k, new GlyphKey(font, 16, 32, 0, 5.99f, 0, 10));
        assertNotEquals(k, new GlyphKey(new Object(), 16, 32, 0, 5.99f, 0, 10));
        assertNotEquals(k, new GlyphKey(font, 17, 32, 0, 5.99f, 0, 10));
        assertNotEquals(k, new GlyphKey(font, 16, 33, 0, 5.99f, 0, 10));
        assertNotEquals(k, new GlyphKey(font, 16, 32, 1, 5.99f, 0, 10));
        assertNotEquals(k, new GlyphKey(font, 16, 32, 0, 6, 0, 10));
        assertNotEquals(k, new GlyphKey(font, 16, 32, 0, 5.99f, -0.0f, 10));
        assertNotEquals(k, new GlyphKey(font, 16, 32, 0, 5.99f, 0, 11));
    }
}
