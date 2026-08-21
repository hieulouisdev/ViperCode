package com.vipercode.ide.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.data.prefs.SettingsRepository.FontFamily
import com.vipercode.ide.data.prefs.SettingsRepository.LanguageMode
import com.vipercode.ide.data.prefs.SettingsRepository.SortBy
import com.vipercode.ide.data.prefs.SettingsRepository.ThemeMode
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.launch

/**
 * Settings screen.
 *
 * v0.0.4 additions:
 *  - **Language selector** (English / Tiếng Việt). Flip is live — the
 *    whole UI recomposes through [Strings.active] without an Activity
 *    restart.
 *  - **Show hidden files** toggle.
 *  - **Sort files by** selector (Name / Size / Modified).
 *  - **Live preview auto-refresh** toggle + delay slider.
 *
 * v0.0.2 — v0.0.3 features remain unchanged: theme, dynamic colour,
 * font family, font size, tab size, word wrap, line numbers,
 * auto-indent, auto-save + delay, local workspace toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    // Subscribe to the Strings catalogue so the screen recomposes when
    // the user flips the language.
    val activeLanguage by Strings.active.collectAsState()
    val s = Strings.get()

    val themeMode by SettingsRepository.themeMode.flow.collectAsState(initial = ThemeMode.SYSTEM)
    val dynamicColor by SettingsRepository.dynamicColor.flow.collectAsState(initial = true)
    val fontSize by SettingsRepository.fontSize.flow.collectAsState(initial = 14)
    val tabSize by SettingsRepository.tabSize.flow.collectAsState(initial = 4)
    val wordWrap by SettingsRepository.wordWrap.flow.collectAsState(initial = false)
    val lineNumbers by SettingsRepository.lineNumbers.flow.collectAsState(initial = true)
    val autoSave by SettingsRepository.autoSave.flow.collectAsState(initial = true)
    val autoSaveDelayMs by SettingsRepository.autoSaveDelayMs.flow.collectAsState(initial = 1500)
    val autoIndent by SettingsRepository.autoIndent.flow.collectAsState(initial = true)
    val fontFamily by SettingsRepository.fontFamily.flow.collectAsState(initial = FontFamily.SYSTEM)
    val useLocalWorkspace by SettingsRepository.useLocalWorkspace.flow.collectAsState(initial = true)

    // v0.0.4 — new prefs.
    val languageMode by SettingsRepository.languageMode.flow.collectAsState(initial = LanguageMode.SYSTEM)
    val showHidden by SettingsRepository.showHiddenFiles.flow.collectAsState(initial = false)
    val sortBy by SettingsRepository.sortBy.flow.collectAsState(initial = SortBy.NAME)
    val livePreview by SettingsRepository.livePreview.flow.collectAsState(initial = true)
    val previewDelayMs by SettingsRepository.previewDelayMs.flow.collectAsState(initial = 800)

    // v0.0.5 — new editor prefs.
    val autoCloseBrackets by SettingsRepository.autoCloseBrackets.flow.collectAsState(initial = true)
    val showStatusBar by SettingsRepository.showStatusBar.flow.collectAsState(initial = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(s.settingsAppearance)
            LanguageSelector(languageMode) { mode ->
                scope.launch { SettingsRepository.languageMode.set(mode) }
            }
            ThemeSelector(themeMode, s) { mode ->
                scope.launch { SettingsRepository.themeMode.set(mode) }
            }
            ToggleRow(
                title = s.settingsDynamicColor,
                subtitle = s.settingsDynamicColorDesc,
                checked = dynamicColor,
                onChange = { v -> scope.launch { SettingsRepository.dynamicColor.set(v) } },
            )
            FontFamilySelector(fontFamily, s) { ff ->
                scope.launch { SettingsRepository.fontFamily.set(ff) }
            }

            HorizontalDivider()
            SectionHeader(s.settingsEditor)
            SliderRow(
                title = s.settingsFontSize,
                valueLabel = "${fontSize} sp",
                value = fontSize.toFloat(),
                range = 10f..24f,
                steps = 13,
                onChange = { v -> scope.launch { SettingsRepository.fontSize.set(v.toInt()) } },
            )
            SliderRow(
                title = s.settingsTabSize,
                valueLabel = s.settingsTabSizeValue.format(tabSize),
                value = tabSize.toFloat(),
                range = 2f..8f,
                steps = 5,
                onChange = { v -> scope.launch { SettingsRepository.tabSize.set(v.toInt()) } },
            )
            ToggleRow(
                title = s.settingsWordWrap,
                subtitle = s.settingsWordWrapDesc,
                checked = wordWrap,
                onChange = { v -> scope.launch { SettingsRepository.wordWrap.set(v) } },
            )
            ToggleRow(
                title = s.settingsLineNumbers,
                subtitle = s.settingsLineNumbersDesc,
                checked = lineNumbers,
                onChange = { v -> scope.launch { SettingsRepository.lineNumbers.set(v) } },
            )
            ToggleRow(
                title = s.settingsAutoIndent,
                subtitle = s.settingsAutoIndentDesc,
                checked = autoIndent,
                onChange = { v -> scope.launch { SettingsRepository.autoIndent.set(v) } },
            )
            // v0.0.5 — auto-close brackets toggle.
            ToggleRow(
                title = s.settingsAutoCloseBrackets,
                subtitle = s.settingsAutoCloseBracketsDesc,
                checked = autoCloseBrackets,
                onChange = { v -> scope.launch { SettingsRepository.autoCloseBrackets.set(v) } },
            )
            // v0.0.5 — show editor status bar.
            ToggleRow(
                title = s.settingsShowStatusBar,
                subtitle = s.settingsShowStatusBarDesc,
                checked = showStatusBar,
                onChange = { v -> scope.launch { SettingsRepository.showStatusBar.set(v) } },
            )

            HorizontalDivider()
            SectionHeader(s.settingsAutoSave)
            ToggleRow(
                title = s.settingsAutoSave,
                subtitle = s.settingsAutoSaveDesc,
                checked = autoSave,
                onChange = { v -> scope.launch { SettingsRepository.autoSave.set(v) } },
            )
            SliderRow(
                title = s.settingsAutoSaveDelay,
                valueLabel = "${autoSaveDelayMs} ms",
                value = autoSaveDelayMs.toFloat(),
                range = 500f..5000f,
                steps = 8,
                onChange = { v -> scope.launch { SettingsRepository.autoSaveDelayMs.set(v.toInt()) } },
            )

            HorizontalDivider()
            SectionHeader(s.settingsStorage)
            ToggleRow(
                title = s.settingsUseLocalWorkspace,
                subtitle = s.settingsUseLocalWorkspaceDesc,
                checked = useLocalWorkspace,
                onChange = { v -> scope.launch { SettingsRepository.useLocalWorkspace.set(v) } },
            )
            ToggleRow(
                title = s.settingsHiddenFiles,
                subtitle = s.settingsHiddenFilesDesc,
                checked = showHidden,
                onChange = { v -> scope.launch { SettingsRepository.showHiddenFiles.set(v) } },
            )
            SortSelector(sortBy, s) { sb ->
                scope.launch { SettingsRepository.sortBy.set(sb) }
            }

            HorizontalDivider()
            SectionHeader(s.previewSubtitle)
            ToggleRow(
                title = s.previewLiveToggle,
                subtitle = s.previewSubtitle,
                checked = livePreview,
                onChange = { v -> scope.launch { SettingsRepository.livePreview.set(v) } },
            )
            SliderRow(
                title = s.settingsAutoSaveDelay,
                valueLabel = "${previewDelayMs} ms",
                value = previewDelayMs.toFloat(),
                range = 300f..3000f,
                steps = 8,
                onChange = { v -> scope.launch { SettingsRepository.previewDelayMs.set(v.toInt()) } },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun ThemeSelector(current: ThemeMode, s: Strings.T, onChange: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(s.settingsTheme, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == current,
                    onClick = { onChange(mode) },
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> s.settingsThemeSystem
                                ThemeMode.DARK -> s.settingsThemeDark
                                ThemeMode.LIGHT -> s.settingsThemeLight
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageSelector(current: LanguageMode, onChange: (LanguageMode) -> Unit) {
    val s = Strings.get()
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(s.settingsLanguage, style = MaterialTheme.typography.bodyLarge)
        Text(
            s.settingsLanguageDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LanguageMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == current,
                    onClick = { onChange(mode) },
                    label = {
                        Text(
                            when (mode) {
                                LanguageMode.SYSTEM -> s.settingsThemeSystem
                                LanguageMode.ENGLISH -> Strings.Language.ENGLISH.displayName
                                LanguageMode.VIETNAMESE -> Strings.Language.VIETNAMESE.nativeName
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SortSelector(current: SortBy, s: Strings.T, onChange: (SortBy) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortBy.entries.forEach { sb ->
                FilterChip(
                    selected = sb == current,
                    onClick = { onChange(sb) },
                    label = {
                        Text(
                            when (sb) {
                                SortBy.NAME -> s.commonSortByName
                                SortBy.SIZE -> s.commonSortBySize
                                SortBy.MODIFIED -> s.commonSortByModified
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FontFamilySelector(current: FontFamily, s: Strings.T, onChange: (FontFamily) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(s.settingsFontFamily, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FontFamily.entries.forEach { ff ->
                FilterChip(
                    selected = ff == current,
                    onClick = { onChange(ff) },
                    label = { Text(ff.displayName) },
                )
            }
        }
    }
}
