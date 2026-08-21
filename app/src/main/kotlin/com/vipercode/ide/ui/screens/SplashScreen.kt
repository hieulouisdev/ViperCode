package com.vipercode.ide.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

    LaunchedEffect(Unit) {
        delay(1100)
        onContinue()
    }

    var visible by remember { mutableStateOf(false) }
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
            .background(ViperDark),
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
