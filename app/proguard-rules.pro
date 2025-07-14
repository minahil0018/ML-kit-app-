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


# ProGuard rules for ML Kit, Firebase, CameraX, and Room
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.** { *; }
-keep class androidx.camera.** { *; }
-keep class androidx.room.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.firebase.**
-dontwarn androidx.camera.**
-dontwarn androidx.room.**

# Prevent stripping ML Kit vision classes
-keep class com.google.mlkit.vision.** { *; }

# Prevent stripping Firebase classes
-keep class com.google.firebase.ml.modeldownloader.** { *; }
