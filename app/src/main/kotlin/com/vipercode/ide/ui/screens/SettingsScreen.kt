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
import com.vipercode.ide.data.prefs.SettingsRepository.ThemeMode
import com.vipercode.ide.data.prefs.SettingsRepository.FontFamily
import kotlinx.coroutines.launch

/**
 * Settings screen.
 *
 * Drives every preference via [SettingsRepository]'s suspend setters so
 * updates are immediately persisted to DataStore and propagated to the
 * rest of the UI through the StateFlow exposed by each [Pref].
 *
 * v0.0.2 adds:
 *  - Auto-save toggle + delay slider
 *  - Font family picker (system / JetBrains Mono / Fira Code)
 *  - Default-local-workspace toggle (offline-first)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            SectionHeader("Appearance")
            ThemeSelector(themeMode) { mode ->
                scope.launch { SettingsRepository.themeMode.set(mode) }
            }
            ToggleRow(
                title = "Dynamic color (Android 12+)",
                subtitle = "Use the system palette if available",
                checked = dynamicColor,
                onChange = { v -> scope.launch { SettingsRepository.dynamicColor.set(v) } },
            )
            FontFamilySelector(fontFamily) { ff ->
                scope.launch { SettingsRepository.fontFamily.set(ff) }
            }

            HorizontalDivider()
            SectionHeader("Editor")
            SliderRow(
                title = "Font size",
                valueLabel = "${fontSize} sp",
                value = fontSize.toFloat(),
                range = 10f..24f,
                steps = 13,
                onChange = { v -> scope.launch { SettingsRepository.fontSize.set(v.toInt()) } },
            )
            SliderRow(
                title = "Tab size",
                valueLabel = "$tabSize spaces",
                value = tabSize.toFloat(),
                range = 2f..8f,
                steps = 5,
                onChange = { v -> scope.launch { SettingsRepository.tabSize.set(v.toInt()) } },
            )
            ToggleRow(
                title = "Word wrap",
                subtitle = "Wrap long lines instead of horizontal scroll",
                checked = wordWrap,
                onChange = { v -> scope.launch { SettingsRepository.wordWrap.set(v) } },
            )
            ToggleRow(
                title = "Show line numbers",
                subtitle = "Display the gutter column on the left",
                checked = lineNumbers,
                onChange = { v -> scope.launch { SettingsRepository.lineNumbers.set(v) } },
            )
            ToggleRow(
                title = "Auto indent",
                subtitle = "Match the previous line's indentation",
                checked = autoIndent,
                onChange = { v -> scope.launch { SettingsRepository.autoIndent.set(v) } },
            )

            HorizontalDivider()
            SectionHeader("Auto-save")
            ToggleRow(
                title = "Auto save",
                subtitle = "Save dirty files after a short idle delay",
                checked = autoSave,
                onChange = { v -> scope.launch { SettingsRepository.autoSave.set(v) } },
            )
            SliderRow(
                title = "Auto-save delay",
                valueLabel = "${autoSaveDelayMs} ms",
                value = autoSaveDelayMs.toFloat(),
                range = 500f..5000f,
                steps = 8,
                onChange = { v -> scope.launch { SettingsRepository.autoSaveDelayMs.set(v.toInt()) } },
            )

            HorizontalDivider()
            SectionHeader("Storage (offline)")
            ToggleRow(
                title = "Use local workspace",
                subtitle = "Create a default offline workspace inside the app's private storage so ViperCode works without picking a folder",
                checked = useLocalWorkspace,
                onChange = { v -> scope.launch { SettingsRepository.useLocalWorkspace.set(v) } },
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
private fun ThemeSelector(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == current
                FilterChip(
                    selected = selected,
                    onClick = { onChange(mode) },
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.LIGHT -> "Light"
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FontFamilySelector(current: FontFamily, onChange: (FontFamily) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("Editor font family", style = MaterialTheme.typography.bodyLarge)
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
