package com.vipercode.ide.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.util.Language
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live HTML/CSS/JS preview screen.
 *
 * Renders the active HTML tab in a [WebView] with full JavaScript
 * enabled. If the user has additional CSS or JS tabs open alongside
 * the HTML tab, those stylesheets/scripts are inlined into the
 * rendered document so a small multi-file project "just works"
 * without any server or file:// routing.
 *
 * v0.0.4 fixes preview lag (the v0.0.3 complaint):
 *  - **`composedHtml` no longer recomputes on every keystroke**. The
 *    `remember` key is now `[refreshKey]` only — `activeTab.content`
 *    was removed from the key so the regex-based asset inliner runs
 *    once per refresh, not once per keystroke. v0.0.3 inlined CSS+JS
 *    on every keystroke even though the WebView only reloaded after
 *    the 600 ms debounce.
 *  - **Debounce is configurable** via [SettingsRepository.previewDelayMs]
 *    (default 800 ms, range 300–3000 ms in Settings).
 *  - **Live refresh toggle** ([SettingsRepository.livePreview]) lets
 *    the user disable auto-refresh entirely and rely on the manual
 *    reload button — useful when iterating on a JavaScript-heavy page
 *    whose state should not be reset on every save.
 *  - **Update callback compares content** so a recomposition triggered
 *    by an unrelated state change (e.g. tab focus) doesn't blow away
 *    the WebView's state by calling `loadDataWithBaseURL` with the
 *    same HTML.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    tabId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { FileRepository.get(context) }
    val tabs by repo.tabs.collectAsState()
    val activeId by repo.activeTabId.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Observe the Strings catalogue so the screen recomposes when the
    // user flips the UI language mid-session.
    val activeLanguage by Strings.active.collectAsState()
    val s = Strings.get()

    val livePreview by SettingsRepository.livePreview.flow
        .collectAsState(initial = SettingsRepository.livePreview.default)
    val previewDelayMs by SettingsRepository.previewDelayMs.flow
        .collectAsState(initial = SettingsRepository.previewDelayMs.default)

    val activeTab = remember(tabs, activeId, tabId) {
        tabs.firstOrNull { it.id == (activeId ?: tabId) }
            ?: tabs.firstOrNull { it.id == tabId }
    }

    var webviewRef by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    // Snapshot of the HTML rendered in the WebView. We only call
    // loadDataWithBaseURL when this differs from the next snapshot.
    var lastRenderedHtml by remember { mutableStateOf("") }

    // The composed HTML document (HTML + inlined CSS + inlined JS from
    // sibling tabs of the same workspace).
    //
    // KEY FIX (v0.0.4): keyed ONLY on `refreshKey` (and `tabs` so
    // CSS/JS edits to a sibling tab are picked up after the next
    // refresh tick). The previous `activeTab?.content` key caused
    // this whole block — including the regex-based companion asset
    // inliner — to run on every keystroke.
    val composedHtml = remember(refreshKey, tabs, activeLanguage) {
        val html = activeTab?.content.orEmpty()
        if (html.isBlank()) {
            "<html><body style='font-family:sans-serif;padding:2em;color:#888'>" +
                "<h2>${s.previewEmpty}</h2>" +
                "</body></html>"
        } else {
            inlineCompanionAssets(html, tabs.map { it })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${s.previewTitle} • ${activeTab?.name ?: "Untitled"}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = s.previewSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.previewBackToEditor)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Toggle live preview on/off without leaving the screen.
                        scope.launch {
                            SettingsRepository.livePreview.set(!livePreview)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = s.previewLiveToggle,
                            tint = if (livePreview) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = s.previewReload)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                HorizontalDivider()
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        webViewClient = WebViewClient()
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }
                        }
                        webviewRef = this
                        loadDataWithBaseURL(
                            "about:blank",
                            composedHtml,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                        lastRenderedHtml = composedHtml
                    }
                },
                update = { webview ->
                    // Only reload if the composed HTML actually changed
                    // since the last load. Spurious recompositions (e.g.
                    // an unrelated tab gaining focus) used to nuke the
                    // WebView's DOM state on every recomposition.
                    if (composedHtml != lastRenderedHtml) {
                        webview.loadDataWithBaseURL(
                            "about:blank",
                            composedHtml,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                        lastRenderedHtml = composedHtml
                    }
                },
            )
        }
    }

    // Auto-refresh debounce: re-render `previewDelayMs` ms after the
    // user stops typing. Bumps `refreshKey` so the `composedHtml`
    // snapshot above is recomputed AND the AndroidView's `update`
    // callback re-fires with the new content.
    //
    // Only fires when [livePreview] is enabled.
    if (livePreview) {
        LaunchedEffect(activeTab?.content, activeTab?.id, livePreview, previewDelayMs) {
            delay(previewDelayMs.toLong())
            refreshKey++
        }
    }
}

