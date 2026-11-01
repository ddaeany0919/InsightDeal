# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# [r8-analyzer] 릴리즈 빌드 크래시 스택트레이스 가독성 보존 및 원본 파일명 보호
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit & OkHttp
-dontwarn retrofit2.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Firebase & Google Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# [r8-analyzer] EncryptedSharedPreferences & Tink Cryptography (EncryptedPrefsManager)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Models & Network (DDAEANY0919)
-keep class com.ddaeany0919.insightdeal.models.** { *; }
-keep class com.ddaeany0919.insightdeal.network.** { *; }
-keep class com.ddaeany0919.insightdeal.data.** { *; }
-keep class com.ddaeany0919.insightdeal.local.db.** { *; }
-keep class com.ddaeany0919.insightdeal.domain.** { *; }
-keep class com.ddaeany0919.insightdeal.presentation.wishlist.** { *; }
-keep class com.ddaeany0919.insightdeal.presentation.KeywordManagerViewModel$DayDndSetting { *; }
-keep class com.ddaeany0919.insightdeal.presentation.KeywordManagerViewModel$WeekDndSettings { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.RoomOpenHelper
-dontwarn androidx.room.**

# WorkManager
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn androidx.work.**

# Coil Image Loader
-keep class io.coilkt.** { *; }
-dontwarn coil.**

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Vico Charts
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**

# Kotlinx Serialization (Release R8 난독화 시 Companion Serializer 보호)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers class * {
    static ** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
