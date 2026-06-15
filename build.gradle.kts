import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.compose)
    alias(libs.plugins.jmh)
}

group = "com.vishalgupta.photoselector"
version = "1.5.0"

kotlin {
    jvmToolchain(17)
}

// Compose compiler stability reports. Off by default (zero build cost); enable
// with `-PcomposeReports=true` to dump *-composables.txt / *-classes.txt under
// build/compose_compiler/. Inspect those to spot a composable that can't skip
// or a param/class that turned unstable. See CLAUDE.md "Recomposition checks".
composeCompiler {
    if (project.findProperty("composeReports") == "true") {
        val dir = layout.buildDirectory.dir("compose_compiler")
        reportsDestination = dir
        metricsDestination = dir
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    // HEIC decode bridges into the macOS ImageIO/CoreGraphics system frameworks via JNA.
    // No native libs are bundled (system frameworks are loaded by name); JNA ships its own
    // jnidispatch. The decode path sits behind PhotoDecoder so a future Windows build adds
    // its own decoder rather than replacing this.
    implementation(libs.jna)

    // ONNX Runtime powers the learned visual-similarity embedder (OnnxEmbeddingModel). The JAR
    // bundles the JNI native library for every desktop platform, so it works behind the
    // EmbeddingModel interface with no per-OS wiring; the model blob itself ships as a resource.
    implementation(libs.onnxruntime)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(compose.desktop.uiTestJUnit4)

    // JMH (perf benchmarks under `src/jmh/kotlin/`)
    jmh(libs.jmh.core)
    jmh(libs.jmh.annprocess)
    "kaptJmh"(libs.jmh.annprocess)
}

jmh {
    resultFormat = "JSON"
    resultsFile = layout.buildDirectory.file("jmh/results.json")
    includeTests = false
    // Skiko needs the Metal renderApi hint on macOS; the rest of the JVM args
    // mirror the production application config to keep numbers comparable.
    jvmArgsAppend = listOf(
        "-Dskiko.renderApi=METAL",
        "-Dapple.awt.application.appearance=system",
        "-Dfile.encoding=UTF-8",
    )
}

compose.desktop {
    application {
        mainClass = "com.vishalgupta.photoselector.MainKt"

        jvmArgs += listOf(
            "-Xmx2g",
            "-XX:MaxMetaspaceSize=256m",
            "-Dapple.awt.application.appearance=system",
            "-Dskiko.renderApi=METAL",
            "-Dfile.encoding=UTF-8",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Rhenium"
            packageVersion = project.version.toString()
            description = "Browse, favourite and export wedding photos."
            copyright = "Copyright (c) 2026 Vishal Gupta"
            modules("java.desktop", "java.naming", "jdk.unsupported")

            macOS {
                bundleID = "com.vishalgupta.photoselector"
                appCategory = "public.app-category.photography"
                iconFile.set(project.file("src/main/resources/icon/AppIcon.icns"))
            }
        }
    }
}
