package com.vipercode.ide.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Centralised, type-safe access to all user preferences.
 *
 * Backed by Jetpack DataStore (Preferences flavour). Each setting is
 * exposed as a typed [Flow] for reactive Compose consumption and a
 * blocking [Pref.now] variant for one-shot reads on background threads.
 *
 * The repository is intentionally a singleton — it must be initialised
 * once via [init] before any read/write.
 */
object SettingsRepository {

    @Volatile
    private var ctx: Context? = null

    private val Context.dataStore by preferencesDataStore(name = "vipercode_settings")

    enum class ThemeMode { SYSTEM, DARK, LIGHT }
    enum class FontFamily(val displayName: String, val cssStack: String) {
        SYSTEM("System default", "sans-serif-monospace"),
        JETBRAINS("JetBrains Mono", "JetBrainsMono"),
        FIRA("Fira Code", "FiraCode"),
    }

    /**
     * Interface language preference (v0.0.4).
     *
     * - [SYSTEM] follows Android's locale (English fallback if Vietnamese
     *   is not the system language).
     * - [ENGLISH] forces English regardless of the system locale.
     * - [VIETNAMESE] forces Vietnamese.
     */
    enum class LanguageMode { SYSTEM, ENGLISH, VIETNAMESE }

    /** Sort order for the file explorer (v0.0.4). */
    enum class SortBy { NAME, SIZE, MODIFIED }

    val themeMode = Pref(ThemeMode::class.java, "theme_mode", ThemeMode.SYSTEM)
    val dynamicColor = Pref(Boolean::class.javaObjectType, "dynamic_color", true)
    val fontSize = Pref(Int::class.javaObjectType, "font_size", 14)
    val tabSize = Pref(Int::class.javaObjectType, "tab_size", 4)
    val wordWrap = Pref(Boolean::class.javaObjectType, "word_wrap", false)
    val lineNumbers = Pref(Boolean::class.javaObjectType, "line_numbers", true)
    val autoSave = Pref(Boolean::class.javaObjectType, "auto_save", true)
    val autoSaveDelayMs = Pref(Int::class.javaObjectType, "auto_save_delay_ms", 1500)
    val autoIndent = Pref(Boolean::class.javaObjectType, "auto_indent", true)
    val fontFamily = Pref(FontFamily::class.java, "font_family", FontFamily.SYSTEM)
    val lastFolderUri = Pref(String::class.java, "last_folder_uri", "")
    val useLocalWorkspace = Pref(Boolean::class.javaObjectType, "use_local_workspace", true)

    // v0.0.4 — new preferences.
    val languageMode = Pref(LanguageMode::class.java, "language_mode", LanguageMode.SYSTEM)
    val showHiddenFiles = Pref(Boolean::class.javaObjectType, "show_hidden_files", false)
    val sortBy = Pref(SortBy::class.java, "sort_by", SortBy.NAME)
    val livePreview = Pref(Boolean::class.javaObjectType, "live_preview_auto_refresh", true)
    val previewDelayMs = Pref(Int::class.javaObjectType, "preview_delay_ms", 800)

    // v0.0.5 — new editor preferences.
    val autoCloseBrackets = Pref(Boolean::class.javaObjectType, "auto_close_brackets", true)
    val showStatusBar = Pref(Boolean::class.javaObjectType, "show_status_bar", true)

    // v0.0.5 — recent files list (serialised as \n-separated URIs).
    val recentFiles = Pref(String::class.java, "recent_files", "")

    // v0.0.6 — recent folders list (serialised as \n-separated URIs).
    // Used by the "Switch folder" sheet on the home screen so the user
    // can jump between the workspace, extracted projects and any SAF
    // folder they previously picked without re-opening the SAF picker.
    val recentFolders = Pref(String::class.java, "recent_folders", "")

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    private fun appContext(): Context =
        ctx ?: error("SettingsRepository not initialised. Call init(context) first.")

    private fun <T> keyOf(name: String, type: Class<T>): Preferences.Key<*> = when {
        type == Int::class.javaObjectType -> intPreferencesKey(name)
        type == Boolean::class.javaObjectType -> booleanPreferencesKey(name)
        type == String::class.java -> stringPreferencesKey(name)
        type.isEnum -> stringPreferencesKey(name)
        else -> stringPreferencesKey(name)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> decode(value: Any?, type: Class<T>, default: T): T = when {
        value == null -> default
        type == Int::class.javaObjectType -> (value as Int) as T
        type == Boolean::class.javaObjectType -> (value as Boolean) as T
        type == String::class.java -> (value as String) as T
        // Safe decode: if a stored enum name no longer matches any
        // variant (e.g. an enum value was renamed across versions),
        // fall back to the default instead of throwing
        // NoSuchElementException.
        type.isEnum -> type.enumConstants!!
            .map { it as Enum<*> }
            .firstOrNull { it.name == value } as? T ?: default
        else -> value as T
    }

    private fun <T> encode(value: T): Any = when (value) {
        is Enum<*> -> value.name
        else -> value as Any
    }

    /**
     * A typed preference wrapper. Exposes both a [flow] (for Compose) and
     * a blocking [now] accessor (for one-shot reads on background threads).
     *
     * IMPORTANT: [flow] and [key] are lazy. The DataStore is only resolved
     * on first collection / first key lookup, which guarantees the
     * singleton's [ctx] is already set by the time the JVM needs it.
     *
     * v0.0.3 changes:
     *   - [default] is now publicly exposed so callers can pass it as the
     *     `initial` value of `collectAsState`, avoiding a blocking
     *     `now()` call during composition.
     *   - [first] helper added for one-shot reads inside coroutines
     *     (replaces the discouraged `now()` inside `LaunchedEffect`).
     *
     * (v0.0.1 had [ctx] as `lateinit var` and eagerly built the Flow at
     * Pref-construction time — which ran before [init] could assign [ctx]
     * and crashed the app with UninitializedPropertyAccessException on
     * every launch.)
     */
    class Pref<T : Any>(
        private val type: Class<T>,
        private val name: String,
        val default: T,
    ) {
        val key: Preferences.Key<*> by lazy { keyOf(name, type) }

        val flow: Flow<T> by lazy {
            appContext().dataStore.data.map { prefs ->
                @Suppress("UNCHECKED_CAST")
                val raw = prefs[key as Preferences.Key<Any>] as Any?
                decode(raw, type, default)
            }
        }

        /** Blocking one-shot read. Use sparingly — only on background threads. */
        fun now(): T = runBlocking { flow.first() }

        /** Suspending one-shot read. Preferred over [now] in coroutines. */
        suspend fun first(): T = flow.first()

        suspend fun set(value: T) {
            appContext().dataStore.edit { prefs ->
                @Suppress("UNCHECKED_CAST")
                prefs[key as Preferences.Key<Any>] = encode(value) as Any
            }
        }
    }
}
