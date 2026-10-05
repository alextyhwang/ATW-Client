package com.atw.renderboost.terrain;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainVaoCacheTest {
    static final class Buffer {
        int name, count = 24, mode = 7;
        final Object format = new Object();
        Buffer(int name) { this.name = name; }
    }
    static final class ArraysState {
        Buffer descriptor;
        final TextureDescriptor[] uv = new TextureDescriptor[2];
        boolean blockEnabled;
    }
    static final class TextureDescriptor {
        final int vbo, offset, stride = 28, count = 2;
        final String type;
        TextureDescriptor(Buffer b, String type, int offset) { vbo = b.name; this.type = type; this.offset = offset; }
    }
    /** Executable GL state model: VAO state is separated from global ARRAY_BUFFER/selector. */
    static final class Driver implements TerrainVaoCache.Backend {
        int next = 1, vao, arrayBuffer, selector, setups, binds, refuses;
        final Map<Integer, ArraysState> arrays = new HashMap<>();
        final List<String> draws = new ArrayList<>(), operations = new ArrayList<>();
        final List<Integer> deleted = new ArrayList<>();
        RuntimeException setupFailure, cleanupFailure, zeroBindFailure, uv1EnableFailure, selectorFailure;
        Driver() { arrays.put(0, new ArraysState()); arrays.get(0).blockEnabled = true; }
        public int allocate() {
            if (refuses-- > 0) return 0;
            int id = next++; arrays.put(id, new ArraysState()); operations.add("allocate:" + id); return id;
        }
        public void bind(int id) {
            assertTrue(arrays.containsKey(id)); vao = id; binds++; operations.add("bind:" + id);
            if (id == 0 && zeroBindFailure != null) throw zeroBindFailure;
        }
        public void originalBind(Object object) { arrayBuffer = ((Buffer)object).name; }
        public void delete(int id) {
            assertNotEquals(0, id); assertTrue(arrays.containsKey(id));
            arrays.remove(id); deleted.add(id); if (vao == id) vao = 0;
            if (cleanupFailure != null) throw cleanupFailure;
        }
        public void originalPointers(Object object) {
            setups++; Buffer b = (Buffer)object; arrayBuffer = b.name;
            // Captured routine writes UV0 before selecting UV1: it depends on entry selector0.
            arrays.get(vao).descriptor = b;
            operations.add("pointers:" + b.name + ":selector" + selector);
            arrays.get(vao).uv[selector] = new TextureDescriptor(b, "FLOAT", 16);
            selector = 1; arrays.get(vao).uv[selector] = new TextureDescriptor(b, "SHORT", 24); selector = 0;
            if (setupFailure != null) throw setupFailure;
        }
        public void enableBlockArrays() {
            selector = 0; selector = 1;
            if (uv1EnableFailure != null) throw uv1EnableFailure;
            arrays.get(vao).blockEnabled = true; selector = 0;
        }
        public void restoreDefaultClientSelector() {
            operations.add("restore-selector0");
            if (selectorFailure != null) throw selectorFailure;
            selector = 0;
        }
        void draw(Buffer b, String matrix) {
            assertSame(b, arrays.get(vao).descriptor);
            assertTrue(arrays.get(vao).blockEnabled);
            assertUv(b, arrays.get(vao).uv[0], "FLOAT", 16);
            assertUv(b, arrays.get(vao).uv[1], "SHORT", 24);
            draws.add(b.name + ":" + b.count + ":" + b.mode + ":" + matrix);
        }
        void assertUv(Buffer b, TextureDescriptor uv, String type, int offset) {
            assertNotNull(uv); assertEquals(b.name, uv.vbo); assertEquals(type, uv.type);
            assertEquals(offset, uv.offset); assertEquals(28, uv.stride); assertEquals(2, uv.count);
        }
        void originalEpilogueAndOuterDisable() {
            assertEquals(0, vao); arrayBuffer = 0; arrays.get(0).blockEnabled = false; selector = 0;
        }
    }
    static void submit(TerrainVaoCache c, Driver d, Buffer b, String matrix) {
        c.pointers(b, b.name, b.format, true); d.draw(b, matrix);
    }

    @Test void hitsPreserveDrawOrderLiveCountModeMatrixAndFinalBaselineDescriptors() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 8);
        Object context = new Object(); Buffer a = new Buffer(41), b = new Buffer(99);
        assertTrue(c.enter(context, true)); submit(c, d, a, "cameraA/chunkA"); submit(c, d, b, "cameraA/chunkB"); c.exit();
        assertEquals(3, d.setups); assertSame(b, d.arrays.get(0).descriptor);
        d.originalEpilogueAndOuterDisable(); d.arrays.get(0).blockEnabled = true;
        a.count = 80; b.count = 12; b.mode = 4;
        int setups = d.setups, binds = d.binds;
        assertTrue(c.enter(context, true)); submit(c, d, b, "cameraB/chunkB"); submit(c, d, a, "cameraB/chunkA");
        assertEquals(setups, d.setups); assertEquals(binds + 2, d.binds);
        c.exit(); assertEquals(setups + 1, d.setups); assertEquals(0, d.vao);
        assertSame(a, d.arrays.get(0).descriptor); assertEquals(a.name, d.arrayBuffer); assertEquals(0, d.selector);
        d.originalEpilogueAndOuterDisable(); assertEquals(0, d.arrayBuffer);
        assertEquals(Arrays.asList("41:24:7:cameraA/chunkA", "99:24:7:cameraA/chunkB",
                "99:12:4:cameraB/chunkB", "41:80:7:cameraB/chunkA"), d.draws);
        assertEquals(2, c.hits); assertEquals(2, c.misses); assertEquals(2, c.restorations);
    }
    @Test void emptyLayerRetainsExistingDescriptorsAndIssuesNoBindOrSetup() {
        Driver d = new Driver(); Buffer previous = new Buffer(77); d.originalPointers(previous);
        TerrainVaoCache c = new TerrainVaoCache(d, 2); int setups = d.setups;
        assertTrue(c.enter(new Object(), true)); c.exit();
        assertSame(previous, d.arrays.get(0).descriptor); assertEquals(setups, d.setups); assertEquals(0, d.binds);
    }
    @Test void integerVboReuseCannotReuseAnotherBuffersVaoAndOwnershipIsBounded() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Object context = new Object();
        Buffer a = new Buffer(5), b = new Buffer(5), e = new Buffer(8);
        assertTrue(c.enter(context, true)); submit(c, d, a, "A"); submit(c, d, b, "B");
        assertEquals(0, c.hits); assertEquals(2, c.size()); submit(c, d, a, "A"); submit(c, d, e, "E"); c.exit();
        assertEquals(2, c.size()); assertEquals(Arrays.asList(2), d.deleted); assertEquals(1, c.evictions);
    }
    @Test void liveNameAndExactFormatChangeInvalidateBeforeReuse() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Object context = new Object(); Buffer a = new Buffer(5);
        assertTrue(c.enter(context, true)); submit(c, d, a, "A"); a.name = 12;
        submit(c, d, a, "A"); c.pointers(a, a.name, new Object(), true); d.draw(a, "A"); c.exit();
        assertEquals(0, c.hits); assertEquals(3, c.allocations); assertEquals(2, d.deleted.size()); assertEquals(1, c.size());
    }
    @Test void uploadDeleteAndRegionAssignmentInvalidationsDisposeOnlyOwnedVao() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Object context = new Object(); Buffer a = new Buffer(5);
        for (int i = 0; i < 3; i++) {
            assertTrue(c.enter(context, true)); submit(c, d, a, "A"); c.exit();
            c.invalidateBuffer(a); assertEquals(0, c.size());
        }
        assertEquals(Arrays.asList(1, 2, 3), d.deleted); assertEquals(3, c.invalidations);
        c.invalidateBuffer(new Buffer(5)); assertEquals(3, d.deleted.size());
    }
    @Test void reloadWorldToggleAndClearDisposeSameContextButContextReplacementAbandonsNames() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Object context = new Object(); Buffer a = new Buffer(5);
        assertTrue(c.enter(context, true)); submit(c, d, a, "A"); c.exit(); c.clear(context);
        assertEquals(Arrays.asList(1), d.deleted);
        assertTrue(c.enter(context, true)); submit(c, d, a, "A"); c.exit();
        // New context can recycle the same integer VAO. Old names must never be deleted here.
        d.arrays.clear(); d.arrays.put(0, new ArraysState()); d.arrays.get(0).blockEnabled = true; d.vao = 0; d.next = 2;
        assertTrue(c.enter(new Object(), true)); submit(c, d, a, "A"); c.exit();
        assertEquals(Arrays.asList(1), d.deleted); assertEquals(0, c.hits); assertEquals(1, c.size());
    }
    @Test void allocationRefusalExecutesOriginalSetupAndDrawOnceThenRestoresLastDescriptors() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Buffer a = new Buffer(5), b = new Buffer(7);
        assertTrue(c.enter(new Object(), true)); submit(c, d, a, "A"); d.refuses = 1; submit(c, d, b, "B"); c.exit();
        assertEquals(2, d.draws.size()); assertEquals(3, d.setups); assertSame(b, d.arrays.get(0).descriptor);
        assertEquals(1, c.size()); assertEquals(1, c.fallbacks);
    }
    @Test void ineligibleBufferAfterHitUsesBaselineAndRestoresCorrectFinalDescriptors() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Buffer a = new Buffer(5), b = new Buffer(7);
        assertTrue(c.enter(new Object(), true)); submit(c, d, a, "A"); c.pointers(b, b.name, b.format, false); d.draw(b, "B"); c.exit();
        assertEquals(0, d.vao); assertSame(b, d.arrays.get(0).descriptor); assertEquals(2, d.draws.size());
    }
    @Test void exceptionDoesNotRetrySubmissionOrDrawAndCleanupCannotMaskOriginal() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Buffer a = new Buffer(5);
        assertTrue(c.enter(new Object(), true)); RuntimeException original = new RuntimeException("pointer failure");
        d.setupFailure = original;
        assertSame(original, assertThrows(RuntimeException.class, () -> submit(c, d, a, "A")));
        c.abort(original); assertTrue(c.failed()); assertEquals(0, d.draws.size()); assertEquals(1, d.setups);
        assertEquals(0, d.vao); assertFalse(c.inScope());
    }
    @Test void uncertainDeletionIsNotRetriedAndPreventsFurtherAllocation() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Object context = new Object(); Buffer a = new Buffer(5);
        assertTrue(c.enter(context, true)); submit(c, d, a, "A"); c.exit(); d.cleanupFailure = new RuntimeException("after-delete");
        assertThrows(RuntimeException.class, () -> c.clear(context)); assertTrue(c.failed()); assertEquals(0, c.size());
        c.clear(context); assertEquals(Arrays.asList(1), d.deleted); assertFalse(c.enter(context, true));
    }
    @Test void cleanupFailureIsSuppressedWithoutReplacingTheOriginalExceptionOrRepeatingDraw() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Buffer a = new Buffer(5);
        assertTrue(c.enter(new Object(), true)); submit(c, d, a, "A");
        RuntimeException original = new RuntimeException("draw failure"), cleanup = new RuntimeException("cleanup failure");
        d.zeroBindFailure = cleanup; c.abort(original);
        assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed()); assertEquals(1, d.draws.size());
        assertFalse(c.inScope()); assertTrue(c.failed());
    }
    @Test void unavailableOrOffScopeNeverAllocatesAndNestedEntryDoesNotStealOwnership() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Object context = new Object(); Buffer a = new Buffer(5);
        assertFalse(c.enter(context, false)); c.pointers(a, a.name, a.format, true); d.draw(a, "A"); c.exit();
        assertEquals(0, c.allocations); assertEquals(0, d.binds);
        assertTrue(c.enter(context, true)); assertFalse(c.enter(context, true)); assertTrue(c.inScope()); c.exit();
    }
    @Test void invalidationInsideScopeIsRejectedBeforeAnyDeletion() {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 2); Object context = new Object();
        assertTrue(c.enter(context, true)); assertThrows(IllegalStateException.class, () -> c.clear(context)); assertTrue(d.deleted.isEmpty()); c.exit();
    }
    @Test void firstMissFailureAfterSelectingUv1RestoresSeparateDescriptorsBeforeOneOriginalDraw() {
        recoversUv1Failure(false);
    }
    @Test void missFailureAfterSuccessfulBufferRestoresSelectorBeforePreviousAndCurrentOriginalPointers() {
        recoversUv1Failure(true);
    }
    private static void recoversUv1Failure(boolean previousSuccess) {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 4);
        Buffer stale = new Buffer(3), previous = new Buffer(5), current = new Buffer(7);
        d.originalPointers(stale); assertTrue(c.enter(new Object(), true));
        if (previousSuccess) submit(c, d, previous, "previous");
        int drawsBefore = d.draws.size(), setupsBefore = d.setups;
        RuntimeException primary = new RuntimeException("enable UV1 failed"); d.uv1EnableFailure = primary;
        assertSame(primary, assertThrows(RuntimeException.class, () -> c.pointers(current, current.name, current.format, true)));
        assertEquals(1, d.selector); assertEquals(setupsBefore, d.setups);
        c.recoverAddedFailure(primary, current);
        assertEquals(0, d.vao); assertEquals(0, d.selector);
        d.assertUv(current, d.arrays.get(0).uv[0], "FLOAT", 16);
        d.assertUv(current, d.arrays.get(0).uv[1], "SHORT", 24);
        d.draw(current, "original"); c.exit();
        assertEquals(drawsBefore + 1, d.draws.size());
        assertEquals(setupsBefore + (previousSuccess ? 2 : 1), d.setups);
        assertTrue(d.operations.stream().filter(s -> s.startsWith("pointers:")).allMatch(s -> s.endsWith("selector0")));
        assertTrue(c.failed()); assertFalse(c.inScope()); assertEquals(0, primary.getSuppressed().length);
    }
    @Test void selectorRecoveryFailurePropagatesPrimaryAndSuppressesCleanupWithoutAnyOriginalDraw() {
        failedRecovery(false);
    }
    @Test void selectorRecoveryFailureAfterSuccessfulBufferDoesNotAttemptOriginalRestorationOrCurrentDraw() {
        failedRecovery(true);
    }
    private static void failedRecovery(boolean previousSuccess) {
        Driver d = new Driver(); TerrainVaoCache c = new TerrainVaoCache(d, 4);
        Buffer previous = new Buffer(5), current = new Buffer(7);
        assertTrue(c.enter(new Object(), true));
        if (previousSuccess) submit(c, d, previous, "previous");
        RuntimeException primary = new RuntimeException("enable UV1 failed"), secondary = new RuntimeException("selector cleanup failed");
        d.uv1EnableFailure = primary;
        assertSame(primary, assertThrows(RuntimeException.class, () -> c.pointers(current, current.name, current.format, true)));
        d.selectorFailure = secondary; int setupsBefore = d.setups, drawsBefore = d.draws.size();
        assertSame(primary, assertThrows(RuntimeException.class, () -> { c.recoverAddedFailure(primary, current); d.draw(current, "unsafe"); }));
        assertArrayEquals(new Throwable[]{secondary}, primary.getSuppressed());
        assertEquals(setupsBefore, d.setups); assertEquals(drawsBefore, d.draws.size());
        assertFalse(c.inScope()); assertTrue(c.failed());
    }
}
