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
        hooks = listOf("com.atw.renderboost.hook.FrameHook", "com.atw.renderboost.hook.FrameErrorHook", "com.atw.renderboost.hook.FontHook", "com.atw.renderboost.hook.HudHook", "com.atw.renderboost.hook.TerrainHook", "com.atw.renderboost.hook.NameProbeHook")
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
// Private captured bytes are never published. Public/state tests always run.
val terrainFixtureNames = listOf(
    "com_moonsworth_lunar_OCCOIRIHCIHOCIOCICROCRHRHRIHCO_HHCCHRHORCRIHRIIHRIICIHHCOOCHR_CHIRCOROIOHCCIIRRCCHOIHHHIOIOH_CHIRCOROIOHCCIIRRCCHOIHHHIOIOH_CCIRCHROCOHOCHICRORRCIRCRRIOHR.loader1.live.class.dat",
    "net_minecraft_client_renderer_chunk_RenderChunk.loader1.live.class.dat",
    "net_minecraft_client_renderer_ChunkRenderContainer.loader2.live.class.dat",
    "net_minecraft_client_renderer_GlStateManager.loader4.live.class.dat",
    "net_minecraft_client_renderer_OpenGlHelper.loader3.live.class.dat",
    "net_minecraft_client_renderer_RenderGlobal.loader3.live.class.dat",
    "net_minecraft_client_renderer_VboRenderList.loader2.live.class.dat",
    "net_minecraft_client_renderer_vertex_DefaultVertexFormats.loader10.live.class.dat",
    "net_minecraft_client_renderer_vertex_VertexBuffer.loader1.live.class.dat",
    "net_minecraft_client_renderer_vertex_VertexFormat.loader9.live.class.dat",
    "net_minecraft_client_renderer_vertex_VertexFormatElement.loader8.live.class.dat",
    "net_optifine_Config.loader7.live.class.dat",
    "org_lwjgl_opengl_ContextGL.loader4.live.class.dat",
    "org_lwjgl_opengl_Display.loader6.live.class.dat",
    "org_lwjgl_opengl_GLContext.loader5.live.class.dat",
) + listOf("CCIRCHROCOHOCHICRORRCIRCRRIOHR", "ChunkRenderContainer", "RenderChunk",
    "GlStateManager", "OpenGlHelper", "RenderGlobal", "VboRenderList", "DefaultVertexFormats",
    "VertexBuffer", "VertexFormat", "VertexFormatElement", "Config", "ContextGL", "Display", "GLContext")
    .map { "hookstage/$it.hook-input.class.dat" }
val terrainCaptureMode = providers.gradleProperty("terrainCaptureTests").orElse("auto").get()
check(terrainCaptureMode in listOf("auto", "required", "public")) {
    "terrainCaptureTests must be auto, required, or public"
}
val terrainPresent = terrainFixtureNames.count {
    file("src/test/resources/terrain/$it").isFile
}
val terrainCapturesRequired = terrainCaptureMode == "required" ||
    (terrainCaptureMode == "auto" && terrainPresent > 0)
if (terrainCapturesRequired) check(terrainPresent == terrainFixtureNames.size) {
    "Private terrain acceptance requires all 30 fixtures ($terrainPresent present); see fixture README"
}
// Additional cross-startup proof reads four root-owned private captures in place.
// Keep the original 30 fixtures and their exact ignore entries unchanged.
val terrainRestartCaptureMode = providers.gradleProperty("terrainRestartCaptureTests").orElse("off").get()
check(terrainRestartCaptureMode in listOf("off", "required")) {
    "terrainRestartCaptureTests must be off or required"
}
val terrainRestartsRequired = terrainRestartCaptureMode == "required"
val terrainRestartRoot = file("../../upgrade-work/environment-20261005")
if (terrainRestartsRequired) {
    check(terrainCapturesRequired) { "Restart capture proof requires the full private terrain suite" }
    for (directory in listOf("terrain-hookstage", "terrain-restart-evidence/first-start"))
        for (name in listOf("Config", "RenderGlobal"))
            check(terrainRestartRoot.resolve("$directory/$name.hook-input.class").isFile) {
                "Missing private restart capture: $directory/$name"
            }
}
val nameCaptureMode = providers.gradleProperty("nameCaptureTests").orElse(
    if (terrainCaptureMode == "public") "public" else "auto").get()
