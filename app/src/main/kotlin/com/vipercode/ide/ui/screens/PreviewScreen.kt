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
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.util.Language
import kotlinx.coroutines.delay

/**
 * Live HTML/CSS/JS preview screen.
 *
 * Renders the active HTML tab in a [WebView] with full JavaScript
 * enabled. If the user has additional CSS or JS tabs open alongside
 * the HTML tab, those stylesheets/scripts are inlined into the
 * rendered document so a small multi-file project "just works"
 * without any server or file:// routing.
 *
 * Auto-refresh: a debounce fires 600 ms after the user stops typing
 * in the source HTML tab, reloading the WebView with the new content
 * so the user sees live updates without manual refresh.
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

    val activeTab = remember(tabs, activeId, tabId) {
        tabs.firstOrNull { it.id == (activeId ?: tabId) }
            ?: tabs.firstOrNull { it.id == tabId }
    }

    var webviewRef by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // The composed HTML document (HTML + inlined CSS + inlined JS from
    // sibling tabs of the same workspace).
    val composedHtml = remember(activeTab?.content, tabs, refreshKey) {
        val html = activeTab?.content.orEmpty()
        if (html.isBlank()) {
            "<html><body style='font-family:sans-serif;padding:2em;color:#888'>" +
                "<h2>Empty document</h2><p>Type some HTML in the editor to see the live preview.</p>" +
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
                            text = "Preview • ${activeTab?.name ?: "Untitled"}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Live HTML/CSS/JS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to editor")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload preview")
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
                    }
                },
                update = { webview ->
                    // Reload the document whenever composedHtml changes.
                    webview.loadDataWithBaseURL(
                        "about:blank",
                        composedHtml,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                },
                key = { refreshKey },
            )
        }
    }

    // Auto-refresh debounce: re-render 600 ms after the user stops
    // typing. We bump refreshKey so the AndroidView's key changes,
    // which forces `update` to run with the latest composedHtml.
    LaunchedEffect(activeTab?.content, activeTab?.id) {
        delay(600)
        refreshKey++
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
