# ===================== Otimizações / R8 =====================
# Mantém anotações e assinaturas genéricas (Gson/Retrofit dependem disso)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ---------- Gson (modelos serializados) ----------
# Modelos do app: mantém campos/anotações @SerializedName
-keep class io.loja.network.** { *; }
-keep class io.loja.models.** { *; }
-keep class io.loja.network.models.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ---------- Retrofit ----------
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ---------- WebView JavaScript Interface ----------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------- ML Kit / CameraX ----------
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ---------- Kotlin / Coroutines ----------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---------- Compose ----------
-dontwarn androidx.compose.**

# ---------- Manter stacktrace legível ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
