pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
    }
}
rootProject.name = "raw-input"

// Weave also caches Minecraft under user.home; keep that cache module-local.
System.setProperty("user.home", rootDir.resolve(".build-home").absolutePath)
