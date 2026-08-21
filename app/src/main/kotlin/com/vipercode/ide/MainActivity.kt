package com.vipercode.ide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.ui.navigation.ViperNavHost
import com.vipercode.ide.ui.theme.ViperCodeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-Activity host for the whole app.
 *
 * - Installs the Android 12+ splash screen on creation.
 * - Hands control to a Compose [ViperNavHost] that owns the back stack.
 * - Forwards `VIEW` intents (file open from outside the app) into the
 *   navigation graph so a tapped file opens directly in the editor.
 *
 * v0.0.3 fixes:
 *  - `pendingExternalUri` is now a [MutableStateFlow] that Compose can
 *    observe via `collectAsState`. Previously it was a plain
 *    `@Volatile var` in the companion object, so subsequent ACTION_VIEW
 *    intents that arrived while the Activity was alive were never
 *    observed (the initial `LaunchedEffect(Unit)` had already fired).
 *  - The splash-screen keep-alive now uses the main [Handler] instead of
 *    a raw [Thread], which guarantees visibility of the `keepSplash`
 *    flag across threads.
 *  - Theme & dynamic-colour reads are no longer blocking the main thread
 *    — defaults are emitted immediately and the real preferences flow in
 *    asynchronously.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the splash visible for ~600 ms so the brand animation is
        // readable instead of a flash. Uses the main-thread Handler so
        // the visibility update happens on the same thread that reads it.
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        Handler(Looper.getMainLooper()).postDelayed({ keepSplash = false }, 600L)

        setContent {
            // Defaults are emitted immediately; the real preferences flow
            // in via the StateFlow without ever blocking the main thread.
            val themeMode by SettingsRepository.themeMode.flow
                .collectAsState(initial = SettingsRepository.themeMode.default)
            val dynamicColor by SettingsRepository.dynamicColor.flow
                .collectAsState(initial = SettingsRepository.dynamicColor.default)

            ViperCodeTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val externalUri by pendingExternalUri.asStateFlow().collectAsState()
                    ViperNavHost(
                        externalFileUri = externalUri,
                        onExternalUriConsumed = { pendingExternalUri.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            pendingExternalUri.value = intent.data
        }
    }

    companion object {
        // A StateFlow so Compose can observe new ACTION_VIEW URIs as
        // they arrive (instead of the v0.0.2 plain @Volatile var that
        // could only be read once by a `LaunchedEffect(Unit)`).
        private val pendingExternalUri = MutableStateFlow<Uri?>(null)
    }
}
