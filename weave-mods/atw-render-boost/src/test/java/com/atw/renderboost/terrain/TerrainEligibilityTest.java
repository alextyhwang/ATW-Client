package com.atw.renderboost.terrain;

import com.atw.renderboost.FrameErrorPolicy;
import java.util.Properties;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainEligibilityTest {
    static TerrainEligibility proven() {
        TerrainEligibility e = new TerrainEligibility();
        e.hooksVerified = e.contextLifetimeVerified = e.renderThread = e.compatibilityArrays = e.vaoSupported = true;
        e.effectiveVbo = e.exactBlockFormat = e.exactTextureUnits = e.expectedArrayEnables = true;
        return e;
    }
    static void rejects(Consumer<TerrainEligibility> mutation, String reason) {
        TerrainEligibility e = proven(); mutation.accept(e); assertEquals(reason, e.rejection());
    }
    @Test void everyMissingPrerequisiteAndUnknownStateFallsBack() {
        assertNull(proven().rejection());
        rejects(e -> e.hooksVerified = false, "missing-or-rejected-hook");
        rejects(e -> e.contextLifetimeVerified = false, "unverified-context-lifetime");
        rejects(e -> e.renderThread = false, "wrong-thread");
        rejects(e -> e.nested = true, "nested-scope");
        rejects(e -> e.regions = true, "render-regions-active");
        rejects(e -> e.shaders = true, "shader-or-custom-program-active");
        rejects(e -> e.currentProgram = 2, "shader-or-custom-program-active");
        rejects(e -> e.effectiveVbo = false, "effective-vbo-unavailable");
        rejects(e -> e.compatibilityArrays = false, "compatibility-vao-unavailable");
        rejects(e -> e.vaoSupported = false, "compatibility-vao-unavailable");
        rejects(e -> e.exactBlockFormat = false, "unknown-block-layout-or-texture-units");
        rejects(e -> e.exactTextureUnits = false, "unknown-block-layout-or-texture-units");
        rejects(e -> e.baselineVao = 2, "foreign-vao");
        rejects(e -> e.clientTextureUnit = 1, "foreign-client-selector");
        rejects(e -> e.displayListRecording = true, "display-list-recording");
        rejects(e -> e.expectedArrayEnables = false, "unknown-enabled-arrays");
        rejects(e -> e.extraArrays = true, "unknown-enabled-arrays");
    }
    @Test void terrainSwitchCannotEnableUnavailableCacheOrChangeFrameErrorSwitch() {
        assertFalse(TerrainControl.requested());
        String before = FrameErrorPolicy.mode();
        try {
            TerrainControl.request(true); assertTrue(TerrainControl.requested());
            assertFalse(TerrainControl.available()); assertFalse(TerrainControl.active());
            assertEquals(before, FrameErrorPolicy.mode());
            assertTrue(TerrainControl.status().contains("UNAVAILABLE"));
            Properties p = new Properties(); TerrainControl.export(p);
            assertEquals("true", p.getProperty("terrainRequested"));
            assertEquals("false", p.getProperty("terrainCacheActive"));
            assertEquals("false", p.getProperty("terrainHooksInstalled"));
            assertEquals("false", p.getProperty("terrainDrawCountsObserved"));
        } finally { TerrainControl.request(false); }
    }
}
