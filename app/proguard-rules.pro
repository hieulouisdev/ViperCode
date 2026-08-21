# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Compose runtime metadata
-keep class androidx.compose.** { *; }

# Kotlin metadata
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-dontwarn kotlinx.**

# Keep ViperCode app entry points
-keep class com.vipercode.ide.MainActivity { *; }
-keep class com.vipercode.ide.ViperCodeApp { *; }
