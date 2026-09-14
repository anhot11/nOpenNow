package com.opencloudgaming.opennow

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.ClipData
import android.content.ClipboardManager
import com.opencloudgaming.opennow.browser.BookmarkItem
import com.opencloudgaming.opennow.browser.BrowserColors
import com.opencloudgaming.opennow.browser.BrowserSecurityManager
import com.opencloudgaming.opennow.browser.HistoryItem
import com.opencloudgaming.opennow.browser.IncognitoManager
import com.opencloudgaming.opennow.browser.PcTunnelProxyServer
import com.opencloudgaming.opennow.browser.VpsProxyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "OpenNowInAppBrowser"

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

data class InAppDownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val url: String,
    val mimeType: String = "",
    val totalBytes: Long = -1L,
    val timestamp: Long = System.currentTimeMillis(),
    val isExecutable: Boolean = false,
)

private fun openFileOnAndroid(context: Context, item: InAppDownloadItem) {
    try {
        val uri = Uri.parse(item.url)
        val mime = item.mimeType.ifBlank {
            when {
                item.fileName.endsWith(".apk", true) -> "application/vnd.android.package-archive"
                item.fileName.endsWith(".zip", true) -> "application/zip"
                item.fileName.endsWith(".pdf", true) -> "application/pdf"
                item.fileName.endsWith(".txt", true) -> "text/plain"
                else -> "*/*"
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No hay app para abrir este archivo: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenNowInAppBrowserDialog(
    onDismissRequest: () -> Unit,
    onRunCommandOnPc: ((String) -> Unit)? = null,
    initialUrl: String = "https://www.google.com",
    browserLowQualityEnabled: Boolean = false,
    onBrowserLowQualityToggle: () -> Unit = {},
    sessionId: String? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val securityManager = remember { BrowserSecurityManager(context) }

    // Orientation & Display state
    var isPortrait by rememberSaveable { mutableStateOf(true) }
    var currentUrl by rememberSaveable { mutableStateOf(initialUrl) }
    var urlInputText by rememberSaveable { mutableStateOf(initialUrl) }
    var pageTitle by rememberSaveable { mutableStateOf("Navegador") }
    var pageLoadingProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(false) }

    // Browser navigation & Engine
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Editing State (Omnibar IME auto-focus)
    var isEditingOmnibar by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Privacy & Incognito (Pure RAM)
    var isIncognito by rememberSaveable { mutableStateOf(false) }

    // PC WebSocket Tunnel & Dual-Mode Proxy Engine
    val pairCode = remember { securityManager.getPairCode() }
    var activeTunnelUrl by remember { mutableStateOf(securityManager.getPcTunnelUrl()) }
    var isPcTunnelConnected by remember { mutableStateOf(!activeTunnelUrl.isNullOrBlank()) }
    var pcTunnelStatusText by remember {
        mutableStateOf(if (!activeTunnelUrl.isNullOrBlank()) "🟢 Conectado (GeForce NOW)" else "📱 Modo Celular Directo")
    }
    var proxyServerInstance by remember { mutableStateOf<PcTunnelProxyServer?>(null) }

    // Dialogs & Menus
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showPcTunnelDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Downloads list
    val downloadList = remember { mutableStateListOf<InAppDownloadItem>() }

    fun switchTunnel(newUrl: String?) {
        val clean = newUrl?.trim()?.ifBlank { null }
        activeTunnelUrl = clean
        securityManager.setPcTunnelUrl(clean)
        proxyServerInstance?.tunnelWssUrl = clean
        if (clean != null) {
            isPcTunnelConnected = true
            pcTunnelStatusText = "🟢 Conectado (GeForce NOW)"
            Toast.makeText(context, "🟢 Túnel PC GeForce NOW Activo", Toast.LENGTH_SHORT).show()
        } else {
            isPcTunnelConnected = false
            pcTunnelStatusText = "📱 Modo Celular Directo"
            Toast.makeText(context, "📱 Modo Celular Directo (0ms lag)", Toast.LENGTH_SHORT).show()
        }
        webViewInstance?.reload()
    }

    // Initialize local Dual-Mode proxy server on ephemeral port (0) and hook into WebView
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val server = PcTunnelProxyServer(port = 0, tunnelWssUrl = activeTunnelUrl)
            server.start()
            proxyServerInstance = server
            withContext(Dispatchers.Main) {
                VpsProxyController.applySocksProxy(server.boundPort) { success ->
                    if (success) {
                        Log.d(TAG, "Local dual-mode proxy hooked to WebView on port ${server.boundPort}")
                    }
                }
            }
        }
    }

    // Background auto-discovery for PC transmitter on ntfy.sh
    LaunchedEffect(pairCode, isPcTunnelConnected) {
        if (!isPcTunnelConnected) {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url("https://ntfy.sh/opennow_tunnel_$pairCode/raw?poll=1")
                .build()

            while (!isPcTunnelConnected && isActive) {
                try {
                    val body = withContext(Dispatchers.IO) {
                        val resp = client.newCall(req).execute()
                        resp.body?.string()?.trim() ?: ""
                    }
                    if (body.contains("trycloudflare.com")) {
                        switchTunnel(body)
                        break
                    }
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    // Auto-focus IME keyboard when entering omnibar editing
    LaunchedEffect(isEditingOmnibar) {
        if (isEditingOmnibar) {
            urlInputText = currentUrl
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Sync input text when navigation finishes
    LaunchedEffect(currentUrl) {
        if (!isEditingOmnibar) {
            urlInputText = currentUrl
        }
    }

    // Orientation management & cleanup
    DisposableEffect(isPortrait) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = if (isPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = originalOrientation
            // Clean up proxy and server when exiting browser
            proxyServerInstance?.stop()
            VpsProxyController.clearProxy()
            if (isIncognito) {
                IncognitoManager.purgeIncognitoData(webViewInstance)
            }
        }
    }

    // Back handling
    BackHandler {
        when {
            showDownloadsDialog -> showDownloadsDialog = false
            showPcTunnelDialog -> showPcTunnelDialog = false
            showBookmarksDialog -> showBookmarksDialog = false
            showHistoryDialog -> showHistoryDialog = false
            isEditingOmnibar -> isEditingOmnibar = false
            webViewInstance?.canGoBack() == true -> webViewInstance?.goBack()
            else -> onDismissRequest()
        }
    }

    val displayHost = remember(currentUrl) {
        try {
            if (currentUrl.isBlank()) {
                "Buscar o escribir URL"
            } else {
                val uri = URI(currentUrl)
                val host = uri.host ?: currentUrl
                host.removePrefix("www.")
            }
        } catch (_: Exception) {
            currentUrl.ifBlank { "Buscar o escribir URL" }
        }
    }

    val shieldColor = if (isPcTunnelConnected) BrowserColors.StatusGreen else Color(0xFF89B4FA)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isIncognito) BrowserColors.IncognitoPurpleDark else BrowserColors.DarkBackground)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 1. WebView Viewport taking main area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = !isIncognito
                                    databaseEnabled = !isIncognito
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    mediaPlaybackRequiresUserGesture = false
                                    cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        pageLoadingProgress = newProgress / 100f
                                        isPageLoading = newProgress < 100
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            pageTitle = title
                                            if (!isIncognito && !currentUrl.startsWith("about:") && !currentUrl.startsWith("data:")) {
                                                securityManager.addHistory(title, currentUrl)
                                            }
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isPageLoading = true
                                        if (!url.isNullOrBlank()) {
                                            currentUrl = url
                                            urlInputText = url
                                        }
                                        canGoBack = canGoBack()
                                        canGoForward = canGoForward()
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isPageLoading = false
                                        canGoBack = canGoBack()
                                        canGoForward = canGoForward()
                                        if (!url.isNullOrBlank()) {
                                            currentUrl = url
                                            urlInputText = url
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return false
                                    }
                                }

                                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                    val guessedName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    val isExe = guessedName.endsWith(".exe", true) ||
                                            guessedName.endsWith(".msi", true) ||
                                            guessedName.endsWith(".bat", true) ||
                                            guessedName.endsWith(".ps1", true)

                                    val item = InAppDownloadItem(
                                        fileName = guessedName,
                                        url = url,
                                        mimeType = mimetype ?: "",
                                        totalBytes = contentLength,
                                        isExecutable = isExe
                                    )
                                    downloadList.add(0, item)
                                    showDownloadsDialog = true

                                    try {
                                        val req = DownloadManager.Request(Uri.parse(url)).apply {
                                            setTitle(guessedName)
                                            setDescription("Descargando desde new OpenNow...")
                                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessedName)
                                            allowScanningByMediaScanner()
                                        }
                                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                                        dm?.enqueue(req)
                                    } catch (_: Exception) {}
                                }

                                loadUrl(currentUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 2. Loading Indicator (above bottom Omnibar)
                if (isPageLoading && pageLoadingProgress in 0.01f..0.99f) {
                    LinearProgressIndicator(
                        progress = { pageLoadingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = if (isIncognito) BrowserColors.IncognitoPurpleBadge else Color(0xFF89B4FA),
                        trackColor = if (isIncognito) BrowserColors.IncognitoPurplePill else Color(0xFF313244)
                    )
                }

                // 3. Sleek Mobile Omnibar (52dp Bottom Bar from VpsBrowser)
                Surface(
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    color = if (isIncognito) BrowserColors.IncognitoPurpleDark else Color(0xFF181825),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isIncognito) BrowserColors.IncognitoPurpleBorder else Color(0xFF313244),
                            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                        )
                ) {
                    if (isEditingOmnibar) {
                        // Full width text editing row with instant Android IME keyboard
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isIncognito) BrowserColors.IncognitoPurpleBadge else Color(0xFF89B4FA),
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(20.dp)
                            )

                            OutlinedTextField(
                                value = urlInputText,
                                onValueChange = { urlInputText = it },
                                placeholder = {
                                    Text(
                                        if (isIncognito) "Buscar con Incógnito (RAM)..." else "Buscar o ingresar URL...",
                                        fontSize = 13.sp,
                                        color = Color(0xFFA6ADC8)
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isIncognito) BrowserColors.IncognitoPurpleBorder else Color(0xFF89B4FA),
                                    unfocusedBorderColor = if (isIncognito) BrowserColors.IncognitoPurple.copy(alpha = 0.5f) else Color(0xFF45475A),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color(0xFFCDD6F4)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isEditingOmnibar = false
                                        var target = urlInputText.trim()
                                        if (target.isNotBlank()) {
                                            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                                target = if (target.contains(".") && !target.contains(" ")) {
                                                    "https://$target"
                                                } else {
                                                    "https://www.google.com/search?q=${URLEncoder.encode(target, "UTF-8")}"
                                                }
                                            }
                                            currentUrl = target
                                            webViewInstance?.loadUrl(target)
                                        }
                                    }
                                ),
                                trailingIcon = {
                                    if (urlInputText.isNotBlank()) {
                                        IconButton(onClick = { urlInputText = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Borrar", modifier = Modifier.size(18.dp), tint = Color.White)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .focusRequester(focusRequester)
                            )

                            TextButton(
                                onClick = {
                                    isEditingOmnibar = false
                                    urlInputText = currentUrl
                                }
                            ) {
                                Text(
                                    "Cancelar",
                                    fontSize = 12.sp,
                                    color = if (isIncognito) BrowserColors.IncognitoPurpleBadge else Color(0xFF89B4FA)
                                )
                            }
                        }
                    } else {
                        // Standard Omnibar Navigation Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            // Back Button
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    webViewInstance?.goBack()
                                },
                                enabled = canGoBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Atrás",
                                    tint = if (canGoBack) {
                                        if (isIncognito) BrowserColors.IncognitoPurpleAccent else Color.White
                                    } else {
                                        Color(0xFF585B70)
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Forward Button
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    webViewInstance?.goForward()
                                },
                                enabled = canGoForward,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Adelante",
                                    tint = if (canGoForward) {
                                        if (isIncognito) BrowserColors.IncognitoPurpleAccent else Color.White
                                    } else {
                                        Color(0xFF585B70)
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Central Pill: Domain + Incognito + VPS Shield Indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(
                                        if (isIncognito) BrowserColors.IncognitoPurplePill else Color(0xFF313244)
                                    )
                                    .border(
                                        1.dp,
                                        if (isIncognito) BrowserColors.IncognitoPurpleBorder.copy(alpha = 0.8f) else Color(0xFF45475A),
                                        RoundedCornerShape(19.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isEditingOmnibar = true
                                    }
                                    .padding(horizontal = 10.dp)
                            ) {
                                if (isIncognito) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = "Modo Incógnito",
                                        tint = BrowserColors.IncognitoPurpleBadge,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (currentUrl.startsWith("https://")) BrowserColors.StatusGreen else Color(0xFFA6ADC8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = displayHost,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isIncognito) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(BrowserColors.IncognitoPurple.copy(alpha = 0.35f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "RAM",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = BrowserColors.IncognitoPurpleBadge
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                // PC Tunnel Shield Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isIncognito) BrowserColors.IncognitoPurple.copy(alpha = 0.25f) else Color(0xFF1E1E2E)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showPcTunnelDialog = true
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(shieldColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isPcTunnelConnected) "PC GFN" else "Directo",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncognito) BrowserColors.IncognitoPurpleBadge else Color(0xFF89B4FA)
                                    )
                                }
                            }

                            // Reload Button
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isPageLoading) webViewInstance?.stopLoading() else webViewInstance?.reload()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPageLoading) Icons.Default.Close else Icons.Default.Refresh,
                                    contentDescription = "Recargar",
                                    tint = if (isIncognito) BrowserColors.IncognitoPurpleAccent else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 3-Dots Menu Button
                            Box {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showOptionsMenu = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Menú",
                                        tint = if (isIncognito) BrowserColors.IncognitoPurpleBadge else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false },
                                    modifier = Modifier.background(Color(0xFF181825))
                                ) {
                                    // 1. Incognito Mode (RAM Pura)
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        if (isIncognito) "🕵️ Incógnito (RAM Pura)" else "🕵️ Modo Incógnito",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = if (isIncognito) BrowserColors.IncognitoPurpleAccent else Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isIncognito) BrowserColors.IncognitoPurple else Color(0xFF313244)
                                                    ) {
                                                        Text(
                                                            text = if (isIncognito) "ACTIVO" else "OFF",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isIncognito) Color.White else Color(0xFFA6ADC8),
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    if (isIncognito) "Caché y cookies solo en RAM volátil (0 disco)" else "Desactiva escrituras en almacenamiento local",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFA6ADC8)
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isIncognito) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = if (isIncognito) BrowserColors.IncognitoPurple else Color(0xFF89B4FA)
                                            )
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isIncognito = !isIncognito
                                            webViewInstance?.let { wv ->
                                                IncognitoManager.applyIncognitoSettings(wv, isIncognito)
                                                wv.reload()
                                            }
                                            Toast.makeText(
                                                context,
                                                if (isIncognito) "🕵️ Modo Incógnito Activado (RAM Pura)" else "Modo Estándar Activado",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )

                                    HorizontalDivider(color = Color(0xFF313244))

                                    // 2. Rotate Screen Toggle
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isPortrait) "🔄 Rotar a Pantalla Horizontal" else "🔄 Rotar a Pantalla Vertical",
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color(0xFF89B4FA))
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            isPortrait = !isPortrait
                                        }
                                    )

                                    // 3. PC Tunnel (GeForce NOW)
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    if (isPcTunnelConnected) "🖥️ Túnel PC GFN (Conectado)" else "🖥️ Túnel de PC (GeForce NOW)",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = if (isPcTunnelConnected) Color(0xFFA6E3A1) else Color.White
                                                )
                                                Text(
                                                    if (isPcTunnelConnected) "IP NVIDIA: ${activeTunnelUrl ?: "PC"}" else "Enrutar tráfico por la PC de GeForce NOW",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFA6ADC8)
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Shield, contentDescription = null, tint = shieldColor)
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showPcTunnelDialog = true
                                        }
                                    )

                                    // 4. Downloads Manager
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("📥 Administrador de Descargas", fontSize = 13.sp, color = Color.White)
                                                if (downloadList.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color(0xFF89B4FA)
                                                    ) {
                                                        Text(
                                                            text = "${downloadList.size}",
                                                            fontSize = 10.sp,
                                                            color = Color.Black,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFFA6E3A1))
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showDownloadsDialog = true
                                        }
                                    )

                                    // 5. Bookmarks
                                    DropdownMenuItem(
                                        text = {
                                            Text("⭐️ Marcadores y Favoritos", fontSize = 13.sp, color = Color.White)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF9E2AF))
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showBookmarksDialog = true
                                        }
                                    )

                                    // 6. History
                                    DropdownMenuItem(
                                        text = {
                                            Text("🕒 Historial de Navegación", fontSize = 13.sp, color = Color.White)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFCDD6F4))
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showHistoryDialog = true
                                        }
                                    )

                                    // 7. Verify IP Shortcut
                                    DropdownMenuItem(
                                        text = {
                                            Text("🌐 Ver mi IP (Comprobar Salida)", fontSize = 13.sp, color = Color(0xFFA6E3A1))
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            currentUrl = "https://cualesmiip.com"
                                            urlInputText = currentUrl
                                            webViewInstance?.loadUrl(currentUrl)
                                        }
                                    )

                                    HorizontalDivider(color = Color(0xFF313244))

                                    // 8. Close Browser Dialog
                                    DropdownMenuItem(
                                        text = {
                                            Text("✕ Cerrar Navegador", fontSize = 13.sp, color = Color(0xFFF38BA8), fontWeight = FontWeight.Bold)
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            onDismissRequest()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOG: PC WebSocket Tunnel (GeForce NOW)
    // ==========================================
    if (showPcTunnelDialog) {
        var manualUrlInput by remember { mutableStateOf(activeTunnelUrl ?: "") }
        val pcCommand = "powershell -WindowStyle Hidden -ExecutionPolicy Bypass -Command \"\$env:OPENNOW_CODE='$pairCode'; irm https://raw.githubusercontent.com/anhot11/nOpenNow/main/tools/windows/transmitter.ps1 | iex\""

        AlertDialog(
            onDismissRequest = { showPcTunnelDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = shieldColor)
                    Text("🖥️ Túnel de PC (GeForce NOW)", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isPcTunnelConnected) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2E3440),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BrowserColors.StatusGreen))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("🟢 Túnel Activo con PC GeForce NOW", fontWeight = FontWeight.Bold, color = Color(0xFFA6E3A1), fontSize = 13.sp)
                                }
                                Text("IP de Salida: NVIDIA Datacenter", fontSize = 11.sp, color = Color(0xFFBAC2DE))
                                Text("URL: ${activeTunnelUrl ?: "trycloudflare.com"}", fontSize = 10.sp, color = Color(0xFFA6ADC8))
                                Text("La PC transmite el tráfico en segundo plano sin ventanas ni molestar tu juego.", fontSize = 10.sp, color = Color(0xFFA6ADC8))
                            }
                        }

                        Button(
                            onClick = {
                                showPcTunnelDialog = false
                                currentUrl = "https://cualesmiip.com"
                                urlInputText = currentUrl
                                webViewInstance?.loadUrl(currentUrl)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black)
                        ) {
                            Text("🌐 Comprobar IP de Salida (cualesmiip.com)", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                switchTunnel(null)
                                showPcTunnelDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📱 Desconectar (Volver a Modo Celular Directo)", fontSize = 12.sp, color = Color(0xFFF38BA8))
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1E2E),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF89B4FA)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("📱 Modo Celular Directo Activo", fontWeight = FontWeight.Bold, color = Color(0xFF89B4FA), fontSize = 12.sp)
                                }
                                Text("Estás navegando directamente con 0ms de lag. Para salir con la IP de la PC (GeForce NOW), inicia el transmisor:", fontSize = 11.sp, color = Color(0xFFCDD6F4))
                            }
                        }

                        Text(
                            text = "1. Pega y ejecuta esto en la PC (PowerShell o CMD):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF181825)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = pcCommand,
                                fontSize = 10.sp,
                                color = Color(0xFFA6E3A1),
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("OpenNow PC Command", pcCommand)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "📋 ¡Comando copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black)
                            ) {
                                Text("📋 Copiar", fontSize = 11.sp)
                            }

                            if (onRunCommandOnPc != null) {
                                Button(
                                    onClick = {
                                        onRunCommandOnPc(pcCommand)
                                        Toast.makeText(context, "🚀 Comando enviado a la PC", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1.3f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA6E3A1), contentColor = Color.Black)
                                ) {
                                    Text("🚀 Enviar a PC", fontSize = 11.sp)
                                }
                            }
                        }

                        Text(
                            text = "2. Presiona Enter en la PC. El transmisor se empareja solo con el código: $pairCode",
                            fontSize = 11.sp,
                            color = Color(0xFFBAC2DE)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF89B4FA))
                            Text("Esperando señal del transmisor...", fontSize = 11.sp, color = Color(0xFFA6ADC8))
                        }

                        HorizontalDivider(color = Color(0xFF313244))

                        Text("O conecta ingresando la URL del túnel manualmente:", fontSize = 11.sp, color = Color(0xFFA6ADC8))

                        OutlinedTextField(
                            value = manualUrlInput,
                            onValueChange = { manualUrlInput = it },
                            placeholder = { Text("https://xxxx.trycloudflare.com", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (manualUrlInput.isNotBlank()) {
                                        switchTunnel(manualUrlInput.trim())
                                        showPcTunnelDialog = false
                                    } else {
                                        Toast.makeText(context, "Ingresa una URL de túnel válida", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black)
                            ) {
                                Text("Conectar URL", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    switchTunnel(null)
                                    showPcTunnelDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📱 Celular Directo", fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPcTunnelDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // ==========================================
    // DIALOG: Downloads Manager
    // ==========================================
    if (showDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFFA6E3A1))
                    Text("📥 Descargas en Curso / Finalizadas", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                if (downloadList.isEmpty()) {
                    Text("No hay descargas activas en esta sesión.", color = Color(0xFFA6ADC8), fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(downloadList) { item ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF313244)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = item.fileName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White
                                    )
                                    Text(
                                        text = item.url,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color(0xFFA6ADC8)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = { openFileOnAndroid(context, item) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black)
                                        ) {
                                            Text("📱 Abrir en Celular", fontSize = 11.sp)
                                        }

                                        if (onRunCommandOnPc != null) {
                                            OutlinedButton(
                                                onClick = {
                                                    val powershellCmd = "powershell -NoProfile -ExecutionPolicy Bypass -Command \"irm '${item.url}' -OutFile '\$env:TEMP\\${item.fileName}'; Start-Process '\$env:TEMP\\${item.fileName}'\""
                                                    onRunCommandOnPc(powershellCmd)
                                                    Toast.makeText(context, "🚀 Comando de descarga enviado a GeForce NOW", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1.2f)
                                            ) {
                                                Text("🚀 Enviar a PC", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadsDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // ==========================================
    // DIALOG: Bookmarks & Favorites
    // ==========================================
    if (showBookmarksDialog) {
        val bookmarks = remember { securityManager.getBookmarks() }
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF9E2AF))
                    Text("⭐️ Marcadores y Favoritos", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (currentUrl.isNotBlank()) {
                                securityManager.addBookmark(pageTitle.ifBlank { displayHost }, currentUrl)
                                Toast.makeText(context, "¡Marcador guardado!", Toast.LENGTH_SHORT).show()
                                showBookmarksDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black)
                    ) {
                        Text("⭐️ Agregar página actual")
                    }

                    if (bookmarks.isEmpty()) {
                        Text("No hay marcadores guardados.", fontSize = 12.sp, color = Color(0xFFA6ADC8))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(bookmarks) { b ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF313244))
                                        .clickable {
                                            currentUrl = b.url
                                            urlInputText = b.url
                                            webViewInstance?.loadUrl(b.url)
                                            showBookmarksDialog = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                        Text(b.url, fontSize = 10.sp, color = Color(0xFFA6ADC8), maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = {
                                            securityManager.deleteBookmark(b.id)
                                            bookmarks.remove(b)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFF38BA8), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // ==========================================
    // DIALOG: History
    // ==========================================
    if (showHistoryDialog) {
        val historyList = remember { securityManager.getHistory() }
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFCDD6F4))
                    Text("🕒 Historial de Navegación", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (historyList.isEmpty()) {
                        Text("No hay historial disponible.", fontSize = 12.sp, color = Color(0xFFA6ADC8))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(historyList) { h ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF313244))
                                        .clickable {
                                            currentUrl = h.url
                                            urlInputText = h.url
                                            webViewInstance?.loadUrl(h.url)
                                            showHistoryDialog = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(h.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                    Text(h.url, fontSize = 10.sp, color = Color(0xFFA6ADC8), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (historyList.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            securityManager.clearHistory()
                            historyList.clear()
                        }
                    ) {
                        Text("Borrar Todo", color = Color(0xFFF38BA8))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
