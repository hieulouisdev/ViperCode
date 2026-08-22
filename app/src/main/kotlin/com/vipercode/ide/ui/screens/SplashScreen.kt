package com.vipercode.ide.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.BuildConfig
import com.vipercode.ide.R
import com.vipercode.ide.ui.theme.ViperAccent
import com.vipercode.ide.ui.theme.ViperDark
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.delay

/**
 * Branded splash screen shown after the system splash.
 *
 * v0.0.4: tagline + version are routed through [Strings.get] so the
 * splash honours the user's language preference.
 */
@Composable
fun SplashScreen(onContinue: () -> Unit) {
    val activeLanguage by Strings.active.collectAsState()
    val s = Strings.get()

    // v0.0.8 — guard against double-invocation when the timer
    // fires AND the user taps the screen within the same frame.
    var continued by remember { mutableStateOf(false) }
    val once: () -> Unit = {
        if (!continued) {
            continued = true
            onContinue()
        }
    }

    // v0.0.7 — splash is skippable: tap the screen to continue
    // immediately. Delay reduced from 1100 ms to 800 ms (combined
    // with the 600 ms system splash, total splash time is now
    // ~1.4 s instead of 1.7 s).
    LaunchedEffect(Unit) {
        delay(800)
        once()
    }

    var visible by remember { mutableStateOf(false) }
    // v0.0.8 — merged with the splash-delay effect above for a
    // single 80ms-tick + 720ms-wait timeline (was two separate
    // LaunchedEffect(Unit) blocks).
    LaunchedEffect(Unit) {
        delay(80)
        visible = true
    }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(700),
        label = "splash-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700),
        label = "splash-alpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViperDark)
            // v0.11 — FIX (M2): key on Unit so the gesture detector
            // is NOT cancelled & restarted every recomposition (was
            // `pointerInput(once)` where `once` is a fresh lambda every
            // recomposition). The original v0.0.8 "fix" was based on a
            // misread — capturing a new `onContinue` lambda is handled
            // by Compose's automatic state tracking; we only need a
            // stable key here.
            .pointerInput(Unit) {
                // v0.0.7 — tap-to-skip the splash.
                detectTapGestures(onTap = { once() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "ViperCode logo",
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .alpha(alpha),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = s.appName,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = s.tagline,
                color = ViperAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha),
            )
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = "v" + BuildConfig.VERSION_NAME,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}
