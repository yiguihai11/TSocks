# Add project specific ProGuard rules here.
# By default, the flags in this file are applied to all build types.

# Optimization settings for smaller APK size
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep Go JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Tun2Socks class and JNI methods
-keep class com.yiguihai.tun2socks.Tun2Socks { *; }
-keep class com.yiguihai.tun2socks.** { *; }

# Keep Material Design components that are actually used
-keep class com.google.android.material.** { *; }

# Remove unused Android classes
-keep class android.support.v7.app.** { *; }
-dontwarn android.support.v7.**

# If you are using external libraries, you may need to keep certain aspects
# of your code in order to use them correctly. This is especially true for
# libraries that use reflection.
