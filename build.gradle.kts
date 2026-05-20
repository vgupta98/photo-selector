import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

group = "com.vishalgupta.photoselector"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
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
