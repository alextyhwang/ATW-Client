plugins { id("net.weavemc.gradle") version "1.4.1" }
check(net.weavemc.gradle.util.Constants.CACHE_DIR.toPath().toAbsolutePath().normalize()
    .startsWith(layout.projectDirectory.asFile.toPath().toAbsolutePath().normalize())) {
    "Weave's Minecraft cache must stay inside this module"
}
group = "com.atw"
version = "0.1.0"
weave {
    configure {
        name = "ATW Render Boost"
        modId = "atw-render-boost"
        entryPoints = listOf("com.atw.renderboost.RenderBoostMod")
        hooks = listOf("com.atw.renderboost.hook.FrameHook", "com.atw.renderboost.hook.FrameErrorHook", "com.atw.renderboost.hook.FontHook", "com.atw.renderboost.hook.HudHook")
        mcpMappings()
    }
    version("1.8.9")
    // 1.4.1's ConfigurationBuilder does not expose compiledFor; ModConfig does.
    configuration.set(configuration.get().copy(compiledFor = "1.8.9"))
}
repositories {
    mavenCentral()
    maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
}
dependencies {
    compileOnly("net.weavemc.api:api:1.4.1")
    compileOnly("net.weavemc.api:api-v1_8:1.4.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("net.weavemc.api:api:1.4.1")
    testImplementation("org.ow2.asm:asm-util:9.9.1")
}
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    withSourcesJar()
}
tasks.withType<JavaCompile>().configureEach { options.release.set(8) }
tasks.test { useJUnitPlatform() }
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar { archiveBaseName.set("ATWRenderBoost") }
