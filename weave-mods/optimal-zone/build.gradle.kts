plugins {
    java
    id("net.weavemc.gradle") version "1.4.1"
}

group = "com.atw"
version = "0.1.0"

weave {
    configure {
        name = "ATW's Overlay"
        modId = "atw-overlay"
        entryPoints = listOf("com.atw.optimalzone.OverlayInitializer")
        accessWideners = listOf("atwoverlay.accesswidener")
        mcpMappings()
    }
    // 1.4.1's builder does not expose compiledFor, but ModConfig supports it.
    configuration.set(configuration.get().copy(compiledFor = "1.8.9"))
    version("1.8.9")
}

repositories {
    mavenCentral()
    maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
}

dependencies {
    compileOnly("net.weavemc.api:api:1.4.1")
    compileOnly("net.weavemc.api:api-v1_8:1.4.1")
    testImplementation(files(sourceSets.main.get().compileClasspath))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
    options.release.set(17)
}

tasks.jar {
    archiveBaseName.set("ATWOverlay")
}

tasks.processResources {
    // Weave 1.4.1 generates the authoritative initializer/widener metadata.
    exclude("weave.mod.json")
}
