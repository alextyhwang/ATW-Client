package com.atw.renderboost.terrain;

import java.util.Properties;
import java.util.*;

/** Independent, session-only default-OFF switch. No game classes or GL calls. */
public final class TerrainControl {
    private static volatile boolean requested;
    public static final int MAX_OWNED_VAOS = 4096;
    private static final Set<String> REQUIRED = new HashSet<>(Arrays.asList(
        "net/minecraft/client/renderer/VboRenderList", "net/minecraft/client/renderer/vertex/VertexBuffer",
        "net/minecraft/client/renderer/RenderGlobal", "net/minecraft/client/renderer/GlStateManager",
        "net/minecraft/client/renderer/OpenGlHelper", "net/minecraft/client/renderer/ChunkRenderContainer",
        "net/minecraft/client/renderer/chunk/RenderChunk", "net/minecraft/client/renderer/vertex/VertexFormat",
        "net/minecraft/client/renderer/vertex/VertexFormatElement", "net/minecraft/client/renderer/vertex/DefaultVertexFormats",
        "net/optifine/Config", "org/lwjgl/opengl/Display", "org/lwjgl/opengl/GLContext", "org/lwjgl/opengl/ContextGL",
        "com/moonsworth/lunar/OCCOIRIHCIHOCIOCICROCRHRHRIHCO/HHCCHRHORCRIHRIIHRIICIHHCOOCHR/CHIRCOROIOHCCIIRRCCHOIHHHIOIOH/CHIRCOROIOHCCIIRRCCHOIHHHIOIOH/CCIRCHROCOHOCHICRORRCIRCRRIOHR"));
    private static final Set<String> accepted = new HashSet<>(), rejected = new HashSet<>();
    private static volatile long revision;
    public interface RuntimeStatus {
        boolean active();
        String status();
        void export(Properties properties);
    }
    private static volatile RuntimeStatus runtime;
    public static void registerRuntime(RuntimeStatus status) { runtime = status; }
    private TerrainControl() {}
    public static boolean requested() { return requested; }
    public static void request(boolean value) { requested = value; revision++; }
    public static long revision() { return revision; }
    public static synchronized void admit(String name, boolean pass) {
        if (!REQUIRED.contains(name)) return;
        if (pass && !rejected.contains(name)) accepted.add(name);
        else { rejected.add(name); accepted.remove(name); revision++; }
    }
    public static synchronized boolean available() { return rejected.isEmpty() && accepted.containsAll(REQUIRED); }
    public static synchronized String missing() {
        Set<String> missing = new TreeSet<>(REQUIRED); missing.removeAll(accepted);
        return missing.toString();
    }
    public static boolean active() { return available() && requested && runtime != null && runtime.active(); }
    public static String status() {
        return "terrainRequested=" + (requested ? "ON" : "OFF")
                + ", terrainHooks=" + (available() ? "admitted" : "UNAVAILABLE: " + missing())
                + ", " + (runtime == null ? "terrainScope=not-observed, terrainCacheHits=0, terrainOwnedVaos=0" : runtime.status());
    }
    public static void export(Properties p) {
        p.setProperty("terrainRequested", String.valueOf(requested));
        p.setProperty("terrainAvailable", String.valueOf(available()));
        p.setProperty("terrainHooksInstalled", String.valueOf(available()));
        p.setProperty("terrainMissingHooks", missing());
        p.setProperty("terrainVaoLimit", String.valueOf(MAX_OWNED_VAOS));
        if (runtime == null) {
            p.setProperty("terrainCacheActive", "false"); p.setProperty("terrainDrawCountsObserved", "false");
            p.setProperty("terrainCacheHits", "0"); p.setProperty("terrainOwnedVaos", "0");
            p.setProperty("terrainFallback", "not-observed");
        } else runtime.export(p);
    }
}
