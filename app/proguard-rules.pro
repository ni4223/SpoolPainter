# SpoolPainter v2.0 — ProGuard / R8 rules

# Keep crash stack traces readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations (required by Hilt + kotlinx-serialization metadata).
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod

# ----- Strip android.util.Log calls in release (NFR-5) -----
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# ----- Compose -----
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ----- Hilt + KSP-generated DI -----
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class * extends androidx.lifecycle.ViewModel
-keepclassmembers class * { @javax.inject.Inject <init>(...); }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.InstallIn class *

# ----- Retrofit + OkHttp (SpoolmanApi) -----
# R8 full mode (AGP 8+ default) replaces unused proxy-only interfaces with
# null and aggressively strips generic Signature attributes from methods.
# Retrofit needs both: the interface preserved for Proxy.newProxyInstance,
# and the parameterised return / Continuation types preserved so it can
# unwrap `Response<List<SpoolmanSpool>>` from the suspend function's
# Continuation upper bound.

# From retrofit2's official consumer-rules.pro (R8 full-mode safety):
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation,allowshrinking interface <1>

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Suspend-function support: Retrofit reads the response type from the
# Continuation parameter's upper bound. Continuation must survive R8's
# generic-type stripping.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Raw Retrofit response containers Retrofit reflects on at call time.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Belt-and-suspenders: keep SpoolmanApi interface + every method signature
# intact (covers any case the conditional rule above misses).
-keep,allowobfuscation,allowshrinking interface com.spoolpainter.app.data.remote.spoolman.SpoolmanApi {
    <methods>;
}
-keep class com.spoolpainter.app.data.remote.spoolman.** { *; }

# Retrofit-adjacent noise.
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ----- Gson DTOs (reflection-based) -----
# Spoolman wire models live in domain/models/SpoolmanModels.kt and
# data/remote/spoolman/SpoolmanRequests.kt. Gson reflects into fields,
# so keep all members of these data classes.
-keep class com.spoolpainter.app.domain.models.** { *; }
-keepclassmembers class com.spoolpainter.app.domain.models.** { <fields>; }

# ----- kotlinx-serialization (Settings DataStore) -----
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.spoolpainter.app.data.local.**$$serializer { *; }
-keepclassmembers class com.spoolpainter.app.data.local.** {
    *** Companion;
}
-keepclasseswithmembers class com.spoolpainter.app.data.local.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.spoolpainter.app.data.local.Settings { *; }

# ----- Domain enums referenced in serialised JSON -----
-keepclassmembers enum com.spoolpainter.app.domain.** { *; }
-keepclassmembers enum com.spoolpainter.app.data.local.** { *; }
