# --- Moshi ---------------------------------------------------------------------
# Generated adapters are looked up by name, so the DTO package must survive obfuscation.
-keep class pro.bixplayer.player.data.api.dto.** { *; }
-keep class **JsonAdapter { <init>(...); *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keepnames @com.squareup.moshi.JsonClass class *

# --- Retrofit -------------------------------------------------------------------
# Generic signatures are needed to resolve the return type of suspend functions.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# --- OkHttp / Okio ----------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Media3 ------------------------------------------------------------------------
# Extractors and renderers are instantiated reflectively by name.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Room ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# --- Misc ----------------------------------------------------------------------------
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
