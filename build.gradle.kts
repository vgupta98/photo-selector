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
version = "1.3.0"

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
            packageName = "PhotoSelector"
            packageVersion = project.version.toString()
            description = "Browse, favourite and export wedding photos."
            modules("java.desktop", "java.naming", "jdk.unsupported")

            macOS {
                bundleID = "com.vishalgupta.photoselector"
                dockName = "Photo Selector"
                appCategory = "public.app-category.photography"
            }
        }
    }
}
