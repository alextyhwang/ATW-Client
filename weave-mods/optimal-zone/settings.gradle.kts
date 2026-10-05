pluginManagement {
    // Initialize Weave's Minecraft cache inside this module before loading it.
    System.setProperty("user.home", file(".build-home").absolutePath)
    repositories {
        maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
        gradlePluginPortal()
    }
}

rootProject.name = "atw-overlay"
