package com.opencloudgaming.opennow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

/**
 * Generates structured GitHub Issue reports and diagnostics for the nOpenNow repository:
 * https://github.com/anhot11/nOpenNow/issues
 */
internal object GitHubDiagnosticsReporter {
    const val GITHUB_REPO_URL = "https://github.com/anhot11/nOpenNow"
    const val GITHUB_ISSUES_NEW_URL = "https://github.com/anhot11/nOpenNow/issues/new"

    fun buildIssueMarkdown(
        title: String,
        userDescription: String,
        device: AndroidDeviceDiagnosticsSnapshot? = null,
        runtimeDiagnostics: AndroidRuntimeDiagnosticsSnapshot? = null,
        streamStats: StreamRuntimeStats? = null,
        streamSettings: StreamSettings? = null,
        serverZone: String? = null,
        deliveredResolution: String? = null,
        deliveredCodec: String? = null,
        errorSnippet: String? = null,
    ): String = buildString {
        appendLine("### 📋 Descripción del Reporte")
        appendLine(userDescription.ifBlank { "Sin descripción adicional especificada." })
        appendLine()
        appendLine("### 📱 Diagnóstico del Dispositivo")
        if (device != null) {
            appendLine("- **Dispositivo:** ${device.manufacturer} ${device.model} (${device.brand})")
            appendLine("- **Versión Android:** ${device.androidRelease} (SDK ${device.androidSdk})")
            appendLine("- **Arquitectura ABI:** ${device.supportedAbis.joinToString(", ")}")
            device.totalMemoryMiB?.let { appendLine("- **Memoria RAM:** ${it} MiB (Low RAM: ${device.lowRamDevice ?: "No"})") }
            appendLine("- **Resolución de Pantalla:** ${device.displayWidthPixels}x${device.displayHeightPixels} (${device.densityDpi} DPI)")
        } else {
            appendLine("- *Diagnóstico del dispositivo no disponible*")
        }
        appendLine()
        appendLine("### 🎮 Información de la Sesión GeForce NOW")
        appendLine("- **App:** nOpenNow v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
        serverZone?.let { appendLine("- **Zona de Servidor GFN:** $it") }
        streamSettings?.let {
            appendLine("- **Ajustes Solicitados:** ${it.resolution}@${it.fps}fps · ${it.maxBitrateMbps} Mbps · Códec: ${it.codec}")
        }
        deliveredResolution?.let { appendLine("- **Resolución Entregada:** $it") }
        deliveredCodec?.let { appendLine("- **Códec Entregado:** $it") }
        if (streamStats != null) {
            appendLine("- **Métricas de Red:** Latencia: ${streamStats.pingMs}ms · Pérdida de paquetes: ${streamStats.packetLossPct}% · FPS: ${streamStats.fps}")
        }
        if (runtimeDiagnostics != null) {
            appendLine("- **Conexión de Red:** ${runtimeDiagnostics.networkKind} (Wi-Fi Band: ${runtimeDiagnostics.wifiBand})")
        }
        if (!errorSnippet.isNullOrBlank()) {
            appendLine()
            appendLine("### 📄 Registro / Logs del Error")
            appendLine("```text")
            appendLine(errorSnippet.takeLast(2500))
            appendLine("```")
        }
        appendLine()
        appendLine("---")
        appendLine("*Reporte generado automáticamente desde nOpenNow Android Client*")
    }

    fun buildIssueUrl(title: String, bodyMarkdown: String): String {
        val safeTitle = URLEncoder.encode(title.ifBlank { "Reporte de Error / Diagnóstico en nOpenNow" }, "UTF-8")
        val safeBody = URLEncoder.encode(bodyMarkdown, "UTF-8")
        return "$GITHUB_ISSUES_NEW_URL?title=$safeTitle&body=$safeBody"
    }

    fun openGitHubIssue(context: Context, title: String, bodyMarkdown: String) {
        val url = buildIssueUrl(title, bodyMarkdown)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            copyMarkdownToClipboard(context, bodyMarkdown)
            Toast.makeText(context, "No se pudo abrir el navegador. Reporte copiado al portapapeles.", Toast.LENGTH_LONG).show()
        }
    }

    fun copyMarkdownToClipboard(context: Context, markdown: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("nOpenNow Diagnostic Report", markdown))
        Toast.makeText(context, "¡Diagnóstico copiado al portapapeles en formato Markdown!", Toast.LENGTH_SHORT).show()
    }
}
