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
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclasseswithmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class com.spoolpainter.app.data.remote.spoolman.SpoolmanApi { *; }
-keep class com.spoolpainter.app.data.remote.spoolman.** { *; }

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