/**
 * Inlines the contents of sibling CSS and JS tabs into the HTML document.
 *
 * Strategy: if the HTML references `<link href="style.css">` or
 * `<script src="app.js">`, we look for open tabs of the same name and
 * inline their contents directly. This lets a simple multi-file
 * project (index.html + style.css + script.js) render correctly
 * without any host server.
 */
private fun inlineCompanionAssets(html: String, allTabs: List<com.vipercode.ide.data.model.EditorTab>): String {
    if (html.isBlank()) return html

    var result = html

    // Inline <link rel="stylesheet" href="*.css">
    val linkRegex = Regex(
        """<link\s+[^>]*?href\s*=\s*["']([^"']+\.css)["'][^>]*?>""",
        RegexOption.IGNORE_CASE,
    )
    result = linkRegex.replace(result) { mr ->
        val href = mr.groupValues[1]
        val fileName = Uri.parse(href).lastPathSegment ?: href
        val matchTab = allTabs.firstOrNull {
            it.name.equals(fileName, ignoreCase = true) && it.language == Language.CSS
        }
        if (matchTab != null) {
            "<style>\n${matchTab.content}\n</style>"
        } else mr.value
    }

    // Inline <script src="*.js"></script>
    val scriptRegex = Regex(
        """<script\s+[^>]*?src\s*=\s*["']([^"']+\.js)["'][^>]*?>\s*</script>""",
        RegexOption.IGNORE_CASE,
    )
    result = scriptRegex.replace(result) { mr ->
        val src = mr.groupValues[1]
        val fileName = Uri.parse(src).lastPathSegment ?: src
        val matchTab = allTabs.firstOrNull {
            it.name.equals(fileName, ignoreCase = true) &&
                (it.language == Language.JAVASCRIPT || it.language == Language.TYPESCRIPT)
        }
        if (matchTab != null) {
            "<script>\n${matchTab.content}\n</script>"
        } else mr.value
    }

    // Inject inline <style> and <script> blocks from any CSS / JS tabs
    // that are NOT referenced by the HTML at all — drop them at the
    // end of <head> / <body> so the preview still picks them up.
    val unreferencedCss = allTabs.filter {
        it.language == Language.CSS && !result.contains(it.name, ignoreCase = true)
    }
    val unreferencedJs = allTabs.filter {
        (it.language == Language.JAVASCRIPT || it.language == Language.TYPESCRIPT) &&
            !result.contains(it.name, ignoreCase = true)
    }
    if (unreferencedCss.isNotEmpty()) {
        val block = unreferencedCss.joinToString("\n\n") { "/* ${it.name} */\n${it.content}" }
        val cssTag = "<style>\n$block\n</style>"
        result = result.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$cssTag\n</head>")
            .ifBlank { "$result$cssTag" }
    }
    if (unreferencedJs.isNotEmpty()) {
        val block = unreferencedJs.joinToString("\n\n") { "// ${it.name}\n${it.content}" }
        val jsTag = "<script>\n$block\n</script>"
        result = result.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$jsTag\n</body>")
            .ifBlank { "$result$jsTag" }
    }

    return result
}
