package com.vipercode.ide

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ViperCode application entry point.
 *
 * Performs minimal app-wide initialization that must happen before any
 * Activity is created: registers the notification channel used for
 * background save events, primes the [SettingsRepository] so that the
 * first composition in [MainActivity] can read cached preferences without
 * blocking on disk I/O, and applies the saved interface language to
 * [Strings] so the very first frame already speaks the user's preferred
 * language (v0.0.4).
 */
class ViperCodeApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        SettingsRepository.init(this)
        // Apply the saved language preference on a background thread so
        // the main thread is never blocked. The Strings catalogue flips
        // atomically once the read resolves; the first frame may briefly
        // render in English before flipping to Vietnamese — but only on
        // the very first launch after a language change.
        appScope.launch {
            val mode = SettingsRepository.languageMode.first()
            val resolved = when (mode) {
                SettingsRepository.LanguageMode.SYSTEM -> {
                    val lang = Locale.getDefault().language
                    if (lang == "vi") Strings.Language.VIETNAMESE
                    else Strings.Language.ENGLISH
                }
                SettingsRepository.LanguageMode.ENGLISH -> Strings.Language.ENGLISH
                SettingsRepository.LanguageMode.VIETNAMESE -> Strings.Language.VIETNAMESE
            }
            Strings.setLanguage(resolved)
        }
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "vipercode.editor"

        @Volatile
        private var instance: ViperCodeApp? = null

        fun get(): ViperCodeApp =
            instance ?: error("ViperCodeApp not yet created")
    }
}
