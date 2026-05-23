plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    application
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "dev.nlpplayground"
version = project.findProperty("version") as String

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.nlpplayground.ApplicationKt")
}

val ktorVersion = "3.5.0"
val kotestVersion = "6.1.11"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml-jvm:$ktorVersion")

    // Logging backend
    implementation("ch.qos.logback:logback-classic:1.5.32")

    // Serialization (transitively included by Ktor + Mosaic; kept explicit for clarity)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // NLP pipeline — sister projects via JitPack
    // Mosaic transitively brings Tessera, but we pin Tessera explicitly so consumers see the version.
    implementation("com.github.HectorIFC:tessera:v0.0.7")
    implementation("com.github.HectorIFC:mosaic:v0.0.4")

    // Tests
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}

detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

ktlint {
    version.set("1.4.1")
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ----------------------------------------------------------------------
// Pretrainer: tooling source set used only at dev time to regenerate the
// committed `pretrained/*` artifacts. Kept out of the production JAR.
// Run with: ./gradlew runPretrain
// ----------------------------------------------------------------------

sourceSets {
    create("pretrainer") {
        kotlin.srcDir("src/pretrainer/kotlin")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

configurations {
    named("pretrainerImplementation") { extendsFrom(configurations.implementation.get()) }
    named("pretrainerRuntimeOnly") { extendsFrom(configurations.runtimeOnly.get()) }
}

tasks.register<JavaExec>("runPretrain") {
    group = "playground"
    description = "Regenerates the bundled pre-trained corpora (Alice, Shakespeare, Kotlin stdlib KDocs)."
    classpath = sourceSets["pretrainer"].runtimeClasspath
    mainClass.set("dev.nlpplayground.pretrainer.PretrainerKt")
    // Default: write into the canonical resources/pretrained dir; override with -PoutDir=... if needed.
    val outDir = (project.findProperty("outDir") as String?) ?: "src/main/resources/pretrained"
    args(outDir)
}
