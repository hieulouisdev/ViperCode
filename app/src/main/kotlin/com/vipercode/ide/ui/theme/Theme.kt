package com.vipercode.ide.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.vipercode.ide.data.prefs.SettingsRepository

/**
 * ViperCode theme root.
 *
 * Wraps the rest of the UI with the active colour scheme, typography and
 * component shapes. Honours three sources of theme truth:
 *  - user preference ([SettingsRepository.themeMode])
 *  - system override (Dynamic Color on Android 12+)
 *  - fallback brand palette for older devices
 */
@Composable
fun ViperCodeTheme(
    themeMode: SettingsRepository.ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        SettingsRepository.ThemeMode.SYSTEM -> systemDark
        SettingsRepository.ThemeMode.DARK -> true
        SettingsRepository.ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        isDark -> DarkColorPalette
        else -> LightColorPalette
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ViperTypography,
        shapes = ViperShapes,
        content = content,
    )
}

/**
 * Unwraps a Context chain (ContextThemeWrapper → Activity) so we can
 * safely fetch the host window without a fragile `as Activity` cast
 * that crashes if the Compose hierarchy is hosted inside a non-Activity
 * context (e.g., preview surfaces or embedded views).
 */
private fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}
