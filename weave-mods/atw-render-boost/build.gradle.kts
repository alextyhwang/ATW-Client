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
        hooks = listOf("com.atw.renderboost.hook.FrameHook", "com.atw.renderboost.hook.FrameErrorHook", "com.atw.renderboost.hook.FontHook", "com.atw.renderboost.hook.HudHook", "com.atw.renderboost.hook.TerrainHook", "com.atw.renderboost.hook.NameProbeHook", "com.atw.renderboost.hook.NameParseHook", "com.atw.renderboost.hook.NameEncodingHook", "com.atw.renderboost.hook.ForwardHoverHook", "com.atw.renderboost.hook.NamePrepHook")
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
// NamePrepCaptureTest reads this pointer beside the first capture even when
// the separately tagged full restart suite is OFF. Track only its team input.
val namePrepRestartPointer = nameCaptureRoot.parentFile.resolve("restart-capture-path.txt")
val namePrepRestartRoot = if (nameCapturesRequired) {
    check(namePrepRestartPointer.isFile) {
        "Private nameprep proof requires restart-capture-path.txt beside the first capture; populate private restart evidence or use -PnameCaptureTests=public"
    }
    val privatePath = nameProbePrivateRoot.toPath().toRealPath()
    check(namePrepRestartPointer.toPath().toRealPath().startsWith(privatePath)) {
        "Nameprep restart pointer must remain under private name-render-probe"
    }
    val target = namePrepRestartPointer.readText().trim()
    check(target.isNotEmpty()) { "Private nameprep restart pointer is empty; populate private restart evidence" }
    val root = file(target)
    check(root.toPath().toAbsolutePath().normalize().startsWith(nameCaptureRoot.parentFile.toPath().toAbsolutePath().normalize()) &&
        root.toPath().toAbsolutePath().normalize().startsWith(nameProbePrivateRoot.toPath().toAbsolutePath().normalize())) {
        "Nameprep restart inputs must remain private beside the first capture"
    }
    check(root.isDirectory) { "Private nameprep restart capture directory is missing; populate private restart evidence" }
    check(root.toPath().toRealPath().startsWith(privatePath)) {
        "Nameprep restart capture must remain under private name-render-probe"
    }
    root
} else null
val namePrepRestartTeam = namePrepRestartRoot?.let { root ->
    val teams = root.listFiles()?.filter {
        it.name.startsWith("net_minecraft_scoreboard_ScorePlayerTeam.") && it.name.endsWith(".live.class")
    }.orEmpty()
    check(teams.size == 1 && teams.single().isFile) {
        "Private nameprep restart proof requires exactly one captured ScorePlayerTeam .live.class; populate private restart evidence"
    }
    teams.single().also {
        check(it.toPath().toRealPath().startsWith(nameProbePrivateRoot.toPath().toRealPath())) {
            "Nameprep restart team input must remain under private name-render-probe"
        }
    }
}
// Optional actual NamePrepHook-entry bytes stay private and are read in place.
// Public mode excludes this gate even if the local capture happens to exist.
val namePrepIncomingMode = providers.gradleProperty("namePrepIncomingTests").orElse("auto").get()
check(namePrepIncomingMode in listOf("auto", "required", "public")) {
    "namePrepIncomingTests must be auto, required, or public"
}
val namePrepIncomingRoot = file("../../upgrade-work/pvp-experiments-20261006/live/bytecode-startup")
val namePrepIncomingFile = file(providers.gradleProperty("namePrepIncomingCaptureFile").orElse(
    namePrepIncomingRoot.resolve("ScorePlayerTeam-hookstage-f536aa77-7256-4a76-8755-377f2dfc41d3.class").absolutePath).get())
val namePrepIncomingRequired = namePrepIncomingMode == "required" ||
    (namePrepIncomingMode == "auto" && nameCapturesRequired && namePrepIncomingFile.isFile)
val namePrepIncomingLoader = file("../../runtime/weave/Weave-Loader-Agent-1.4.1.jar")
val namePrepIncomingNotes = namePrepIncomingRoot.resolve("capture-run-notes.md")
if (namePrepIncomingRequired) {
    check(nameCapturesRequired) { "Incoming nameprep proof requires private name captures; public mode excludes this gate" }
    check(namePrepIncomingFile.isFile && namePrepIncomingNotes.isFile && namePrepIncomingLoader.isFile) {
        "Incoming nameprep proof requires actual Hook-entry capture, provenance notes and pinned Weave loader"
    }
    check(namePrepIncomingFile.toPath().toAbsolutePath().normalize().startsWith(namePrepIncomingRoot.toPath().toAbsolutePath().normalize()) &&
        namePrepIncomingFile.toPath().toRealPath().startsWith(namePrepIncomingRoot.toPath().toRealPath())) {
        "Incoming nameprep capture must stay under private live/bytecode-startup and be read in place"
    }
}

