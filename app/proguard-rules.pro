# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt

# ══════════════ Retrofit + R8 Full Mode ══════════════
# Retrofit does reflection on generic parameters and annotations.
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Retain Retrofit service method parameters
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# R8 full mode strips generic signatures from Retrofit interfaces created via Proxy.
# This rule keeps entire interfaces that have Retrofit annotations (fixes ClassCastException).
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Keep inherited Retrofit services
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# Keep generic signature of Response
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep Gson
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep data classes
-keep class com.weather.forecast.data.model.** { *; }
-keep class com.weather.forecast.data.api.** { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ══════════════ Security Hardening ══════════════

# Obfuscate aggressively — rename classes, methods, fields
-repackageclasses 'c'
-allowaccessmodification
-overloadaggressively

# Remove all Log.d, Log.v, Log.i calls in release (keep Log.w and Log.e for crash tracking)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# Remove BuildConfig.DEBUG references evaluate to false in release
-assumevalues class com.weather.forecast.BuildConfig {
    public static final boolean DEBUG return false;
}

# Keep SecurityManager intact (reflection used for signature check)
-keep class com.weather.forecast.security.SecurityManager { *; }
-keep class com.weather.forecast.security.SecurityManager$* { *; }

# Prevent decompilers from easily reading string constants
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
