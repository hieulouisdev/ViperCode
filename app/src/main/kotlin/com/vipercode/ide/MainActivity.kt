package com.vipercode.ide

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.ui.navigation.ViperNavHost
import com.vipercode.ide.ui.theme.ViperCodeTheme
import kotlinx.coroutines.delay

/**
 * Single-Activity host for the whole app.
 *
 * - Installs the Android 12+ splash screen on creation.
 * - Hands control to a Compose [ViperNavHost] that owns the back stack.
 * - Forwards `VIEW` intents (file open from outside the app) into the
 *   navigation graph so a tapped file opens directly in the editor.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the splash visible for at least 600 ms so the brand
        // animation is readable instead of a flash.
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }

        setContent {
            val themeMode by SettingsRepository.themeMode.flow.collectAsState(
                initial = SettingsRepository.themeMode.now(),
            )
            val dynamicColor by SettingsRepository.dynamicColor.flow.collectAsState(
                initial = SettingsRepository.dynamicColor.now(),
            )

            ViperCodeTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var externalUri by remember {
                        mutableStateOf(
                            intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data,
                        )
                    }
                    // If another ACTION_VIEW arrives while the activity is alive
                    // (launchMode defaults to singleTop, so onNewIntent fires).
                    LaunchedEffect(Unit) {
                        pendingExternalUri?.let { externalUri = it; pendingExternalUri = null }
                    }
                    ViperNavHost(externalFileUri = externalUri) {
                        externalUri = null
                    }
                }
            }
        }

        Thread {
            try { Thread.sleep(600) } finally { keepSplash = false }
        }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            pendingExternalUri = intent.data
        }
    }

    companion object {
        @Volatile var pendingExternalUri: Uri? = null
    }
}
