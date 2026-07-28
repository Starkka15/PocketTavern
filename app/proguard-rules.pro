# Tink (transitive via security-crypto) uses errorprone annotations at compile-time only
-dontwarn com.google.errorprone.annotations.Immutable

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.stark.sillytavern.**$$serializer { *; }
-keepclassmembers class com.stark.sillytavern.** {
    *** Companion;
}
-keepclasseswithmembers class com.stark.sillytavern.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- On-device inference (JNI) ----
# LiteRT-LM and llamatik bind to Kotlin/Java classes & methods BY NAME from native code.
# R8 must not rename or strip them, or the native side fails to bind (release-only crash —
# debug works because it doesn't minify). Fixes on-device crash in release builds.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keep class com.google.ai.edge.litertlm.** { *; }
-keep interface com.google.ai.edge.litertlm.** { *; }
-keep class com.llamatik.** { *; }
-keep interface com.llamatik.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.llamatik.**
