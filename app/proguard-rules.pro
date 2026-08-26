# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ========= Общие атрибуты =========

# Сохраняем информацию для читабельных stack traces
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,EnclosingMethod,InnerClasses

# ViewBinding (классы генерируются компилятором)
-keep class **.*Binding { *; }

# BuildConfig
-keep class **.BuildConfig { *; }

# Kotlin metadata (для reflection)
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.**

# Сохраняем enum-значения
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========= Firebase Models (важно!) =========
# LocationData должен быть доступен для Firebase serialization
-keep @com.google.firebase.database.IgnoreExtraProperties class * { *; }
-keep @androidx.annotation.Keep class * { *; }

-keep class ru.wizand.safeorbit.data.model.LocationData {
    <init>();
    <fields>;
    <methods>;
}

# Все Firebase data models
-keep class ru.wizand.safeorbit.data.model.** {
    <init>();
    <fields>;
}

# ========= Firebase SDK =========
-keep class com.google.firebase.database.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keepclassmembers class com.google.firebase.** {
    <init>();
}
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase GenericTypeIndicator
-keepclassmembers class * {
    *** getValue();
}

# ========= Hilt / Dagger =========
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Hilt generated classes
-keep class **_HiltModules { *; }
-keep class **_HiltComponents { *; }
-keep class **_ComponentTreeDeps { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }

-dontwarn dagger.hilt.**
-dontwarn javax.inject.**

# Защита от обфускации компонентов для DI
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }

# ========= Room Database =========
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao class * { *; }

-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# Room entities с @Keep защищены дополнительно
-keep @androidx.annotation.Keep class * {
    <init>();
    <fields>;
    <methods>;
}

-dontwarn androidx.room.**

# ========= EncryptedSharedPreferences =========
-keep class androidx.security.crypto.** { *; }
-keepclassmembers class androidx.security.crypto.** {
    <init>(...);
    <fields>;
    <methods>;
}

# ========= Yandex Maps =========
-keep class com.yandex.mapkit.** { *; }
-keep class com.yandex.runtime.** { *; }
-keep class ru.yandex.** { *; }
-dontwarn com.yandex.**

# ========= ZXing (QR Code) =========
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn com.journeyapps.**
-dontwarn com.google.zxing.**

# ========= WorkManager =========
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn androidx.work.**

# ========= Navigation Component =========
-keep class androidx.navigation.** { *; }
-keepnames class androidx.navigation.fragment.NavHostFragment

# ========= LiveData/ViewModel =========
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# ========= Coroutines =========
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ========= Serialization =========
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ========= Material Components =========
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ========= Parcelable =========
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ========= Native methods =========
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========= Application / системные компоненты =========
-keep class ru.wizand.safeorbit.MainApplication { *; }
-keep class androidx.core.content.FileProvider { *; }

# ========= Защита классов, используемых через reflection =========
-keep class ru.wizand.safeorbit.data.** { *; }
-keep class ru.wizand.safeorbit.utils.** { *; }

# ========= Optimization =========
-optimizationpasses 5
-dontusemixedcaseclassnames

-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ========= 2.3 (audit): strip debug/info logs in release =========
# d/v/i calls are removed from release bytecode; keep w/e for diagnostics.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}