check(nameCaptureMode in listOf("auto", "required", "public")) { "nameCaptureTests must be auto, required, or public" }
val nameProbePrivateRoot = file("../../upgrade-work/environment-20261005/name-render-probe")
val nameLatest = nameProbePrivateRoot.resolve("latest-capture-path.txt")
val nameCaptureRoot = if (nameLatest.isFile) file(nameLatest.readText().trim()) else nameProbePrivateRoot.resolve("absent")
val nameCapturesRequired = nameCaptureMode == "required" || (nameCaptureMode == "auto" && nameLatest.isFile)
if (nameCapturesRequired) {
    check(nameCaptureRoot.toPath().toAbsolutePath().normalize().startsWith(nameProbePrivateRoot.toPath().toAbsolutePath().normalize())) {
        "Name capture tests must read private inputs in place under name-render-probe"
    }
    check(nameCaptureRoot.listFiles()?.count { it.name.endsWith(".live.class") } == 11) {
        "Private name probe proof requires all eleven actual captured inputs"
    }
}
tasks.test {
    val nameRestartMode = providers.gradleProperty("nameRestartCaptureTests").orElse("off").get()
    check(nameRestartMode in listOf("off", "required")) { "nameRestartCaptureTests must be off or required" }
    val nameRestartRequired = nameRestartMode == "required"
    inputs.property("nameRestartRequired", nameRestartRequired)
    if (nameRestartRequired) {
        check(nameCapturesRequired) { "Name restart proof requires private name captures" }
        val pointer = nameProbePrivateRoot.resolve("restart-capture-path.txt")
        check(pointer.isFile) { "Missing private name restart pointer" }
        val restartRoot = file(pointer.readText().trim())
        check(restartRoot.toPath().toAbsolutePath().normalize().startsWith(nameProbePrivateRoot.toPath().toAbsolutePath().normalize())) { "Restart inputs must remain private" }
        check(restartRoot.listFiles()?.count { it.name.endsWith(".live.class") } == 11) { "Name restart proof requires eleven inputs" }
        inputs.files(fileTree(restartRoot) { include("*.live.class", "capture-report.txt") })
        systemProperty("atwboost.nameRestartCaptureRoot", restartRoot.absolutePath)
    } else {
        useJUnitPlatform { excludeTags("private-name-restart") }
        doFirst { println("Name restart capture proof EXCLUDED: no cross-startup proof claimed") }
    }
    inputs.property("nameCapturesRequired", nameCapturesRequired)
    if (nameCapturesRequired) {
        inputs.files(fileTree(nameCaptureRoot) { include("*.live.class", "capture-report.txt") })
        systemProperty("atwboost.nameCaptureRoot", nameCaptureRoot.absolutePath)
    }
    inputs.property("terrainCapturesRequired", terrainCapturesRequired)
    inputs.property("terrainRestartsRequired", terrainRestartsRequired)
    if (terrainRestartsRequired) {
        for (directory in listOf("terrain-hookstage", "terrain-restart-evidence/first-start"))
            for (name in listOf("Config", "RenderGlobal"))
                inputs.file(terrainRestartRoot.resolve("$directory/$name.hook-input.class"))
        systemProperty("atwboost.terrainRestartCaptureRoot", terrainRestartRoot.absolutePath)
    }
    useJUnitPlatform {
        if (!terrainCapturesRequired) excludeTags("private-terrain-capture")
        if (!terrainRestartsRequired) excludeTags("private-terrain-restart-capture")
        if (!nameCapturesRequired) excludeTags("private-name-capture")
    }
    doFirst {
        logger.lifecycle(if (nameCapturesRequired) "Name private capture proof ENABLED: eleven inputs read in place"
            else "Name private capture proof EXCLUDED: public executable/state tests only")
        logger.lifecycle(if (terrainCapturesRequired)
            "Terrain private capture acceptance ENABLED: all 30 local fixtures present"
        else "Terrain private capture acceptance EXCLUDED: public/state tests only; no actual-capture validation claimed")
    }
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar { archiveBaseName.set("ATWRenderBoost") }
