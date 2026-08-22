package com.vipercode.ide.data.prefs

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tracks recently opened file URIs (v0.0.5).
 *
 * The list is kept in-memory (singleton) for fast access from any
 * screen. Persistence across app restarts is handled by the
 * [SettingsRepository.recentFilesPref] string preference, which
 * serialises the list as `\n`-separated URI strings.
 *
 * The repository caps the list at [MAX_ENTRIES] so it never grows
 * unbounded. When a new entry is added that's already in the list,
 * it's moved to the front (most-recent) instead of duplicated.
 *
 * v0.0.8 fixes:
 *  - Race conditions on `add()` / `remove()` / `clear()` now
 *    use atomic `_uris.update { ... }` (was non-atomic read-modify-
 *    write, could lose entries under concurrent calls).
 *  - `load()` no longer overwrites concurrent `add()` updates; it
 *    merges the persisted list with any in-memory entries.
 *  - `persistAsync()` wraps the write in a try/catch so a disk
 *    failure doesn't crash the supervisor scope.
 *  - `Uri.parse` is post-validated (`scheme != null`) so malformed
 *    persisted strings don't sneak in as `Uri.EMPTY`.
 */
object RecentFiles {

    private const val MAX_ENTRIES = 12

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uris = MutableStateFlow<List<Uri>>(emptyList())
    val uris: StateFlow<List<Uri>> = _uris.asStateFlow()

    /** Adds [uri] to the front of the recent list (de-duplicating). */
    fun add(uri: Uri) {
        val string = uri.toString()
        _uris.update { current ->
            (listOf(uri) + current.filterNot { it.toString() == string }).take(MAX_ENTRIES)
        }
        persistAsync()
    }

    /** Removes [uri] from the recent list. */
    fun remove(uri: Uri) {
        val string = uri.toString()
        _uris.update { current ->
            current.filterNot { it.toString() == string }
        }
        persistAsync()
    }

    /** Clears the entire recent list. */
    fun clear() {
        _uris.update { emptyList() }
        persistAsync()
    }

    /**
     * Loads the persisted list from [SettingsRepository.recentFiles].
     * Called once at app start by [com.vipercode.ide.ViperCodeApp].
     *
     * v0.0.8 — merges the persisted list with any in-memory entries
     * added between app start and the load finishing (was a hard
     * assignment that clobbered concurrent `add()` calls).
     */
    suspend fun load() {
        val raw = SettingsRepository.recentFiles.first()
        if (raw.isBlank()) return
        val persisted = raw.split('\n').filter { it.isNotBlank() }
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .filter { it.scheme != null }  // v0.0.8 — drop Uri.EMPTY
            .take(MAX_ENTRIES)
        _uris.update { current ->
            // Merge persisted (disk) with current (in-memory).
            // In-memory entries that came in via `add()` during the
            // load are kept; persisted entries fill the remaining
            // slots up to MAX_ENTRIES.
            val seen = current.map { it.toString() }.toMutableSet()
            val merged = current.toMutableList()
            for (u in persisted) {
                if (merged.size >= MAX_ENTRIES) break
                if (u.toString() !in seen) {
                    merged.add(u)
                    seen.add(u.toString())
                }
            }
            merged
        }
    }

    /** Persists the current list to [SettingsRepository.recentFiles]. */
    private fun persistAsync() {
        val serialised = _uris.value.joinToString("\n") { it.toString() }
        scope.launch {
            try {
                SettingsRepository.recentFiles.set(serialised)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w("RecentFiles", "persist failed", t)
            }
        }
    }
}
