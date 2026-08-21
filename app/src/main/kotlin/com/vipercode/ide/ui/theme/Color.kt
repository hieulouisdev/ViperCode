package com.vipercode.ide.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — derived directly from the ViperCode logo (logoide.png).
// Background navy: #0F1525, brand blue accent: #4089F6, lighter accent: #6BA0FA.
val ViperDark = Color(0xFF0F1525)
val ViperSurface = Color(0xFF161D2F)
val ViperSurfaceVariant = Color(0xFF1F2740)
val ViperAccent = Color(0xFF4089F6)
val ViperAccentLight = Color(0xFF6BA0FA)
val ViperOnDark = Color(0xFFE8ECF4)
val ViperOnLight = Color(0xFF0F1525)

// Dark scheme — tuned for late-night code sessions.
val DarkColorPalette = darkColorScheme(
    primary = ViperAccent,
    onPrimary = Color.White,
    primaryContainer = ViperSurfaceVariant,
    onPrimaryContainer = ViperAccentLight,
    secondary = Color(0xFF7B8CF6),
    onSecondary = Color.White,
    background = ViperDark,
    onBackground = ViperOnDark,
    surface = ViperSurface,
    onSurface = ViperOnDark,
    surfaceVariant = ViperSurfaceVariant,
    onSurfaceVariant = Color(0xFFB7BED2),
    outline = Color(0xFF3A4566),
    outlineVariant = Color(0xFF2A3354),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
)

// Light scheme — high contrast, paper-like.
val LightColorPalette = lightColorScheme(
    primary = ViperAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF0E2A55),
    secondary = Color(0xFF4F66C9),
    onSecondary = Color.White,
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF0F1525),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1525),
    surfaceVariant = Color(0xFFE7EAF1),
    onSurfaceVariant = Color(0xFF3F4658),
    outline = Color(0xFFB0BAD0),
    outlineVariant = Color(0xFFD3D9E6),
    error = Color(0xFFC53B3B),
    onError = Color.White,
)
