package com.atw.renderboost.terrain;

/** Required scope proof for a future live backend. This class performs no GL or config calls. */
public final class TerrainEligibility {
    public boolean hooksVerified, contextLifetimeVerified, renderThread, compatibilityArrays,
            vaoSupported, effectiveVbo, exactBlockFormat, exactTextureUnits, expectedArrayEnables;
    public boolean regions, shaders, displayListRecording, extraArrays, nested;
    public int baselineVao, clientTextureUnit, defaultTextureUnit, currentProgram;

    public String rejection() {
        if (!hooksVerified) return "missing-or-rejected-hook";
        if (!contextLifetimeVerified) return "unverified-context-lifetime";
        if (!renderThread) return "wrong-thread";
        if (nested) return "nested-scope";
        if (regions) return "render-regions-active";
        if (shaders || currentProgram != 0) return "shader-or-custom-program-active";
        if (!effectiveVbo) return "effective-vbo-unavailable";
        if (!compatibilityArrays || !vaoSupported) return "compatibility-vao-unavailable";
        if (!exactBlockFormat || !exactTextureUnits) return "unknown-block-layout-or-texture-units";
        if (baselineVao != 0) return "foreign-vao";
        if (clientTextureUnit != defaultTextureUnit) return "foreign-client-selector";
        if (displayListRecording) return "display-list-recording";
        if (!expectedArrayEnables || extraArrays) return "unknown-enabled-arrays";
        return null;
    }
}
