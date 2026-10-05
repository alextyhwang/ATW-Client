package com.atw.renderboost.benchmark;

/** Game-free scene contract. Moving changes only position/yaw/pitch admission. */
public final class BenchmarkSceneGuard {
    public static final class Camera {
        public final double x, y, z;
        public final float yaw, pitch;
        public Camera(double x, double y, double z, float yaw, float pitch) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
        boolean same(Camera other) {
            return other != null && x == other.x && y == other.y && z == other.z
                    && yaw == other.yaw && pitch == other.pitch;
        }
        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Float.isFinite(yaw) && Float.isFinite(pitch);
        }
        @Override public String toString() { return x + "," + y + "," + z + "," + yaw + "," + pitch; }
    }
    private final Object world;
    private final Camera camera;
    private final String settings;
    private final boolean moving;
    public BenchmarkSceneGuard(Object world, Camera camera, String settings, boolean moving) {
        if (world == null || camera == null || !camera.finite() || settings == null)
            throw new IllegalArgumentException("Active world, finite camera and settings required");
        this.world = world; this.camera = camera; this.settings = settings; this.moving = moving;
    }
    public boolean accepts(Object currentWorld, Camera currentCamera, String currentSettings,
                           boolean focused, boolean paused, boolean guiOpen, boolean playerPresent) {
        return focused && !paused && !guiOpen && playerPresent && world == currentWorld
                && settings.equals(currentSettings) && currentCamera != null && currentCamera.finite()
                && (moving || camera.same(currentCamera));
    }
    public String mode() { return moving ? "moving" : "stationary"; }
}
