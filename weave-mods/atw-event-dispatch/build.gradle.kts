plugins { id("net.weavemc.gradle") version "1.4.1" }
check(net.weavemc.gradle.util.Constants.CACHE_DIR.toPath().toAbsolutePath().normalize()
    .startsWith(layout.projectDirectory.asFile.toPath().toAbsolutePath().normalize()))
group = "com.atw"
version = "0.1.0"
weave {
    configure {
        name = "ATW Dispatch Experiment"
        modId = "atw-event-dispatch"
        entryPoints = listOf("com.atw.dispatch.DispatchMod")
        hooks = listOf("com.atw.dispatch.hook.DispatchHook")
        mcpMappings()
    }
    version("1.8.9")
    configuration.set(configuration.get().copy(compiledFor = "1.8.9"))
}
repositories {
    mavenCentral()
    maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
}
dependencies {
    compileOnly("net.weavemc.api:api:1.4.1")
    compileOnly("net.weavemc.api:api-v1_8:1.4.1")
    testImplementation("net.weavemc.api:api:1.4.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.ow2.asm:asm-util:9.9.1")
    testImplementation("org.ow2.asm:asm-commons:9.9.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
tasks.jar {
    archiveBaseName.set("ATWDispatchExperiment")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(listOf("LICENSE", "NOTICE.md")) { into("META-INF") }
}
tasks.named<Jar>("sourcesJar") {
    from("LICENSE", "NOTICE.md", "README.md", "build.gradle.kts", "settings.gradle.kts", "gradle.properties")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.test {
    dependsOn(tasks.jar)
    useJUnitPlatform()
    systemProperty("dispatch.repository", rootDir.resolve("../..").canonicalPath)
    systemProperty("dispatch.api", configurations.testRuntimeClasspath.get().files
        .single { it.name == "api-1.4.1.jar" }.absolutePath)
    systemProperty("user.home", layout.buildDirectory.dir("test-home").get().asFile.absolutePath)
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}
