package com.opencloudgaming.opennow

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

private fun applyProxyOverride(proxyAddress: String?, onComplete: (Boolean) -> Unit) {
    try {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.w("OpenNowBrowser", "PROXY_OVERRIDE not supported on this WebView version")
            onComplete(false)
            return
        }
        val controller = ProxyController.getInstance()
        if (proxyAddress.isNullOrBlank()) {
            controller.clearProxyOverride({ it.run() }) {
                onComplete(true)
            }
        } else {
            val config = ProxyConfig.Builder()
                .addProxyRule(proxyAddress.trim())
                .build()
            controller.setProxyOverride(config, { it.run() }) {
                Log.d("OpenNowBrowser", "Proxy override set to: $proxyAddress")
                onComplete(true)
            }
        }
    } catch (e: Exception) {
        Log.e("OpenNowBrowser", "Failed to set proxy override", e)
        onComplete(false)
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
    val coroutineScope = rememberCoroutineScope()

    var isPortrait by rememberSaveable { mutableStateOf(true) }
    var currentUrl by rememberSaveable { mutableStateOf(initialUrl) }
    var urlInputText by rememberSaveable { mutableStateOf(initialUrl) }
    var pageTitle by rememberSaveable { mutableStateOf("Navegador") }
    var pageLoadingProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(false) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val downloadList = remember { mutableStateListOf<InAppDownloadItem>() }
    var downloadManagerOpen by rememberSaveable { mutableStateOf(false) }
    var pendingLaunchItem by remember { mutableStateOf<InAppDownloadItem?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Tunnel / Proxy state
    val tunnelToken = remember(sessionId) {
        sessionId?.takeLast(8) ?: "opennow"
    }
    var activeProxyHostPort by rememberSaveable { mutableStateOf<String?>(null) }
    var proxyStatusText by remember { mutableStateOf("Comprobando túnel...") }
    var proxyDialogOpen by remember { mutableStateOf(false) }
    var manualProxyInput by remember { mutableStateOf("") }
    var isCheckingProxy by remember { mutableStateOf(false) }

    // Auto-discover tunnel from ntfy relay on launch
    fun checkTunnelStatus() {
        coroutineScope.launch {
            isCheckingProxy = true
            val proxyEndpoint = withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://ntfy.sh/opennow_proxy_$tunnelToken/raw")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.requestMethod = "GET"
                    if (conn.responseCode == 200) {
                        BufferedReader(InputStreamReader(conn.inputStream)).use { it.readLine()?.trim() }
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            isCheckingProxy = false
            if (!proxyEndpoint.isNullOrBlank() && proxyEndpoint.contains(":")) {
                activeProxyHostPort = proxyEndpoint
                proxyStatusText = "🟢 Conectado a la PC ($proxyEndpoint)"
                applyProxyOverride(proxyEndpoint) { success ->
                    if (success) {
                        Toast.makeText(context, "🟢 Túnel activo con GeForce NOW: $proxyEndpoint", Toast.LENGTH_SHORT).show()
                        webViewInstance?.reload()
                    }
                }
            } else {
                proxyStatusText = "🟡 Directo (Sin túnel de PC activo)"
            }
        }
    }

    LaunchedEffect(tunnelToken) {
        checkTunnelStatus()
    }

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
            applyProxyOverride(null) {}
        }
    }

    BackHandler {
        if (downloadManagerOpen) {
            downloadManagerOpen = false
        } else if (proxyDialogOpen) {
            proxyDialogOpen = false
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onDismissRequest()
        }
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
                .background(Color(0xFF1E1E2E))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Navigation Bar
                Surface(
                    color = Color(0xFF181825),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Back Button
                            IconButton(
                                onClick = { webViewInstance?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    "◀",
                                    color = if (canGoBack) Color.White else Color(0xFF585B70),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            // Forward Button
                            IconButton(
                                onClick = { webViewInstance?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    "▶",
                                    color = if (canGoForward) Color.White else Color(0xFF585B70),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            // Reload Button
                            IconButton(
                                onClick = { webViewInstance?.reload() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    if (isPageLoading) "✕" else "↻",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            // URL & Search Box
                            OutlinedTextField(
                                value = urlInputText,
                                onValueChange = { urlInputText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF313244),
                                    unfocusedContainerColor = Color(0xFF313244),
                                    focusedBorderColor = Color(0xFF89B4FA),
                                    unfocusedBorderColor = Color(0xFF45475A),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color(0xFFCDD6F4)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
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
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            // Downloads Button
                            IconButton(
                                onClick = { downloadManagerOpen = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("📥", color = Color(0xFFA6E3A1), style = MaterialTheme.typography.titleMedium)
                            }

                            // 3-Dots Menu Button
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("⋮", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    modifier = Modifier.background(Color(0xFF181825))
                                ) {
                                    // Rotation Toggle
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_screen_rotation),
                                                    contentDescription = "Girar Pantalla",
                                                    tint = Color(0xFF89B4FA),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = if (isPortrait) "Vista Horizontal" else "Vista Vertical",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        },
                                        onClick = {
                                            isPortrait = !isPortrait
                                            menuExpanded = false
                                        }
                                    )

                                    // Tunnel / Proxy Config
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(if (activeProxyHostPort != null) "🟢" else "🟡")
                                                Text(
                                                    text = if (activeProxyHostPort != null) "Túnel PC Activo" else "Conectar Túnel PC",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            proxyDialogOpen = true
                                        }
                                    )

                                    // Data Saver
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(if (browserLowQualityEnabled) "⚡" else "💤")
                                                Text(
                                                    text = if (browserLowQualityEnabled) "Ahorro 2Mbps: Activado" else "Ahorro 2Mbps: Desactivado",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        },
                                        onClick = {
                                            onBrowserLowQualityToggle()
                                            menuExpanded = false
                                        }
                                    )

                                    HorizontalDivider(color = Color(0xFF313244))

                                    // Quick shortcut: YouTube
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("▶", color = Color(0xFFF38BA8))
                                                Text("YouTube Móvil", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            currentUrl = "https://m.youtube.com"
                                            urlInputText = currentUrl
                                            webViewInstance?.loadUrl(currentUrl)
                                        }
                                    )

                                    // Quick shortcut: Check IP
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("🌐", color = Color(0xFFA6E3A1))
                                                Text("Ver mi IP (Comprobar PC)", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            currentUrl = "https://cualesmiip.com"
                                            urlInputText = currentUrl
                                            webViewInstance?.loadUrl(currentUrl)
                                        }
                                    )
                                }
                            }

                            // Close Button
                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    "✕",
                                    color = Color(0xFFF38BA8),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Tunnel Status Ribbon
                        Surface(
                            color = if (activeProxyHostPort != null) Color(0xFF1E3A2F) else Color(0xFF2A2A3C),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { proxyDialogOpen = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (activeProxyHostPort != null) "🔒 Conexión enrutada por la PC (GeForce NOW: $activeProxyHostPort)" else "⚠️ Modo directo (Toca para conectar túnel de la PC)",
                                    color = if (activeProxyHostPort != null) Color(0xFFA6E3A1) else Color(0xFFF9E2AF),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Ajustes",
                                    color = Color(0xFF89B4FA),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Loading Progress Indicator
                        if (isPageLoading && pageLoadingProgress in 0.01f..0.99f) {
                            LinearProgressIndicator(
                                progress = { pageLoadingProgress },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = Color(0xFF89B4FA),
                                trackColor = Color(0xFF313244)
                            )
                        }
                    }
                }

                // WebView Container
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
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    mediaPlaybackRequiresUserGesture = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
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
                                    pendingLaunchItem = item

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
            }
        }
    }

    // Tunnel / Proxy Config Dialog
    if (proxyDialogOpen) {
        AlertDialog(
            onDismissRequest = { proxyDialogOpen = false },
            title = {
                Text("🌐 Conexión y Túnel de la PC (GeForce NOW)")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Estado: $proxyStatusText",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (activeProxyHostPort != null) Color(0xFFA6E3A1) else Color(0xFFF9E2AF)
                    )
                    Text(
                        text = "El túnel permite que el navegador móvil navegue usando la conexión, IP y velocidad de la PC en GeForce NOW.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBAC2DE)
                    )

                    OutlinedTextField(
                        value = manualProxyInput,
                        onValueChange = { manualProxyInput = it },
                        label = { Text("Proxy manual (ej: bore.pub:12345 o IP:puerto)") },
                        placeholder = { Text(activeProxyHostPort ?: "bore.pub:xxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Button(
                        onClick = {
                            if (manualProxyInput.isNotBlank()) {
                                activeProxyHostPort = manualProxyInput.trim()
                                proxyStatusText = "🟢 Conectado a ($manualProxyInput)"
                                applyProxyOverride(manualProxyInput.trim()) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Proxy aplicado", Toast.LENGTH_SHORT).show()
                                        webViewInstance?.reload()
                                        proxyDialogOpen = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aplicar Proxy Manual")
                    }

                    if (onRunCommandOnPc != null) {
                        OutlinedButton(
                            onClick = {
                                val cmd = "start \"\" \"I:\\nOpenNow_Browser\\nOpenNow-Browser.bat\" tunnel $tunnelToken"
                                onRunCommandOnPc(cmd)
                                Toast.makeText(context, "🚀 Comando de túnel enviado a la PC...", Toast.LENGTH_SHORT).show()
                                checkTunnelStatus()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🚀 Iniciar Túnel en la PC")
                        }
                    }

                    OutlinedButton(
                        onClick = { checkTunnelStatus() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCheckingProxy) "Buscando..." else "🔄 Auto-Detectar Túnel de la PC")
                    }

                    if (activeProxyHostPort != null) {
                        TextButton(
                            onClick = {
                                activeProxyHostPort = null
                                proxyStatusText = "🟡 Directo (Sin túnel)"
                                applyProxyOverride(null) {
                                    webViewInstance?.reload()
                                    proxyDialogOpen = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Desconectar Túnel (Navegación Directa)", color = Color(0xFFF38BA8))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { proxyDialogOpen = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Download Manager Dialog
    if (downloadManagerOpen) {
        Dialog(
            onDismissRequest = { downloadManagerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E2E).copy(alpha = 0.96f))
                    .padding(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181825)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📥 Administrador de Descargas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            IconButton(onClick = { downloadManagerOpen = false }) {
                                Text("✕", color = Color(0xFFBAC2DE), style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        HorizontalDivider(color = Color(0xFF313244), modifier = Modifier.padding(vertical = 8.dp))

                        if (downloadList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No hay descargas registradas en esta sesión.",
                                    color = Color(0xFF6C7086),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(downloadList, key = { it.id }) { item ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF313244)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.fileName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (item.isExecutable) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFA6E3A1).copy(alpha = 0.2f),
                                                        modifier = Modifier.padding(start = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = "PROGRAMA",
                                                            color = Color(0xFFA6E3A1),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = item.url,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF6C7086),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { pendingLaunchItem = item },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("🚀 Abrir en PC", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                }

                                                OutlinedButton(
                                                    onClick = { openFileOnAndroid(context, item) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCDD6F4))
                                                ) {
                                                    Text("📱 Celular", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Launch in PC Dialog
    pendingLaunchItem?.let { item ->
        val safeFileName = item.fileName
        val windowsDownloadPath = "I:\\nOpenNow_Browser\\Downloads\\$safeFileName"
        val cmdRunOnly = "start \"\" \"$windowsDownloadPath\""
        val powershellFetchAndRun = "\"I:\\Apps\\SalsaNOW SilentApps\\Powershell\\pwsh.exe\" -c \"irm '${item.url}' -OutFile '$windowsDownloadPath'; start '$windowsDownloadPath'\""

        AlertDialog(
            onDismissRequest = { pendingLaunchItem = null },
            title = {
                Text("🚀 Abrir en la PC (GeForce NOW)")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Archivo: $safeFileName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "El programa se ejecutará en Windows GFN fuera del navegador (Disco I:):",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBAC2DE)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF11111B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cmdRunOnly,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA6E3A1),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onRunCommandOnPc != null) {
                        Button(
                            onClick = {
                                onRunCommandOnPc(cmdRunOnly)
                                Toast.makeText(context, "🚀 ¡Comando enviado a la PC! Abriendo...", Toast.LENGTH_SHORT).show()
                                pendingLaunchItem = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA6E3A1), contentColor = Color.Black)
                        ) {
                            Text("🚀 Enviar y Ejecutar en PC", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            if (onRunCommandOnPc != null) {
                                onRunCommandOnPc(powershellFetchAndRun)
                                Toast.makeText(context, "🚀 Descargando en I:\\ a 1000 Mbps y abriendo...", Toast.LENGTH_SHORT).show()
                            }
                            pendingLaunchItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black)
                    ) {
                        Text("Descargar en I:\\ a 1000 Mbps")
                    }
                }
            },
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { openFileOnAndroid(context, item) }) {
                        Text("📱 Celular", color = Color(0xFFBAC2DE))
                    }
                    TextButton(onClick = { pendingLaunchItem = null }) {
                        Text("Cerrar", color = Color(0xFF6C7086))
                    }
                }
            }
        )
    }
}
