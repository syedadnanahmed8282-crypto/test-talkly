# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ProGuard / R8 Keep Rules for Firebase, Zego, Moshi, and Data Models
-keep class com.family.talkly.data.models.** { *; }
-keep class com.family.talkly.data.local.entity.** { *; }

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keep class im.zego.zegoexpress.** { *; }
-dontwarn im.zego.zegoexpress.**

-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

-keep class kotlinx.coroutines.** { *; }
-keep class okhttp3.** { *; }

