# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ProGuard / R8 Keep Rules for Data Models & Entities
-keep class com.family.talkly.data.models.** { *; }
-keep class com.family.talkly.data.local.entity.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Agora RTC SDK Keep Rules
-keep class io.agora.** { *; }
-dontwarn io.agora.**

# Supabase SDK Keep Rules
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Moshi & Retrofit
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Coroutines & OkHttp
-keep class kotlinx.coroutines.** { *; }
-keep class okhttp3.** { *; }

