# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities
-keep class com.casureco2.outages.data.model.** { *; }
-keep class com.casureco2.outages.data.dao.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep biweekly
-keep class net.sf.biweekly.** { *; }
