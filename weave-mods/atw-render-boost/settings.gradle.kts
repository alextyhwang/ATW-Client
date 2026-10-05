pluginManagement {
    // Gradle can restore launcher system properties inside its daemon. Set this
    // here, before Weave's Constants initializes its hard-coded user.home cache.
    System.setProperty("user.home", file(".build-home").absolutePath)
    repositories {
        gradlePluginPortal()
        maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
    }
}
rootProject.name = "atw-render-boost"
