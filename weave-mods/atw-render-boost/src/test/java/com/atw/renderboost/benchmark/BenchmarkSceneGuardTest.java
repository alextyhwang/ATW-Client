package com.atw.renderboost.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkSceneGuardTest {
    private final Object world = new Object();
    private final BenchmarkSceneGuard.Camera start = new BenchmarkSceneGuard.Camera(1, 64, 2, 45, 10);
    private final String settings = "2560x1440,fullscreen=false,vsync=false,vbo=true,distance=12";
    private boolean accepts(BenchmarkSceneGuard guard, BenchmarkSceneGuard.Camera camera) {
        return guard.accepts(world, camera, settings, true, false, false, true);
    }
    @Test void stationaryRejectsEachCameraCoordinateAndAngleDrift() {
        BenchmarkSceneGuard guard = new BenchmarkSceneGuard(world, start, settings, false);
        assertEquals("stationary", guard.mode()); assertTrue(accepts(guard, start));
        assertFalse(accepts(guard, new BenchmarkSceneGuard.Camera(1.001, 64, 2, 45, 10)));
        assertFalse(accepts(guard, new BenchmarkSceneGuard.Camera(1, 64.001, 2, 45, 10)));
        assertFalse(accepts(guard, new BenchmarkSceneGuard.Camera(1, 64, 2.001, 45, 10)));
        assertFalse(accepts(guard, new BenchmarkSceneGuard.Camera(1, 64, 2, 45.001f, 10)));
        assertFalse(accepts(guard, new BenchmarkSceneGuard.Camera(1, 64, 2, 45, 10.001f)));
    }
    @Test void movingAcceptsCameraDriftWithoutChangingItsGuardedWorldOrSettings() {
        BenchmarkSceneGuard guard = new BenchmarkSceneGuard(world, start, settings, true);
        assertEquals("moving", guard.mode());
        assertTrue(accepts(guard, new BenchmarkSceneGuard.Camera(500, 80, -300, 180, -70)));
    }
    @Test void bothModesRejectWorldFocusPauseGuiPlayerWindowAndSettingsChanges() {
        for (boolean moving : new boolean[]{false, true}) {
            BenchmarkSceneGuard guard = new BenchmarkSceneGuard(world, start, settings, moving);
            assertFalse(guard.accepts(new Object(), start, settings, true, false, false, true));
            assertFalse(guard.accepts(null, start, settings, true, false, false, true));
            assertFalse(guard.accepts(world, start, settings, false, false, false, true));
            assertFalse(guard.accepts(world, start, settings, true, true, false, true));
            assertFalse(guard.accepts(world, start, settings, true, false, true, true));
            assertFalse(guard.accepts(world, start, settings, true, false, false, false));
            assertFalse(guard.accepts(world, start, settings.replace("2560x1440", "640x480"), true, false, false, true));
            assertFalse(guard.accepts(world, start, settings.replace("distance=12", "distance=8"), true, false, false, true));
            assertFalse(guard.accepts(world, new BenchmarkSceneGuard.Camera(Double.NaN, 64, 2, 45, 10), settings, true, false, false, true));
        }
    }
}
