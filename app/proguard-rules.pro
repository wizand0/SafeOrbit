###############################################
##  ОБЩИЕ РЕКОМЕНДУЕМЫЕ ПРАВИЛА
###############################################

# Сохраняем имена ViewBinding-классов
-keep class **Binding { *; }

# BuildConfig должен сохраняться (важно для API-ключей)
-keep class **.BuildConfig { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Сохраняем enum (используются Firebase, Room, MapKit)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


###############################################
##  HILT / Dagger
###############################################
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-dontwarn dagger.hilt.**

# Сохраняем Application (очень важно!)
-keep class ru.wizand.safeorbit.MainApplication { *; }

# Сохраняем DI-модули
-keep class ru.wizand.safeorbit.di.** { *; }


###############################################
##  FIREBASE
###############################################

# Библиотеки Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Модели (Firebase Database)
-keepclassmembers class ru.wizand.safeorbit.data.model.** {
    <fields>;
    <methods>;
}

# Сохраняем IgnoreExtraProperties
-keepnames class * {
    @com.google.firebase.database.IgnoreExtraProperties *;
}


###############################################
##  ROOM
###############################################
-keep class androidx.room.** { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**

-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}


###############################################
##  ANDROID ARCHITECTURE COMPONENTS
###############################################
# ViewModel
-keep class androidx.lifecycle.** { *; }
-keep class ru.wizand.safeorbit.**ViewModel { *; }

# SavedState
-keep class androidx.lifecycle.savedstate.** { *; }

# LiveData
-keep class androidx.lifecycle.LiveData { *; }


###############################################
##  NAVIGATION
###############################################
-keep class androidx.navigation.** { *; }


###############################################
##  WORKMANAGER
###############################################
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**


###############################################
##  COROUTINES
###############################################
-dontwarn kotlinx.coroutines.**


###############################################
##  ZXING QR
###############################################
-keep class com.journeyapps.** { *; }
-dontwarn com.journeyapps.**
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**



###############################################
##  YANDEX MAPKIT
###############################################

# Сохраняем ВСЁ MapKit – эта SDK активно использует JNI
-keep class com.yandex.** { *; }
-dontwarn com.yandex.**

# Иногда требуется:
-keep class ru.yandex.** { *; }


###############################################
##  ANDROID SYSTEM
###############################################
# Для FileProvider
-keep class androidx.core.content.FileProvider { *; }

# Services / Receivers
-keep class ru.wizand.safeorbit.presentation.server.LocationService { *; }
-keep class ru.wizand.safeorbit.presentation.server.audio.AudioBroadcastService { *; }
-keep class ru.wizand.safeorbit.presentation.client.audio.AudioStreamPlayerService { *; }

-keep class ru.wizand.safeorbit.presentation.server.BootReceiver { *; }
-keep class ru.wizand.safeorbit.device.MyDeviceAdminReceiver { *; }
-keep class ru.wizand.safeorbit.presentation.server.ActivityReceiver { *; }


###############################################
##  KEEP REFLECTION-USING CLASSES
###############################################

# Сохраняем модели, которые создаются через Firebase/JSON
-keep class ru.wizand.safeorbit.data.** { *; }

# Сохраняем UTILS, которые дергаются рефлексией
-keep class ru.wizand.safeorbit.utils.** { *; }


###############################################
##  ОТЛАДКА (если нужно)
###############################################
# Для удобства
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
