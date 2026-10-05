plugins {
    id("net.weavemc.gradle") version "1.4.1"
}
group = "com.atw.ports"
version = "1.0.1-atw-weave1.4.1"
weave {
    configure {
        name = "RawInput"
        modId = "rawinput"
        entryPoints = listOf("com.github.koxx12dev.RawInputInitializer")
        hooks = listOf()
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
    testImplementation("net.weavemc.api:api-v1_8:1.4.1")
    testImplementation(files(sourceSets.main.get().compileClasspath))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.ow2.asm:asm-util:9.9.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}
tasks.jar {
    archiveBaseName.set("RawInput")
    exclude("atw-levelhead-local.properties")
}
tasks.processResources {
    // The plugin generates the authoritative 1.4.1 metadata from weave.configure.
    exclude("weave.mod.json", "atw-levelhead-local.properties")
}
tasks.test {
    useJUnitPlatform()
    // Keep config/cache writes from tests inside this staged module.
    systemProperty("user.home", layout.buildDirectory.dir("test-home").get().asFile.absolutePath)
}

tasks.named<Jar>("sourcesJar") {
    exclude("atw-levelhead-local.properties")
}
