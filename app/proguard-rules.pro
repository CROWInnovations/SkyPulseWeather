# Keep Kotlinx Serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.crowinnovations.skypulseweather.**$$serializer { *; }
-keepclassmembers class com.crowinnovations.skypulseweather.** {
    *** Companion;
}
-keepclasseswithmembers class com.crowinnovations.skypulseweather.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit2 & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Glance AppWidget
-keep class com.crowinnovations.skypulseweather.widget.** { *; }
-keep class androidx.glance.** { *; }
