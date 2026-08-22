package com.vipercode.ide.data.prefs

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Tracks recently opened folder URIs (v0.0.6).
 *
 * Mirrors [RecentFiles] but for folders — used by the home screen's
 * "Switch folder" sheet so the user can jump between the workspace,
 * extracted projects, and any SAF folder they previously picked without
 * having to re-open the SAF picker each time.
 *
 * The list is kept in-memory (singleton) for fast access from any
 * screen. Persistence across app restarts is handled by the
 * [SettingsRepository.recentFolders] string preference, which
 * serialises the list as `\n`-separated URI strings.
 *
 * The repository caps the list at [MAX_ENTRIES] so it never grows
 * unbounded. When a new entry is added that's already in the list,
 * it's moved to the front (most-recent) instead of duplicated.
 */
object RecentFolders {

    private const val MAX_ENTRIES = 12

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uris = MutableStateFlow<List<Uri>>(emptyList())
    val uris: StateFlow<List<Uri>> = _uris.asStateFlow()

    /** Adds [uri] to the front of the recent list (de-duplicating). */
    fun add(uri: Uri) {
        val string = uri.toString()
        val current = _uris.value.filterNot { it.toString() == string }
        _uris.value = (listOf(uri) + current).take(MAX_ENTRIES)
        persistAsync()
    }

    /** Removes [uri] from the recent list. */
    fun remove(uri: Uri) {
        val string = uri.toString()
        _uris.value = _uris.value.filterNot { it.toString() == string }
        persistAsync()
    }

    /** Clears the entire recent list. */
    fun clear() {
        _uris.value = emptyList()
        persistAsync()
    }

    /**
     * Loads the persisted list from [SettingsRepository.recentFolders].
     * Called once at app start by [com.vipercode.ide.ViperCodeApp].
     */
    suspend fun load() {
        val raw = SettingsRepository.recentFolders.first()
        if (raw.isBlank()) return
        val list = raw.split('\n').filter { it.isNotBlank() }
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .take(MAX_ENTRIES)
        _uris.value = list
    }

    /** Persists the current list to [SettingsRepository.recentFolders]. */
    private fun persistAsync() {
        val serialised = _uris.value.joinToString("\n") { it.toString() }
        scope.launch {
            SettingsRepository.recentFolders.set(serialised)
        }
    }
}
