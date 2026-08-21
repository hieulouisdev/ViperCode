package com.vipercode.ide.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.BuildConfig
import com.vipercode.ide.R
import com.vipercode.ide.ui.theme.ViperAccent
import com.vipercode.ide.ui.theme.ViperDark
import com.vipercode.ide.ui.theme.ViperOnDark
import com.vipercode.ide.util.Strings

/**
 * About screen — brand surface, version, tagline, tech stack, license.
 *
 * v0.0.4: every visible string flows through [Strings.get] so the
 * About screen honours the user's interface language. The version
 * number is taken from BuildConfig so it always reflects the actual
 * shipped build (was hardcoded in v0.0.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val activeLanguage by Strings.active.collectAsState()
    val s = Strings.get()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.aboutTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            BrandHero()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = s.aboutDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SectionTitle(s.aboutFeatures)
                BulletList(
                    items = listOf(
                        s.aboutFeature1,
                        s.aboutFeature2,
                        s.aboutFeature3,
                        s.aboutFeature4,
                        s.aboutFeature5,
                        s.aboutFeature6,
                        s.aboutFeature7,
                        s.aboutFeature8,
                        s.aboutFeature9,
                        s.aboutFeature10,
                        s.aboutFeature11,
                        s.aboutFeature12,
                        s.aboutFeature13,
                        s.aboutFeature14,
                    ),
                )
                SectionTitle(s.aboutTechStack)
                BulletList(
                    items = listOf(
                        "Kotlin 2.0.21",
                        "Jetpack Compose (BOM 2024.12.01)",
                        "Material 3 (Material Components 1.12.0)",
                        "Android Gradle Plugin 8.7.3",
                        "Gradle 8.11.1 (Kotlin DSL)",
                        "AndroidX DataStore Preferences",
                        "AndroidX DocumentFile & Navigation Compose",
                        "AndroidX Splash Screen compat",
                        "WebView for live HTML/CSS/JS preview",
                    ),
                )
                HorizontalDivider()
                InfoLine(s.aboutVersion, "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.BUILD_TYPE + ")")
                InfoLine(s.aboutBuild, BuildConfig.VERSION_CODE.toString())
                InfoLine(s.aboutDeveloper, "hieulouisdev")
                InfoLine(s.aboutLicense, "MIT")
                InfoLine(s.aboutSource, "github.com/hieulouisdev/ViperCode")
            }
        }
    }
}

@Composable
private fun BrandHero() {
    val s = Strings.get()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ViperDark, ViperDark.copy(alpha = 0.9f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "ViperCode logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = s.appName,
                color = ViperOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = s.tagline,
                color = ViperAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
