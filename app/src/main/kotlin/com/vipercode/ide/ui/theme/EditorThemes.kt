package com.vipercode.ide.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * v0.0.9 — SUPER UPDATE: curated palette of editor color themes.
 *
 * Each [EditorTheme] exposes the colors the syntax highlighter uses,
 * plus a background + foreground for the editor surface. The user
 * picks one from the new Themes screen; the active theme is bound
 * into [com.vipercode.ide.ui.components.SyntaxHighlighter.Palette]
 * at composition time.
 *
 * Themes included (13 in total):
 *  - ViperCode default (dark + light — already shipped)
 *  - Dracula (the iconic dark purple/pink)
 *  - Monokai Pro (the original rainbow)
 *  - Solarized Light + Dark (Ethan Schoonover's classic)
 *  - GitHub Light + Dark (the "official" dev look)
 *  - One Dark (Atom's signature)
 *  - Material Theme (the VS Code plugin's default palette)
 *  - Nord (Arctic, north-bluish)
 *  - Gruvbox (retro groove)
 *  - Tokyo Night (muted dark blue)
 *  - Catppuccin Mocha (soft pastel)
 */
data class EditorTheme(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val background: Color,
    val foreground: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val function: Color,
    val type: Color,
    val identifier: Color,
)

object EditorThemes {

    val ViperDefaultDark = EditorTheme(
        id = "viper_default_dark",
        displayName = "ViperCode Default",
        isDark = true,
        background = Color(0xFF0D1117),
        foreground = Color(0xFFEEFFFF),
        keyword = Color(0xFF82AAFF),
        string = Color(0xFFC3E88D),
        number = Color(0xFFF78C6C),
        comment = Color(0xFF697098),
        annotation = Color(0xFFFFCB6B),
        function = Color(0xFF82B1FF),
        type = Color(0xFFFFB62C),
        identifier = Color(0xFFEEFFFF),
    )

    val ViperDefaultLight = EditorTheme(
        id = "viper_default_light",
        displayName = "ViperCode Default (Light)",
        isDark = false,
        background = Color(0xFFFAFAFA),
        foreground = Color(0xFF1A1A1A),
        keyword = Color(0xFF1E88E5),
        string = Color(0xFF2E7D32),
        number = Color(0xFFE65100),
        comment = Color(0xFF757575),
        annotation = Color(0xFF8E6F00),
        function = Color(0xFF0D47A1),
        type = Color(0xFFB85C00),
        identifier = Color(0xFF1A1A1A),
    )

    val Dracula = EditorTheme(
        id = "dracula",
        displayName = "Dracula",
        isDark = true,
        background = Color(0xFF282A36),
        foreground = Color(0xFFF8F8F2),
        keyword = Color(0xFFFF79C6),
        string = Color(0xFFF1FA8C),
        number = Color(0xFFBD93F9),
        comment = Color(0xFF6272A4),
        annotation = Color(0xFF50FA7B),
        function = Color(0xFF50FA7B),
        type = Color(0xFFFF79C6),
        identifier = Color(0xFFF8F8F2),
    )

    val Monokai = EditorTheme(
        id = "monokai",
        displayName = "Monokai Pro",
        isDark = true,
        background = Color(0xFF2D2A2E),
        foreground = Color(0xFFFCFCFA),
        keyword = Color(0xFFFF6188),
        string = Color(0xFFFFD866),
        number = Color(0xFFAB9DF2),
        comment = Color(0xFF727072),
        annotation = Color(0xFF78DCE8),
        function = Color(0xFFA9DC76),
        type = Color(0xFFFC9867),
        identifier = Color(0xFFFCFCFA),
    )

    val SolarizedLight = EditorTheme(
        id = "solarized_light",
        displayName = "Solarized Light",
        isDark = false,
        background = Color(0xFFFDF6E3),
        foreground = Color(0xFF657B83),
        keyword = Color(0xFF859900),
        string = Color(0xFF2AA198),
        number = Color(0xFFD33682),
        comment = Color(0xFF93A1A1),
        annotation = Color(0xFFB58900),
        function = Color(0xFF268BD2),
        type = Color(0xFFB58900),
        identifier = Color(0xFF657B83),
    )

    val SolarizedDark = EditorTheme(
        id = "solarized_dark",
        displayName = "Solarized Dark",
        isDark = true,
        background = Color(0xFF002B36),
        foreground = Color(0xFF839496),
        keyword = Color(0xFF859900),
        string = Color(0xFF2AA198),
        number = Color(0xFFD33682),
        comment = Color(0xFF586E75),
        annotation = Color(0xFFB58900),
        function = Color(0xFF268BD2),
        type = Color(0xFFB58900),
        identifier = Color(0xFF93A1A1),
    )

    val GithubLight = EditorTheme(
        id = "github_light",
        displayName = "GitHub Light",
        isDark = false,
        background = Color(0xFFFFFFFF),
        foreground = Color(0xFF24292F),
        keyword = Color(0xFFFF7B72),
        string = Color(0xFF0A3069),
        number = Color(0xFF0550AE),
        comment = Color(0xFF6E7781),
        annotation = Color(0xFF953800),
        function = Color(0xFF8250DF),
        type = Color(0xFF953800),
        identifier = Color(0xFF24292F),
    )

    val GithubDark = EditorTheme(
        id = "github_dark",
        displayName = "GitHub Dark",
        isDark = true,
        background = Color(0xFF0D1117),
        foreground = Color(0xFFC9D1D9),
        keyword = Color(0xFFFF7B72),
        string = Color(0xFFA5D6FF),
        number = Color(0xFF79C0FF),
        comment = Color(0xFF8B949E),
        annotation = Color(0xFFD2A8FF),
        function = Color(0xFFD2A8FF),
        type = Color(0xFFFFA657),
        identifier = Color(0xFFC9D1D9),
    )

    val OneDark = EditorTheme(
        id = "one_dark",
        displayName = "One Dark",
        isDark = true,
        background = Color(0xFF282C34),
        foreground = Color(0xFFABB2BF),
        keyword = Color(0xFFC678DD),
        string = Color(0xFF98C379),
        number = Color(0xFFD19A66),
        comment = Color(0xFF5C6370),
        annotation = Color(0xFF61AFEF),
        function = Color(0xFF61AFEF),
        type = Color(0xFFE5C07B),
        identifier = Color(0xFFABB2BF),
    )

    val Material = EditorTheme(
        id = "material",
        displayName = "Material",
        isDark = true,
        background = Color(0xFF263238),
        foreground = Color(0xFFEEFFFF),
        keyword = Color(0xFFC792EA),
        string = Color(0xFFC3E88D),
        number = Color(0xFFF78C6C),
        comment = Color(0xFF546E7A),
        annotation = Color(0xFFFFCB6B),
        function = Color(0xFF82AAFF),
        type = Color(0xFFFFCB6B),
        identifier = Color(0xFFEEFFFF),
    )

    val Nord = EditorTheme(
        id = "nord",
        displayName = "Nord",
        isDark = true,
        background = Color(0xFF2E3440),
        foreground = Color(0xFFD8DEE9),
        keyword = Color(0xFF81A1C1),
        string = Color(0xFFA3BE8C),
        number = Color(0xFFB48EAD),
        comment = Color(0xFF616E88),
        annotation = Color(0xFF8FBCBB),
        function = Color(0xFF88C0D0),
        type = Color(0xFFEBCB8B),
        identifier = Color(0xFFD8DEE9),
    )

    val Gruvbox = EditorTheme(
        id = "gruvbox",
        displayName = "Gruvbox",
        isDark = true,
        background = Color(0xFF282828),
        foreground = Color(0xFFEBDBB2),
        keyword = Color(0xFFFB4934),
        string = Color(0xFFB8BB26),
        number = Color(0xFFD3869B),
        comment = Color(0xFF928374),
        annotation = Color(0xFFFABD2F),
        function = Color(0xFFFABD2F),
        type = Color(0xFFFABD2F),
        identifier = Color(0xFFEBDBB2),
    )

    val TokyoNight = EditorTheme(
        id = "tokyo_night",
        displayName = "Tokyo Night",
        isDark = true,
        background = Color(0xFF1A1B26),
        foreground = Color(0xFFA9B1D6),
        keyword = Color(0xFFBB9AF7),
        string = Color(0xFF9ECE6A),
        number = Color(0xFFFF9E64),
        comment = Color(0xFF565F89),
        annotation = Color(0xFF7DCFFF),
        function = Color(0xFF7AA2F7),
        type = Color(0xFF0DB9D7),
        identifier = Color(0xFFA9B1D6),
    )

    val Catppuccin = EditorTheme(
        id = "catppuccin_mocha",
        displayName = "Catppuccin Mocha",
        isDark = true,
        background = Color(0xFF1E1E2E),
        foreground = Color(0xFFCDD6F4),
        keyword = Color(0xFFF5C2E7),
        string = Color(0xFFA6E3A1),
        number = Color(0xFFFAB387),
        comment = Color(0xFF6C7086),
        annotation = Color(0xFFF9E2AF),
        function = Color(0xFF89B4FA),
        type = Color(0xFFFFD43B),
        identifier = Color(0xFFCDD6F4),
    )

    /** All themes shipped with ViperCode. */
    val all: List<EditorTheme> = listOf(
        ViperDefaultDark,
        ViperDefaultLight,
        Dracula,
        Monokai,
        SolarizedLight,
        SolarizedDark,
        GithubLight,
        GithubDark,
        OneDark,
        Material,
        Nord,
        Gruvbox,
        TokyoNight,
        Catppuccin,
    )

    /** Lookup by stored id; falls back to the dark default. */
    fun byId(id: String): EditorTheme = all.firstOrNull { it.id == id } ?: ViperDefaultDark
}
