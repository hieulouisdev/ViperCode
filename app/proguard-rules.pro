# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# v0.0.7 — removed `-keep class androidx.compose.** { *; }`.
# Compose ships its own consumer ProGuard rules; the blanket keep
# was bloating the release APK by preventing R8 from shrinking
# unused Compose code. The same applies to DataStore below.

# Kotlin metadata
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-dontwarn kotlinx.**

# Keep ViperCode app entry points
-keep class com.vipercode.ide.MainActivity { *; }
-keep class com.vipercode.ide.ViperCodeApp { *; }

# Keep BuildConfig fields referenced from Compose (version display, etc.)
-keep class com.vipercode.ide.BuildConfig { *; }

# Keep data classes used as Compose state
-keep class com.vipercode.ide.data.model.** { *; }

# Keep SettingsRepository.Pref subclasses (reflection-free, but used in singleton init)
-keep class com.vipercode.ide.data.prefs.** { *; }

# DocumentFile is referenced via reflection by AndroidX
-keep class androidx.documentfile.provider.** { *; }

