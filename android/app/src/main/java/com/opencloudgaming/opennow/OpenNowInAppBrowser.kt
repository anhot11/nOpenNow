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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * Data model representing a downloaded or captured file.
 */
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
                item.fileName.endsWith(".png", true) -> "image/png"
                item.fileName.endsWith(".jpg", true) || item.fileName.endsWith(".jpeg", true) -> "image/jpeg"
                else -> "*/*"
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val dmIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dmIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se encontró una aplicación para abrir este archivo en Android.", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Enhanced full-screen in-app browser dialog for OpenNOW.
 * Includes integrated Download Manager (allowing opening and launching programs
 * on PC or mobile), default vertical orientation with rotation toggle,
 * history navigation, and prominent 'X' button to return to game.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenNowInAppBrowserDialog(
    initialUrl: String = "https://www.google.com",
    lowQualityActive: Boolean = true,
    onRunCommandOnPc: ((String) -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isPortrait by rememberSaveable { mutableStateOf(true) }
    var activeTab by rememberSaveable { mutableStateOf("web") } // "web" or "downloads"

    var currentUrl by remember { mutableStateOf(initialUrl) }
    var inputUrl by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Download Manager state
    val downloads = remember {
        mutableStateListOf<InAppDownloadItem>().apply {
            // Default sample helper items for SalsaNOW/Windows tools
            add(
                InAppDownloadItem(
                    fileName = "nOpenNow-Browser.bat",
                    url = "https://github.com/anhot11/nOpenNow/releases/latest/download/nOpenNow-Browser.bat",
                    mimeType = "application/x-bat",
                    isExecutable = true,
                )
            )
            add(
                InAppDownloadItem(
                    fileName = "brave-portable-setup.exe",
                    url = "https://github.com/portapps/brave-portable/releases/download/1.92.134-100/brave-portable-win64-1.92.134-100-setup.exe",
                    mimeType = "application/x-msdownload",
                    isExecutable = true,
                )
            )
        }
    }
    var pendingLaunchItem by remember { mutableStateOf<InAppDownloadItem?>(null) }
    var directDownloadInput by remember { mutableStateOf("") }

    // Orientation management: default vertical, restore landscape on exit
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Hardware Back button: if in downloads tab, return to web; if in web with history, go back; else close
    BackHandler(enabled = true) {
        when {
            activeTab == "downloads" -> activeTab = "web"
            webViewRef?.canGoBack() == true -> webViewRef?.goBack()
            else -> onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF181825),
            contentColor = Color.White
        ) {
            Column(Modifier.fillMaxSize()) {
                // 1. Top Header & Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E2E))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Prominent 'X' button: returns directly to game
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear),
                            contentDescription = "Cerrar y volver al juego",
                            tint = Color(0xFFF38BA8),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (activeTab == "web") {
                        // Web History Back (<)
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            enabled = canGoBack,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Atrás",
                                tint = if (canGoBack) Color.White else Color(0xFF6C7086),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Omnibox (Search / URL)
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val trimmed = inputUrl.trim()
                                    val target = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                                        if (trimmed.contains(".") && !trimmed.contains(" ")) {
                                            "https://$trimmed"
                                        } else {
                                            "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
                                        }
                                    } else {
                                        trimmed
                                    }
                                    currentUrl = target
                                    inputUrl = target
                                    webViewRef?.loadUrl(target)
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF89B4FA),
                                unfocusedBorderColor = Color(0xFF45475A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF181825),
                                unfocusedContainerColor = Color(0xFF181825)
                            ),
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(23.dp),
                            trailingIcon = {
                                if (inputUrl.isNotBlank()) {
                                    IconButton(onClick = { inputUrl = "" }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_clear),
                                            contentDescription = "Borrar",
                                            tint = Color(0xFF6C7086),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        )

                        // Reload Button
                        IconButton(
                            onClick = { webViewRef?.reload() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = "Recargar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // In Downloads tab, show title
                        Text(
                            text = "Administrador de Descargas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                    }

                    // Download Manager Tab Toggle Button (with count badge)
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(
                            onClick = {
                                activeTab = if (activeTab == "web") "downloads" else "web"
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (activeTab == "downloads") R.drawable.ic_public else R.drawable.ic_download
                                ),
                                contentDescription = if (activeTab == "downloads") "Ver Web" else "Ver Descargas",
                                tint = if (activeTab == "downloads") Color(0xFFA6E3A1) else Color(0xFF89B4FA),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (downloads.isNotEmpty() && activeTab == "web") {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF38BA8),
                                modifier = Modifier
                                    .padding(top = 2.dp, end = 2.dp)
                                    .size(16.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = downloads.size.coerceAtMost(99).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }

                    // 3-Dots Menu Button (Vertical orientation, Recommendations like YouTube, Data Savings, etc.)
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = "Menú de opciones",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF1E1E2E))
                                .border(1.dp, Color(0xFF313244), RoundedCornerShape(12.dp))
                                .width(280.dp)
                        ) {
                            // 1. Orientación de Pantalla (Vertical / Horizontal)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = if (isPortrait) "Rotar a Horizontal" else "Rotar a Vertical",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = if (isPortrait) "Modo actual: Vertical" else "Modo actual: Horizontal",
                                            color = Color(0xFFA6ADC8),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_screen_rotation),
                                        contentDescription = null,
                                        tint = if (isPortrait) Color(0xFF89B4FA) else Color(0xFFA6E3A1),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    isPortrait = !isPortrait
                                    activity?.requestedOrientation = if (isPortrait) {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                }
                            )

                            HorizontalDivider(color = Color(0xFF313244), modifier = Modifier.padding(vertical = 4.dp))

                            // 2. Estado de Ahorro GFN (2 Mbps)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = if (lowQualityActive) "Ahorro GFN Activo (2 Mbps)" else "Calidad GFN Normal",
                                            color = Color(0xFFA6E3A1),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Ahorra batería y datos mientras navegas",
                                            color = Color(0xFFA6ADC8),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Surface(
                                        modifier = Modifier.size(10.dp),
                                        shape = CircleShape,
                                        color = if (lowQualityActive) Color(0xFFA6E3A1) else Color(0xFF89B4FA)
                                    ) {}
                                },
                                onClick = {
                                    showMenu = false
                                }
                            )

                            HorizontalDivider(color = Color(0xFF313244), modifier = Modifier.padding(vertical = 4.dp))

                            // 3. Recomendaciones (YouTube, Google, Steam, etc.)
                            Text(
                                text = "RECOMENDACIONES",
                                color = Color(0xFF89B4FA),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )

                            val recommendations = listOf(
                                Triple("YouTube", "https://www.youtube.com", "Videos y música"),
                                Triple("Google", "https://www.google.com", "Búsqueda web"),
                                Triple("GitHub", "https://github.com/anhot11/nOpenNow", "Repositorio nOpenNow"),
                                Triple("Steam", "https://store.steampowered.com", "Tienda de juegos"),
                                Triple("Descargar BAT", "https://github.com/anhot11/nOpenNow/releases/latest/download/nOpenNow-Browser.bat", "Lanzador Windows"),
                            )

                            recommendations.forEach { (title, url, subtitle) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = title,
                                                color = Color(0xFFCDD6F4),
                                                fontWeight = FontWeight.Medium,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = subtitle,
                                                color = Color(0xFF6C7086),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        activeTab = "web"
                                        inputUrl = url
                                        currentUrl = url
                                        webViewRef?.loadUrl(url)
                                    }
                                )
                            }

                            HorizontalDivider(color = Color(0xFF313244), modifier = Modifier.padding(vertical = 4.dp))

                            // 4. Administrador de Descargas
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (activeTab == "downloads") "Volver a la Web" else "Descargas (${downloads.size})",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(
                                            if (activeTab == "downloads") R.drawable.ic_public else R.drawable.ic_download
                                        ),
                                        contentDescription = null,
                                        tint = Color(0xFF89B4FA),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    activeTab = if (activeTab == "downloads") "web" else "downloads"
                                }
                            )

                            // 5. Copiar enlace actual
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Copiar enlace actual",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_save),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                                    Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // 2. Progress Bar (during web loading)
                if (activeTab == "web" && isLoading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color(0xFF89B4FA),
                        trackColor = Color.Transparent
                    )
                }

                // 3. Content Area: Web Browser vs Download Manager
                if (activeTab == "web") {

                    // Full-Screen WebView
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                                    }

                                    // Intercept downloads and send them to the Download Manager
                                    setDownloadListener { url, _, contentDisposition, mimetype, contentLength ->
                                        val guessedFileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                        val isExe = guessedFileName.endsWith(".exe", true) ||
                                            guessedFileName.endsWith(".bat", true) ||
                                            guessedFileName.endsWith(".msi", true) ||
                                            guessedFileName.endsWith(".cmd", true)

                                        val newDownload = InAppDownloadItem(
                                            fileName = guessedFileName,
                                            url = url,
                                            mimeType = mimetype.orEmpty(),
                                            totalBytes = contentLength,
                                            isExecutable = isExe,
                                        )
                                        downloads.add(0, newDownload)

                                        // Trigger system download manager for background persistence
                                        try {
                                            val request = DownloadManager.Request(Uri.parse(url)).apply {
                                                setTitle(guessedFileName)
                                                setDescription("Descargado desde nOpenNow")
                                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "nOpenNow/$guessedFileName")
                                                setAllowedOverMetered(true)
                                                setAllowedOverRoaming(true)
                                            }
                                            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                                            dm?.enqueue(request)
                                        } catch (_: Exception) {}

                                        Toast.makeText(
                                            ctx,
                                            "Descarga agregada: $guessedFileName. Ver en pestaña Descargas.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            isLoading = true
                                            url?.let {
                                                currentUrl = it
                                                inputUrl = it
                                            }
                                            canGoBack = canGoBack()
                                            canGoForward = canGoForward()
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            isLoading = false
                                            url?.let {
                                                currentUrl = it
                                                inputUrl = it
                                            }
                                            canGoBack = canGoBack()
                                            canGoForward = canGoForward()
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            return false
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress / 100f
                                        }
                                    }

                                    loadUrl(initialUrl)
                                    webViewRef = this
                                }
                            },
                            update = { webView ->
                                webViewRef = webView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // 5. Download Manager UI ("Apartado de Descargas")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Direct Download Input Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = directDownloadInput,
                                onValueChange = { directDownloadInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Pegar enlace directo a descargar...", style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF89B4FA),
                                    unfocusedBorderColor = Color(0xFF45475A),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Button(
                                onClick = {
                                    val trimmed = directDownloadInput.trim()
                                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                        val guessedName = URLUtil.guessFileName(trimmed, null, null)
                                        val isExe = guessedName.endsWith(".exe", true) ||
                                            guessedName.endsWith(".bat", true) ||
                                            guessedName.endsWith(".msi", true)
                                        val item = InAppDownloadItem(
                                            fileName = guessedName,
                                            url = trimmed,
                                            isExecutable = isExe,
                                        )
                                        downloads.add(0, item)
                                        directDownloadInput = ""
                                        Toast.makeText(context, "Descarga agregada: $guessedName", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Ingresa un enlace http:// o https:// válido", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black)
                            ) {
                                Text("Agregar")
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Downloads List
                        if (downloads.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_download),
                                        contentDescription = null,
                                        tint = Color(0xFF6C7086),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "No hay descargas registradas",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFBAC2DE)
                                    )
                                    Text(
                                        text = "Cuando toques enlaces de descarga en el navegador, aparecerán aquí para ejecutarse en la PC o en tu celular.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF6C7086),
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(downloads, key = { it.id }) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF313244))
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                // Icon based on type
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (item.isExecutable) Color(0xFF89B4FA).copy(alpha = 0.2f) else Color(0xFFA6E3A1).copy(alpha = 0.2f),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = if (item.isExecutable) "EXE" else "FILE",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (item.isExecutable) Color(0xFF89B4FA) else Color(0xFFA6E3A1)
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.fileName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val dateStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                                                    Text(
                                                        text = if (item.totalBytes > 0) {
                                                            "${item.totalBytes / 1024 / 1024} MB · $dateStr"
                                                        } else {
                                                            "Listo para abrir · $dateStr"
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color(0xFFBAC2DE)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { downloads.remove(item) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_clear),
                                                        contentDescription = "Eliminar",
                                                        tint = Color(0xFF6C7086),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            // Action Buttons for this download
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // "Abrir / Ejecutar en PC" Button
                                                Button(
                                                    onClick = { pendingLaunchItem = item },
                                                    modifier = Modifier.weight(1.2f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (item.isExecutable) Color(0xFFA6E3A1) else Color(0xFF89B4FA),
                                                        contentColor = Color.Black
                                                    )
                                                ) {
                                                    Text(
                                                        text = if (item.isExecutable) "🚀 Abrir en PC" else "Abrir",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                // "Abrir en Celular" Button
                                                OutlinedButton(
                                                    onClick = { openFileOnAndroid(context, item) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBAC2DE))
                                                ) {
                                                    Text("📱 Celular", style = MaterialTheme.typography.labelSmall)
                                                }

                                                // "Copiar Link" Button
                                                OutlinedButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                        clipboard?.setPrimaryClip(ClipData.newPlainText("URL Descarga", item.url))
                                                        Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBAC2DE))
                                                ) {
                                                    Text("Copiar", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Info Footer
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF11111B),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(
                                text = "💡 Los programas se ejecutarán en Windows fuera del navegador (en I:\\nOpenNow_Browser\\Downloads) a 1000 Mbps de red GFN.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBAC2DE),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 6. Launch / Open in PC Dialog
    pendingLaunchItem?.let { item ->
        val safeFileName = item.fileName
        val windowsDownloadPath = "I:\\nOpenNow_Browser\\Downloads\\$safeFileName"
        val powershellFetchAndRun = "\"I:\\Apps\\SalsaNOW SilentApps\\Powershell\\pwsh.exe\" -c \"irm '${item.url}' -OutFile '$windowsDownloadPath'; start '$windowsDownloadPath'\""
        val cmdRunOnly = "start \"\" \"$windowsDownloadPath\""

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
                        text = "El programa se ejecutará en tu instancia de Windows GFN fuera del navegador:",
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
                    Text(
                        text = "Elige abajo para ejecutar directamente en la PC remota, copiar el comando o descargarlo al disco I:\\ a 1000 Mbps.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6C7086)
                    )
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
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Ejecutar en PC", cmdRunOnly))
                                onRunCommandOnPc(cmdRunOnly)
                                Toast.makeText(context, "¡Comando enviado a la PC! Abriendo programa...", Toast.LENGTH_LONG).show()
                                pendingLaunchItem = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA6E3A1), contentColor = Color.Black)
                        ) {
                            Text("🚀 Enviar y Ejecutar en PC", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Ejecutar en PC", cmdRunOnly))
                                Toast.makeText(context, "¡Comando copiado! Pégalo en Windows para abrir el programa.", Toast.LENGTH_LONG).show()
                                pendingLaunchItem = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF89B4FA), contentColor = Color.Black)
                        ) {
                            Text("Copiar Comando")
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Descargar y Ejecutar en PC", powershellFetchAndRun))
                                Toast.makeText(context, "Comando PowerShell 7 copiado para descargar y abrir a 1000 Mbps.", Toast.LENGTH_LONG).show()
                                pendingLaunchItem = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Descargar en I:\\")
                        }
                    }
                }
            },
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { openFileOnAndroid(context, item) }) {
                        Text("📱 Abrir en Celular", color = Color(0xFFBAC2DE))
                    }
                    TextButton(onClick = { pendingLaunchItem = null }) {
                        Text("Cerrar", color = Color(0xFF6C7086))
                    }
                }
            }
        )
    }
}
