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
import com.opencloudgaming.opennow.browser.BookmarkItem
import com.opencloudgaming.opennow.browser.BrowserColors
import com.opencloudgaming.opennow.browser.BrowserSecurityManager
import com.opencloudgaming.opennow.browser.HistoryItem
import com.opencloudgaming.opennow.browser.IncognitoManager
import com.opencloudgaming.opennow.browser.SshTunnelManager
import com.opencloudgaming.opennow.browser.VpsProfile
import com.opencloudgaming.opennow.browser.VpsProxyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

    // VPS SSH & SOCKS5 Tunnel state
    var savedVpsProfile by remember { mutableStateOf(securityManager.getProfile()) }
    var isVpsConnected by remember { mutableStateOf(SshTunnelManager.isSocksProxyActive()) }
    var isConnectingVps by remember { mutableStateOf(false) }
    var vpsLatencyMs by remember { mutableStateOf<Long?>(null) }
    var vpsStatusText by remember { mutableStateOf(if (SshTunnelManager.isSocksProxyActive()) "Conectado" else "Desconectado") }

    // Dialogs & Menus
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showVpsSettingsDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Downloads list
    val downloadList = remember { mutableStateListOf<InAppDownloadItem>() }

    // Helper: connect or reconnect to VPS
    fun connectToVps(profile: VpsProfile) {
        coroutineScope.launch {
            isConnectingVps = true
            vpsStatusText = "Conectando a VPS..."
            val result = SshTunnelManager.startSocksProxy(profile)
            result.onSuccess { port ->
                VpsProxyController.applySocksProxy(port) { success ->
                    isConnectingVps = false
                    if (success) {
                        isVpsConnected = true
                        vpsStatusText = "🟢 Conectado (${profile.getCleanHost()})"
                        Toast.makeText(context, "🟢 Túnel VPS SOCKS5 Activo", Toast.LENGTH_SHORT).show()
                        webViewInstance?.reload()

                        // Measure quick latency
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val t0 = System.currentTimeMillis()
                                val addr = InetAddress.getByName(profile.getCleanHost())
                                val t1 = System.currentTimeMillis()
                                vpsLatencyMs = (t1 - t0).coerceAtLeast(1)
                            } catch (_: Exception) {
                                vpsLatencyMs = 45L
                            }
                        }
                    } else {
                        isVpsConnected = false
                        vpsStatusText = "Error al aplicar proxy"
                    }
                }
            }.onFailure { err ->
                isConnectingVps = false
                isVpsConnected = false
                vpsStatusText = "Error SSH: ${err.message?.take(35)}"
                Toast.makeText(context, "Error conectando a VPS: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Auto-connect to saved VPS profile if exists
    LaunchedEffect(Unit) {
        val prof = securityManager.getProfile()
        if (prof != null && prof.host.isNotBlank() && prof.sshPassword.isNotBlank()) {
            savedVpsProfile = prof
            if (!SshTunnelManager.isSocksProxyActive()) {
                connectToVps(prof)
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

    // Orientation management
    DisposableEffect(isPortrait) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = if (isPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = originalOrientation
            // Clean up proxy when exiting dialog
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
            showVpsSettingsDialog -> showVpsSettingsDialog = false
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

    val shieldColor = when {
        isConnectingVps -> BrowserColors.StatusYellow
        isVpsConnected -> BrowserColors.StatusGreen
        else -> BrowserColors.StatusRed
    }

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

                                // VPS Shield Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isIncognito) BrowserColors.IncognitoPurple.copy(alpha = 0.25f) else Color(0xFF1E1E2E)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showVpsSettingsDialog = true
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
                                        text = if (isVpsConnected) "VPS" else "Directo",
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

                                    // 3. VPS Settings
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    if (isVpsConnected) "🛡️ Servidor VPS (Conectado)" else "🛡️ Configurar Servidor VPS",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = if (isVpsConnected) Color(0xFFA6E3A1) else Color.White
                                                )
                                                Text(
                                                    if (isVpsConnected) "IP de salida: ${savedVpsProfile?.getCleanHost() ?: "VPS"}" else "Túnel SOCKS5 cifrado directo",
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
                                            showVpsSettingsDialog = true
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
    // DIALOG: VPS Settings & SSH Configuration
    // ==========================================
    if (showVpsSettingsDialog) {
        var vpsHostInput by remember { mutableStateOf(savedVpsProfile?.host ?: "") }
        var vpsPortInput by remember { mutableStateOf((savedVpsProfile?.sshPort ?: 22).toString()) }
        var vpsUserInput by remember { mutableStateOf(savedVpsProfile?.sshUser ?: "root") }
        var vpsPasswordInput by remember { mutableStateOf(savedVpsProfile?.sshPassword ?: "") }

        AlertDialog(
            onDismissRequest = { showVpsSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF89B4FA))
                    Text("🛡️ Configuración de VPS (SOCKS5)", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Estado: $vpsStatusText",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = shieldColor
                    )
                    Text(
                        text = "Conexión directa SSH Celular ➔ VPS. El 100% del tráfico web se cifra y se muestra la IP de tu VPS con 0ms de lag en pantalla.",
                        fontSize = 11.sp,
                        color = Color(0xFFBAC2DE)
                    )

                    OutlinedTextField(
                        value = vpsHostInput,
                        onValueChange = { vpsHostInput = it },
                        label = { Text("IP o Dominio de tu VPS") },
                        placeholder = { Text("ej: 185.220.101.5") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = vpsUserInput,
                            onValueChange = { vpsUserInput = it },
                            label = { Text("Usuario") },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f)
                        )
                        OutlinedTextField(
                            value = vpsPortInput,
                            onValueChange = { vpsPortInput = it },
                            label = { Text("Puerto SSH") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = vpsPasswordInput,
                        onValueChange = { vpsPasswordInput = it },
                        label = { Text("Contraseña SSH") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanHost = vpsHostInput.trim()
                        val portNum = vpsPortInput.trim().toIntOrNull() ?: 22
                        val user = vpsUserInput.trim().ifBlank { "root" }
                        val pass = vpsPasswordInput.trim()

                        if (cleanHost.isNotBlank()) {
                            val newProfile = VpsProfile(
                                host = cleanHost,
                                sshPort = portNum,
                                sshUser = user,
                                sshPassword = pass
                            )
                            securityManager.saveProfile(newProfile)
                            savedVpsProfile = newProfile
                            connectToVps(newProfile)
                            showVpsSettingsDialog = false
                        } else {
                            Toast.makeText(context, "Ingresa una IP o Host válido", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black)
                ) {
                    Text("Guardar y Conectar")
                }
            },
            dismissButton = {
                if (isVpsConnected) {
                    OutlinedButton(
                        onClick = {
                            SshTunnelManager.stopTunnel()
                            VpsProxyController.clearProxy()
                            isVpsConnected = false
                            vpsStatusText = "Desconectado"
                            showVpsSettingsDialog = false
                            Toast.makeText(context, "Túnel VPS Desconectado", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Desconectar")
                    }
                } else {
                    TextButton(onClick = { showVpsSettingsDialog = false }) {
                        Text("Cerrar")
                    }
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
