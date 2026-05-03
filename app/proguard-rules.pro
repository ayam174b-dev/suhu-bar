# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends androidx.hilt.work.HiltWorkerFactory

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
