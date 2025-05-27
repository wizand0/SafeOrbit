# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ========= Общие правила =========

# Оставить ViewBinding
-keep class **Binding { *; }

# Оставить BuildConfig поля
-keep class **.BuildConfig { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Сохраняем enum-значения (например, для Firebase или Room)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========= Firebase =========
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ========= Hilt =========
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-dontwarn dagger.hilt.**

# ========= Room (KSP) =========
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# ========= Yandex Maps =========
-keep class com.yandex.** { *; }
-dontwarn com.yandex.**

# ========= ZXing =========
-keep class com.journeyapps.** { *; }
-dontwarn com.journeyapps.**
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ========= Agora SDK =========
-keep class io.agora.** { *; }
-dontwarn io.agora.**

# ========= WorkManager =========
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ========= Android Navigation =========
-keep class androidx.navigation.** { *; }

# ========= LiveData/ViewModel =========
-keep class androidx.lifecycle.** { *; }

# ========= Coroutines =========
-dontwarn kotlinx.coroutines.**

# ========= Для классов, используемых через reflection =========
-keepnames class * {
    @androidx.room.Entity *;
}
-keepnames class * {
    @com.google.firebase.database.IgnoreExtraProperties *;
}
