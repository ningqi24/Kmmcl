# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Ktor
-keep class io.ktor.** { *; }

# Koin
-keep class org.koin.** { *; }

# Mokt
-keep class io.github.microutils.mokt.** { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Serializable
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
