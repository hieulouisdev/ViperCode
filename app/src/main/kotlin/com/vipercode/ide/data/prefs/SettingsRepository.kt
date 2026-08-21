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

    private lateinit var ctx: Context

    private val Context.dataStore by preferencesDataStore(name = "vipercode_settings")

    enum class ThemeMode { SYSTEM, DARK, LIGHT }
    enum class FontFamily(val displayName: String, val cssStack: String) {
        SYSTEM("System default", "sans-serif-monospace"),
        JETBRAINS("JetBrains Mono", "JetBrainsMono"),
        FIRA("Fira Code", "FiraCode"),
    }

    val themeMode = Pref(ThemeMode::class.java, "theme_mode", ThemeMode.SYSTEM)
    val dynamicColor = Pref(Boolean::class.javaObjectType, "dynamic_color", true)
    val fontSize = Pref(Int::class.javaObjectType, "font_size", 14)
    val tabSize = Pref(Int::class.javaObjectType, "tab_size", 4)
    val wordWrap = Pref(Boolean::class.javaObjectType, "word_wrap", false)
    val lineNumbers = Pref(Boolean::class.javaObjectType, "line_numbers", true)
    val autoSave = Pref(Boolean::class.javaObjectType, "auto_save", false)
    val autoIndent = Pref(Boolean::class.javaObjectType, "auto_indent", true)
    val fontFamily = Pref(FontFamily::class.java, "font_family", FontFamily.SYSTEM)
    val lastFolderUri = Pref(String::class.java, "last_folder_uri", "")

    fun init(context: Context) {
        ctx = context.applicationContext
    }

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
        type.isEnum -> type.enumConstants!!.first { (it as Enum<*>).name == value } as T
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
     * Implemented as a non-inner class — it references the singleton's
     * private [ctx] field directly so it does not need a parent instance.
     */
    class Pref<T : Any>(
        private val type: Class<T>,
        private val name: String,
        private val default: T,
    ) {
        private val key: Preferences.Key<*> by lazy { keyOf(name, type) }

        val flow: Flow<T> = ctx.dataStore.data.map { prefs ->
            @Suppress("UNCHECKED_CAST")
            val raw = prefs[key as Preferences.Key<Any>] as Any?
            decode(raw, type, default)
        }

        fun now(): T = runBlocking { flow.first() }

        suspend fun set(value: T) {
            ctx.dataStore.edit { prefs ->
                @Suppress("UNCHECKED_CAST")
                prefs[key as Preferences.Key<Any>] = encode(value) as Any
            }
        }
    }
}
