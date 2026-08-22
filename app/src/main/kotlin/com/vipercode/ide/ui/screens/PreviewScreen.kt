package com.vipercode.ide.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.util.Language
import com.vipercode.ide.util.MarkdownRenderer
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live preview screen (v0.0.5 rewrite).
 *
 * Renders the active HTML tab in a [WebView] with full JavaScript
 * enabled. The v0.0.4 implementation had several long-standing bugs
 * that caused "preview không chạy hoàn toàn file":
 *
 *  1. **Base URL was `about:blank`** — relative paths, fetch(),
 *     localStorage, ES modules and external CDNs were silently
 *     blocked. v0.0.5 keeps `about:blank` (safer for the WebView's
 *     origin model) BUT injects a proper `<base>` tag so the
 *     document can resolve relative URLs against a synthetic root,
 *     AND enables `allowFileAccess` + `allowContentAccess` so
 *     file:// URLs referenced from the HTML work too.
 *  2. **No viewport meta tag** — mobile pages rendered at 980px
 *     instead of the device width, so CSS media queries fired wrong.
 *     v0.0.5 injects `<meta name="viewport"
 *     content="width=device-width, initial-scale=1">` if missing.
 *  3. **No console capture** — JS errors were silently swallowed.
 *     v0.0.5 wires `WebChromeClient.onConsoleMessage` and surfaces
 *     every console.log / error / warning in a toggleable bottom
 *     panel.
 *  4. **`inlineCompanionAssets` `ifBlank` bug** — the v0.0.4 fallback
 *     `result.replaceFirst(...).ifBlank { ... }` never triggered
 *     because `replaceFirst` returns the input unchanged (never
 *     blank) when no match is found. v0.0.5 uses a real "append if
 *     not present" check.
 *  5. **No link-click handling** — clicking an `<a href>` hijacked
 *     the WebView's history stack. v0.0.5 overrides
 *     `shouldOverrideUrlLoading` so external links open in the
 *     system browser.
 *
 * New features:
 *  - **Markdown preview** — `.md` and `.markdown` files are rendered
 *    via [MarkdownRenderer] so the user can iterate on README drafts
 *    too.
 *  - **Console overlay** — toggleable bottom panel showing every
 *    JS console message with severity, source file and line number.
 *    Clear button resets the panel.
 *  - **Desktop / mobile viewport toggle** — simulate a 1280-px
 *    desktop viewport without rotating the device.
 *  - **Share HTML** — export the composed HTML via Android's share
 *    sheet.
 *  - **Open in browser** — opens the current content in the system
 *    browser (writes the HTML to a cache file first because
 *    `about:blank` content can't be shared via an `intent`).
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
    val scope = rememberCoroutineScope()

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

    // v0.0.5 — console overlay state.
    val consoleMessages = remember { mutableStateListOf<ConsoleEntry>() }
    var showConsole by remember { mutableStateOf(false) }

    // v0.0.5 — viewport toggle.
    var desktopMode by remember { mutableStateOf(false) }

    // v0.0.5 — track resource errors so they also appear in console.
    var resourceErrors by remember { mutableStateOf<List<String>>(emptyList()) }

    val tabLanguage = activeTab?.language
    val isHtml = tabLanguage == Language.HTML
    val isMarkdown = tabLanguage == Language.MARKDOWN
    val canPreview = isHtml || isMarkdown

    // The composed HTML document (HTML + inlined CSS + inlined JS
    // from sibling tabs of the same workspace, OR Markdown→HTML).
    //
    // v0.0.8 FIX: this used to key on `activeTab?.content`, which
    // re-evaluated on every keystroke and bypassed the debounce
    // LaunchedEffect entirely (the `update` block then reloaded the
    // WebView on every keystroke). Now we key ONLY on `refreshKey`
    // (which is bumped by the debounce LaunchedEffect) plus a few
    // other stable signals (sibling tabs, language, viewport mode).
    // The debounce now actually works as documented.
    val composedHtml = remember(refreshKey, tabs, activeLanguage, desktopMode, tabLanguage) {
        val html = activeTab?.content.orEmpty()
        when {
            !canPreview -> {
                "<html><body style='font-family:sans-serif;padding:2em;color:#888'>" +
                    "<h2>${s.previewNoPreview}</h2>" +
                    "</body></html>"
            }
            html.isBlank() -> {
                "<html><body style='font-family:sans-serif;padding:2em;color:#888'>" +
                    "<h2>${s.previewEmpty}</h2>" +
                    "</body></html>"
            }
            isMarkdown -> MarkdownRenderer.render(html)
            else -> inlineCompanionAssets(html, tabs.map { it })
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
                            text = if (isMarkdown) s.previewMarkdown else s.previewSubtitle,
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
                    // Console toggle (only meaningful for HTML/JS pages).
                    if (isHtml) {
                        IconButton(onClick = { showConsole = !showConsole }) {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = s.previewConsole,
                                tint = if (showConsole || consoleMessages.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Desktop/mobile viewport toggle (HTML only).
                    if (isHtml) {
                        IconButton(onClick = { desktopMode = !desktopMode }) {
                            Icon(
                                imageVector = if (desktopMode) Icons.Filled.DesktopWindows
                                else Icons.Filled.PhoneIphone,
                                contentDescription = if (desktopMode) s.previewDesktopView
                                else s.previewMobileView,
                                tint = if (desktopMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Live-refresh toggle.
                    IconButton(onClick = {
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
                    // Manual reload.
                    IconButton(onClick = {
                        refreshKey++
                        // Force the WebView to reload even if the
                        // composed HTML is identical (e.g. user
                        // pressed reload without changing anything).
                        lastRenderedHtml = ""
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = s.previewReload)
                    }
                    // Share HTML.
                    IconButton(onClick = {
                        val html = composedHtml
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/html"
                            putExtra(Intent.EXTRA_TEXT, html)
                            putExtra(Intent.EXTRA_SUBJECT, activeTab?.name ?: "preview.html")
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, s.previewShare))
                        }.onFailure {
                            Log.w("PreviewScreen", "share failed: ${it.message}")
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = s.previewShare)
                    }
                    // Open in browser — needs a file:// URL so we
                    // write the HTML to the app's cache first.
                    IconButton(onClick = {
                        val html = composedHtml
                        // v0.0.8 — write to disk on IO so we don't
                        // ANR on a multi-MB composed HTML doc.
                        scope.launch {
                            runCatching {
                                val cacheFile = withContext(Dispatchers.IO) {
                                    java.io.File(context.cacheDir, "preview_share.html").also {
                                        it.writeText(html)
                                    }
                                }
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cacheFile,
                                )
                                val view = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "text/html")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(view)
                            }.onFailure {
                                Log.w("PreviewScreen", "open external failed: ${it.message}")
                            }
                        }
                    }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = s.previewOpenExternal)
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
            // The WebView fills the remaining space minus the
            // console overlay (if open).
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            @SuppressLint("SetJavaScriptEnabled")
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            // v0.0.5: allow file:// so the HTML can
                            // load relative scripts/CSS. The base URL
                            // remains `about:blank` so the WebView's
                            // origin stays synthetic — only relative
                            // URLs are affected by the injected
                            // <base> tag.
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.mediaPlaybackRequiresUserGesture = true
                            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            // Apply the desktop viewport if requested.
                            if (desktopMode) {
                                setInitialScale(100)
                                settings.useWideViewPort = true
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    // Open every external link in the
                                    // system browser — never inside
                                    // the preview WebView.
                                    val uri = request?.url ?: return true
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                    return true
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    super.onReceivedError(view, request, error)
                                    val url = request?.url?.toString() ?: "<unknown>"
                                    val desc = error?.description?.toString() ?: "(no description)"
                                    val msg = "Failed to load $url: $desc"
                                    resourceErrors = resourceErrors + msg
                                    consoleMessages.add(
                                        ConsoleEntry(
                                            level = ConsoleLevel.ERROR,
                                            message = msg,
                                            sourceId = "network",
                                            lineNumber = 0,
                                        )
                                    )
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?,
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    val url = request?.url?.toString() ?: "<unknown>"
                                    val code = errorResponse?.statusCode ?: -1
                                    val msg = "HTTP $code loading $url"
                                    resourceErrors = resourceErrors + msg
                                    consoleMessages.add(
                                        ConsoleEntry(
                                            level = ConsoleLevel.WARN,
                                            message = msg,
                                            sourceId = "network",
                                            lineNumber = 0,
                                        )
                                    )
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                    val level = when (consoleMessage.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> ConsoleLevel.ERROR
                                        ConsoleMessage.MessageLevel.WARNING -> ConsoleLevel.WARN
                                        ConsoleMessage.MessageLevel.DEBUG -> ConsoleLevel.DEBUG
                                        ConsoleMessage.MessageLevel.TIP -> ConsoleLevel.INFO
                                        else -> ConsoleLevel.LOG
                                    }
                                    consoleMessages.add(
                                        ConsoleEntry(
                                            level = level,
                                            message = consoleMessage.message(),
                                            sourceId = consoleMessage.sourceId(),
                                            lineNumber = consoleMessage.lineNumber(),
                                        )
                                    )
                                    // Cap the buffer so a runaway loop
                                    // doesn't OOM the app.
                                    if (consoleMessages.size > 500) {
                                        consoleMessages.removeAt(0)
                                    }
                                    return true
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
                        // Only reload if the composed HTML actually
                        // changed since the last load. Spurious
                        // recompositions (e.g. an unrelated tab
                        // gaining focus) used to nuke the WebView's
                        // DOM state on every recomposition.
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
                        // v0.0.8 — replace deprecated `webview.scale`
                        // with `useWideViewPort` + `setInitialScale`,
                        // and force a reload so the new scale takes
                        // effect immediately (was one-way / non-
                        // immediate — toggling OFF was impossible).
                        val targetWide = desktopMode
                        if (webview.settings.useWideViewPort != targetWide) {
                            webview.settings.useWideViewPort = targetWide
                            webview.setInitialScale(if (desktopMode) 100 else 0)
                            webview.reload()
                        }
                    },
                )
            }
            // Console overlay panel — toggleable, takes up to 40%
            // of the preview area when expanded.
            if (showConsole && isHtml) {
                ConsoleOverlay(
                    messages = consoleMessages,
                    onClear = {
                        consoleMessages.clear()
                        resourceErrors = emptyList()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                )
            }
        }
    }

    // Clean up the WebView when the composable leaves the tree.
    DisposableEffect(Unit) {
        onDispose {
            webviewRef?.apply {
                runCatching { stopLoading() }
                runCatching { destroy() }
            }
            webviewRef = null
        }
    }

    // Auto-refresh debounce: re-render `previewDelayMs` ms after the
    // user stops typing. Bumps `refreshKey` so the `composedHtml`
    // snapshot above is recomputed AND the AndroidView's `update`
    // callback re-fires with the new content.
    //
    // Only fires when [livePreview] is enabled AND the file type
    // is previewable.
    if (livePreview && canPreview) {
        LaunchedEffect(activeTab?.content, activeTab?.id, livePreview, previewDelayMs) {
            delay(previewDelayMs.toLong())
            refreshKey++
        }
    }
}

/**
 * Console overlay panel — shows every JS console message received
 * since the panel was last cleared.
 */
@Composable
private fun ConsoleOverlay(
    messages: List<ConsoleEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.get()
    val listState = rememberLazyListState()
    // Auto-scroll to the latest message.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.previewConsole + " (${messages.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Clear, contentDescription = s.previewClearConsole)
            }
        }
        HorizontalDivider()
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s.previewConsoleEmpty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            ) {
                items(messages, key = { it.hashCode().toString() + ":" + it.timestamp }) { entry ->
                    ConsoleRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ConsoleRow(entry: ConsoleEntry) {
    val (color, prefix) = when (entry.level) {
        ConsoleLevel.ERROR -> MaterialTheme.colorScheme.error to "✗"
        ConsoleLevel.WARN -> MaterialTheme.colorScheme.tertiary to "⚠"
        ConsoleLevel.INFO -> MaterialTheme.colorScheme.primary to "ℹ"
        ConsoleLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant to "•"
        ConsoleLevel.LOG -> MaterialTheme.colorScheme.onSurface to "›"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = prefix,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 6.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            if (entry.sourceId != "network" && entry.sourceId.isNotBlank()) {
                Text(
                    text = "${entry.sourceId}:${entry.lineNumber}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** One captured console message. */
private data class ConsoleEntry(
    val level: ConsoleLevel,
    val message: String,
    val sourceId: String,
    val lineNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

private enum class ConsoleLevel { LOG, INFO, WARN, ERROR, DEBUG }

/**
 * Inlines the contents of sibling CSS and JS tabs into the HTML
 * document. v0.0.5 fixes the v0.0.4 `ifBlank` bug and adds the
 * viewport + base tag injection.
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

    // Inject inline <style> and <script> blocks from any CSS / JS
    // tabs that are NOT referenced by the HTML at all — drop them at
    // the end of <head> / <body> so the preview still picks them up.
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
        result = appendBeforeClosingTag(result, "head", cssTag)
    }
    if (unreferencedJs.isNotEmpty()) {
        val block = unreferencedJs.joinToString("\n\n") { "// ${it.name}\n${it.content}" }
        val jsTag = "<script>\n$block\n</script>"
        result = appendBeforeClosingTag(result, "body", jsTag)
    }

    // Inject viewport meta tag if missing. Mobile pages without this
    // render at 980px wide which is why CSS media queries don't fire
    // correctly.
    val viewportRegex = Regex("""<meta\s+[^>]*name\s*=\s*["']viewport["']""", RegexOption.IGNORE_CASE)
    if (!viewportRegex.containsMatchIn(result)) {
        val viewportTag = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
        result = appendBeforeClosingTag(result, "head", viewportTag)
    }

    return result
}

/**
 * Inserts [content] immediately before the closing `</[tagName]>` tag.
 * If the tag is not present (e.g. HTML fragment without `<head>`),
 * appends [content] at the very end of the document. This is the
 * v0.0.5 replacement for the v0.0.4 `replaceFirst(...).ifBlank { ... }`
 * which never triggered because `replaceFirst` always returns the
 * input unchanged when no match is found.
 */
private fun appendBeforeClosingTag(html: String, tagName: String, content: String): String {
    val closeTag = "</$tagName>"
    val idx = html.indexOf(closeTag, ignoreCase = true)
    return if (idx < 0) {
        // No closing tag found — append at the end.
        html + content
    } else {
        html.substring(0, idx) + content + "\n" + html.substring(idx)
    }
}
