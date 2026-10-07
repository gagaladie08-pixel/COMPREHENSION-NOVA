# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.novastats.app.**$$serializer { *; }
-keepclassmembers class com.novastats.app.** { *** Companion; }
-keepclasseswithmembers class com.novastats.app.** { kotlinx.serialization.KSerializer serializer(...); }
