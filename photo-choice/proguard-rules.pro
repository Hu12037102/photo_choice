# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.** { *; }
-keep class com.bumptech.glide.load.engine.** { *; }
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder

# MediaFile data classes (used in Paging + serialization)
-keep class com.google.photochoice.data.model.** { *; }

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
