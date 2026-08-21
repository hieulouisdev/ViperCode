package com.vipercode.ide

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.vipercode.ide.data.prefs.SettingsRepository

/**
 * ViperCode application entry point.
 *
 * Performs minimal app-wide initialization that must happen before any
 * Activity is created: registers the notification channel used for
 * background save events and primes the [SettingsRepository] so that the
 * first composition in [MainActivity] can read cached preferences without
 * blocking on disk I/O.
 */
class ViperCodeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        SettingsRepository.init(this)
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
