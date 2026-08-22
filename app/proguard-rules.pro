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

# ── Kotlin metadata & attributes ─────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations
-keepattributes SourceFile, LineNumberTable
-dontwarn kotlinx.**

# ── ViperCode app entry points (mandatory) ───────────────────────────────────
-keep class com.vipercode.ide.MainActivity { *; }
-keep class com.vipercode.ide.ViperCodeApp { *; }

# ── BuildConfig (referenced from Compose UI) ─────────────────────────────────
-keep class com.vipercode.ide.BuildConfig { *; }

# ── Data model classes used as Compose state ─────────────────────────────────
-keep class com.vipercode.ide.data.model.** { *; }

# ── SettingsRepository + Pref + enum reflection ─────────────────────────────
# SettingsRepository.decode reads enum constants via Class.enumConstants,
# so the enum synthetic methods (values/valueOf) MUST survive R8.
-keep class com.vipercode.ide.data.prefs.** { *; }
-keepclassmembers enum com.vipercode.ide.data.prefs.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── FileRepository + RepoResult sealed class (checked via `as?` casts) ───────
-keep class com.vipercode.ide.data.repo.** { *; }

# ── util package — Language enum + Maps + SearchHit ──────────────────────────
# The `Language` enum has 100+ constants and its companion-object init
# block calls `values()` while building byExt/byMime lookup maps. R8
# must NOT rename or remove `values()` / `valueOf(String)` or the
# static-init throws ExceptionInInitializerError on launch — this was
# the root cause of "release crashes, debug works" in v0.0.9.
-keep class com.vipercode.ide.util.** { *; }
-keepclassmembers enum com.vipercode.ide.util.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Editor themes (referenced by name from SettingsRepository.preferredTheme) ─
-keep class com.vipercode.ide.ui.theme.** { *; }

# ── Command + TextTransformOp (enum / data classes used across UI) ───────────
-keep class com.vipercode.ide.ui.screens.Command { *; }
-keepclassmembers enum com.vipercode.ide.ui.components.TextTransformOp {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.vipercode.ide.ui.components.TextTransformOp { *; }

# ── All @Composable methods in the app (R8 sometimes mis-inlines lambdas) ──
-keepclassmembers class com.vipercode.ide.** {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Kotlin @Metadata annotation (sealed subtypes + enum reflection) ─────────
-keep @kotlin.Metadata class com.vipercode.ide.**

# ── DocumentFile (loaded via fromTreeUri/fromSingleUri reflection) ────────────
-keep class androidx.documentfile.provider.** { *; }

# ── DataStore preferences (proto accessor uses reflection on Preferences.Key) ─
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.core.** { *; }

# ── Kotlinx coroutines (state flow symbols used by Pref class) ──────────────
-keepclassmembers class kotlinx.coroutines.flow.** {
    public <methods>;
}