tasks.test {
    inputs.property("namePrepIncomingRequired", namePrepIncomingRequired)
    if (namePrepIncomingRequired) {
        inputs.files(namePrepIncomingFile, namePrepIncomingNotes, namePrepIncomingLoader)
        systemProperty("atwboost.namePrepIncomingCapture", namePrepIncomingFile.absolutePath)
        systemProperty("atwboost.namePrepIncomingLoader", namePrepIncomingLoader.absolutePath)
    } else {
        useJUnitPlatform { excludeTags("private-nameprep-incoming") }
        doFirst { println("Nameprep actual Hook-entry proof EXCLUDED: no private incoming qualification claimed") }
    }
    inputs.file(file(".gradle/weave/client-mcp-named.jar"))
    systemProperty("atwboost.namePrepMappedJar", file(".gradle/weave/client-mcp-named.jar").absolutePath)
    providers.gradleProperty("encodingEvidenceOutput").orNull?.let { systemProperty("atwboost.encodingEvidenceOutput", it) }
    providers.gradleProperty("encodingFactoryEvidenceOutput").orNull?.let { systemProperty("atwboost.encodingFactoryEvidenceOutput", it) }
    val startupMode = providers.gradleProperty("nameCodecStartupTests").orElse("off").get()
    check(startupMode in listOf("off", "required")) { "nameCodecStartupTests must be off or required" }
    val startupRequired = startupMode == "required"
    inputs.property("nameCodecStartupRequired", startupRequired)
    if (startupRequired) {
        check(nameCaptureMode != "public") { "Startup metadata proof requires private name/codec captures" }
        val startupRoot=file("../../upgrade-work/environment-20261005/name-parse-cache/hookstage")
        check(startupRoot.listFiles()?.count { it.name.endsWith(".mismatch.properties") } == 10) { "Startup proof requires all ten actual metadata reports" }
        inputs.files(fileTree(startupRoot) { include("*.mismatch.properties") })
        systemProperty("atwboost.nameCodecStartupRoot",startupRoot.absolutePath)
    } else {
        useJUnitPlatform { excludeTags("private-name-codec-startup") }
        doFirst { println("Name codec startup metadata proof EXCLUDED") }
    }
    val codecMode = providers.gradleProperty("nameCodecCaptureTests").orElse(if (nameCaptureMode == "public") "public" else "auto").get()
    check(codecMode in listOf("auto", "required", "public")) { "nameCodecCaptureTests must be auto, required, or public" }
    val codecPointer = nameProbePrivateRoot.resolve("codec-capture-path.txt")
    val codecRequired = codecMode == "required" || (codecMode == "auto" && codecPointer.isFile)
    inputs.property("nameCodecCapturesRequired", codecRequired)
    if (codecRequired) {
        check(codecPointer.isFile) { "Missing private codec pointer" }
        val codecRoot = file(codecPointer.readText().trim())
        check(codecRoot.toPath().toAbsolutePath().normalize().startsWith(nameProbePrivateRoot.toPath().toAbsolutePath().normalize())) { "Codec inputs must remain private and read in place" }
        check(codecRoot.listFiles()?.count { it.name.endsWith(".live.class") } == 12) { "Codec proof requires twelve actual inputs including Codec interface" }
        inputs.files(fileTree(codecRoot) { include("*.live.class", "capture-report.txt") })
        val encodingGuava = file("../../build/dependencies/minecraft/lunarclient/libraries/com/google/guava/guava/17.0/guava-17.0.jar")
        check(encodingGuava.isFile) { "Private encoding parity requires the pinned package Guava 17.0 (disk fixture only)" }
        inputs.file(encodingGuava)
        // Actual-loaded factory and fresh hook metadata stay private and are read in place.
        val encodingFactoryRoot = file("../../upgrade-work/environment-20261005")
        val encodingFactoryInputs = listOf(
            encodingFactoryRoot.resolve("name-render-probe/maps-capture-20261005/com_google_common_collect_Maps.loader1.live.class"),
            encodingFactoryRoot.resolve("name-render-probe/maps-capture-20261005/capture-report.txt"),
            encodingFactoryRoot.resolve("name-encoding/hookstage/com_google_common_collect_Maps.properties"),
            file("../../upgrade-work/render-next-20261005/maps-loader-relationship.properties"))
        encodingFactoryInputs.forEach { check(it.isFile) { "Missing private encoding factory evidence: ${it.name}" } }
        inputs.files(encodingFactoryInputs)
        systemProperty("atwboost.nameCodecCaptureRoot", codecRoot.absolutePath)
    } else {
        useJUnitPlatform { excludeTags("private-name-codec-capture") }
        doFirst { println("Name codec private proof EXCLUDED: public/state tests only") }
    }
    val nameRestartMode = providers.gradleProperty("nameRestartCaptureTests").orElse("off").get()
    check(nameRestartMode in listOf("off", "required")) { "nameRestartCaptureTests must be off or required" }
    val nameRestartRequired = nameRestartMode == "required"
    inputs.property("nameRestartRequired", nameRestartRequired)
    if (nameRestartRequired) {
        check(nameCapturesRequired) { "Name restart proof requires private name captures" }
        val restartRoot = checkNotNull(namePrepRestartRoot)
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
        inputs.file(namePrepRestartPointer)
        inputs.file(checkNotNull(namePrepRestartTeam))
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
