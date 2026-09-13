package com.opencloudgaming.opennow

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Full-screen in-app browser dialog for OpenNOW.
 * Default in portrait (vertical) orientation like standard Android mobile browsers,
 * with screen rotation toggle, prominent 'X' button to return to game stream,
 * history back support, and dynamic GFN low-bitrate mode.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenNowInAppBrowserDialog(
    initialUrl: String = "https://www.google.com",
    lowQualityActive: Boolean = true,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isPortrait by rememberSaveable { mutableStateOf(true) }

    var currentUrl by remember { mutableStateOf(initialUrl) }
    var inputUrl by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Switch to vertical (portrait) orientation by default, restore game landscape on exit
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Handle Android hardware Back button: navigate web history first, or close browser if at start
    BackHandler(enabled = true) {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false, // Handled by BackHandler above
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF181825),
            contentColor = Color.White
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top Navigation Bar (Mobile Browser style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E2E))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Prominent 'X' button to return to game stream
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

                    // Back button in history
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Atrás en página",
                            tint = if (canGoBack) Color.White else Color(0xFF6C7086),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // URL Input Field (Omnibox)
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val trimmed = inputUrl.trim()
                                val target = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                                    if (trimmed.contains(".") && !trimmed.contains(" ")) {
                                        "https://$trimmed"
                                    } else {
                                        "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
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
                        shape = RoundedCornerShape(24.dp)
                    )

                    // Reload Button
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = "Recargar",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Screen Rotation Toggle (Vertical <-> Horizontal)
                    IconButton(
                        onClick = {
                            isPortrait = !isPortrait
                            activity?.requestedOrientation = if (isPortrait) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_screen_rotation),
                            contentDescription = if (isPortrait) "Rotar a Horizontal" else "Rotar a Vertical",
                            tint = if (isPortrait) Color(0xFF89B4FA) else Color(0xFFA6E3A1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Low Quality & Orientation Status Pill
                if (lowQualityActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF11111B))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(6.dp),
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFFA6E3A1),
                            ) {}
                            Text(
                                text = "Modo Ahorro: GFN 2 Mbps",
                                color = Color(0xFFA6E3A1),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(
                            text = if (isPortrait) "Vertical" else "Horizontal",
                            color = Color(0xFFBAC2DE),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                // Linear Progress Indicator
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color(0xFF89B4FA),
                        trackColor = Color.Transparent
                    )
                }

                // WebView Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
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
            }
        }
    }
}
