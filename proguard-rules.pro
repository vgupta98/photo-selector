# ProGuard keep-rules for the minified release DMG (`./gradlew packageReleaseDmg`).
#
# Compose Desktop's release build tree-shakes the whole classpath in one pass,
# which is the win - but it also strips anything only reached via reflection,
# JNI, or generated code. Those entry points are invisible to the static
# reachability analysis, so each one needs an explicit keep below or the DMG
# builds clean and then misbehaves at runtime. See CLAUDE.md "Known gotchas"
# ("The release DMG is ProGuard-minified").
#
# We do NOT obfuscate (build.gradle.kts sets obfuscate=false) so stack traces
# stay readable; the size win is pure shrinking. Compose's own reflection keeps
# are contributed automatically by the Compose Gradle plugin's default rules -
# only the app-specific native/codegen entry points live here.

# --- ONNX Runtime (bundled native, reached via JNI + reflection) -------------
# OnnxEmbeddingModel loads ai.onnxruntime.* and the runtime calls back into JNI
# native methods. Stripping any of this does NOT crash - OnnxEmbeddingModel is
# fail-soft and silently falls back to the classical embedder, quietly degrading
# the Similarity lens. So keep the whole package and its native method bindings.
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# --- JNA (HEIC/RAW decode bridge, reached reflectively by JNA proxies) --------
# JNA maps each Library interface method to a native symbol and reads each
# Structure's fields by reflection in @FieldOrder order. Keep JNA itself plus
# every mapped interface/structure - our macOS ImageIO bridge lives in this
# package (Library interfaces + the CGRect Structure and its ByValue subtype).
-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-keep class * implements com.sun.jna.Library { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class com.vishalgupta.photoselector.data.format.macos.** { *; }

# --- kotlinx.serialization (codegen-based, not reflection) --------------------
# The categories file, browse position, app prefs and grouping-result cache all
# round-trip through generated $$serializer classes. Strip them and a user's
# favourites/categories silently fail to read on relaunch. Keep the generated
# serializers and the Companion serializer() accessors for our @Serializable DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.vishalgupta.photoselector.**$$serializer { *; }
-keepclassmembers class com.vishalgupta.photoselector.** {
    *** Companion;
}
-keepclasseswithmembers class com.vishalgupta.photoselector.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Adequate only while every DTO stays primitives/List; an enum or polymorphic
# @Serializable needs its entries/subtypes kept too, else it reads back silently
# empty on relaunch (e.g. lost favourites).

# --- Skiko (Compose Desktop's rendering native loader) -----------------------
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-dontwarn org.jetbrains.skia.**
-dontwarn org.jetbrains.skiko.**

# --- Coroutines --------------------------------------------------------------
# No keep needed: the app reaches Dispatchers.Swing directly (a static reference
# the shrinker sees), never Dispatchers.Main / its ServiceLoader factory, and
# Compose's default rules cover its own coroutine use. Just suppress the warnings
# for coroutines' optional/absent references.
-dontwarn kotlinx.coroutines.**

# --- App entry point ---------------------------------------------------------
-keep class com.vishalgupta.photoselector.MainKt { *; }
