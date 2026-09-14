package com.opencloudgaming.opennow.browser

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * High-Performance Dual-Mode Proxy Server for new OpenNow.
 * 
 * Mode 1 (GeForce NOW PC Tunnel):
 *   Routes WebView traffic over secure WebSocket (wss://...) to GeForce NOW Windows VM.
 *   Traffic exits onto the internet using NVIDIA's datacenter IP. 0 open ports, 0 SSH.
 *
 * Mode 2 (Direct Mobile Mode):
 *   Routes directly through phone's internet for 0ms ultra-fast browsing when PC tunnel is off.
 */
class PcTunnelProxyServer(
    val port: Int,
    var tunnelWssUrl: String? = null
) {
    private companion object {
        private const val TAG = "PcTunnelProxy"
        private const val BUFFER_SIZE = 16384
        private const val TIMEOUT_MS = 20000
    }

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false
    private val threadPool = Executors.newCachedThreadPool()
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for streaming
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val boundPort: Int get() = serverSocket?.localPort ?: port

    fun isRunning(): Boolean = isRunning && serverSocket?.isClosed == false

    fun isTunnelActive(): Boolean = !tunnelWssUrl.isNullOrBlank()

    fun start() {
        val s = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = s
        isRunning = true

        threadPool.submit {
            Log.d(TAG, "PcTunnelProxyServer listening on 127.0.0.1:${s.localPort} (Tunnel: $tunnelWssUrl)")
            while (isRunning && !s.isClosed) {
                try {
                    val clientSocket = s.accept()
                    activeSockets.add(clientSocket)
                    threadPool.submit {
                        try {
                            handleClient(clientSocket)
                        } catch (e: Exception) {
                            Log.d(TAG, "Client socket closed: ${e.message}")
                        } finally {
                            activeSockets.remove(clientSocket)
                            try { clientSocket.close() } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.tcpNoDelay = true
        socket.soTimeout = TIMEOUT_MS

        val inStream = socket.getInputStream()
        val outStream = socket.getOutputStream()

        val firstByte = inStream.read()
        if (firstByte < 0) return

        if (firstByte == 0x05) {
            handleSocks5(socket, inStream, outStream)
        } else {
            handleHttpProxy(socket, inStream, outStream, firstByte)
        }
    }

    private fun handleSocks5(socket: Socket, inStream: InputStream, outStream: OutputStream) {
        val nmethods = inStream.read()
        if (nmethods <= 0) return
        val methods = ByteArray(nmethods)
        var readLen = 0
        while (readLen < nmethods) {
            val count = inStream.read(methods, readLen, nmethods - readLen)
            if (count < 0) return
            readLen += count
        }

        // Method 0: NO AUTH
        outStream.write(byteArrayOf(0x05, 0x00))
        outStream.flush()

        val reqVer = inStream.read()
        val cmd = inStream.read()
        inStream.read() // rsv
        val atyp = inStream.read()

        if (reqVer != 5 || cmd != 1) { // 1 = CONNECT
            sendSocksReply(outStream, 0x07)
            return
        }

        val targetHost = when (atyp) {
            1 -> { // IPv4
                val ip = ByteArray(4)
                inStream.read(ip)
                InetAddress.getByAddress(ip).hostAddress ?: return
            }
            3 -> { // Domain
                val len = inStream.read()
                if (len <= 0) return
                val domainBytes = ByteArray(len)
                inStream.read(domainBytes)
                String(domainBytes, Charsets.UTF_8)
            }
            4 -> { // IPv6
                val ip = ByteArray(16)
                inStream.read(ip)
                InetAddress.getByAddress(ip).hostAddress ?: return
            }
            else -> {
                sendSocksReply(outStream, 0x08)
                return
            }
        }

        val p1 = inStream.read()
        val p2 = inStream.read()
        if (p1 < 0 || p2 < 0) return
        val targetPort = ((p1 and 0xFF) shl 8) or (p2 and 0xFF)

        // Route through PC tunnel if active, otherwise route direct
        val wss = tunnelWssUrl
        if (!wss.isNullOrBlank()) {
            routeViaPcWebSocket(socket, inStream, outStream, targetHost, targetPort, isSocks = true)
        } else {
            routeDirect(socket, inStream, outStream, targetHost, targetPort, isSocks = true)
        }
    }

    private fun handleHttpProxy(socket: Socket, inStream: InputStream, outStream: OutputStream, firstByte: Int) {
        val lineBytes = ByteArrayOutputStream()
        lineBytes.write(firstByte)
        while (true) {
            val b = inStream.read()
            if (b < 0) return
            if (b == '\n'.code) break
            if (b != '\r'.code) lineBytes.write(b)
        }
        val requestLine = lineBytes.toString("UTF-8").trim()
        if (requestLine.isBlank()) return

        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]

        val targetHost: String
        val targetPort: Int

        if (method.equals("CONNECT", ignoreCase = true)) {
            skipHeaders(inStream)
            val hostParts = target.split(":")
            targetHost = hostParts[0]
            targetPort = if (hostParts.size > 1) hostParts[1].toIntOrNull() ?: 443 else 443

            val wss = tunnelWssUrl
            if (!wss.isNullOrBlank()) {
                routeViaPcWebSocket(socket, inStream, outStream, targetHost, targetPort, isSocks = false)
            } else {
                routeDirect(socket, inStream, outStream, targetHost, targetPort, isSocks = false)
            }
        } else {
            // Plain HTTP
            val uri = try { java.net.URI(target) } catch (_: Exception) { null }
            targetHost = uri?.host ?: target.split("/")[0].split(":")[0]
            targetPort = if (uri?.port != null && uri.port > 0) uri.port else 80

            val rawPath = if (uri != null) {
                (uri.rawPath ?: "/") + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
            } else target
            val rewrittenHeader = "$method $rawPath ${parts.getOrNull(2) ?: "HTTP/1.1"}\r\n"

            val wss = tunnelWssUrl
            if (!wss.isNullOrBlank()) {
                routeViaPcWebSocket(socket, inStream, outStream, targetHost, targetPort, isSocks = false, prefixBytes = rewrittenHeader.toByteArray(Charsets.UTF_8))
            } else {
                routeDirect(socket, inStream, outStream, targetHost, targetPort, isSocks = false, prefixBytes = rewrittenHeader.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun routeViaPcWebSocket(
        socket: Socket,
        inStream: InputStream,
        outStream: OutputStream,
        targetHost: String,
        targetPort: Int,
        isSocks: Boolean,
        prefixBytes: ByteArray? = null
    ) {
        val wssUrl = tunnelWssUrl ?: return
        val normalizedWss = if (wssUrl.startsWith("http://")) {
            wssUrl.replaceFirst("http://", "ws://")
        } else if (wssUrl.startsWith("https://")) {
            wssUrl.replaceFirst("https://", "wss://")
        } else if (!wssUrl.startsWith("ws://") && !wssUrl.startsWith("wss://")) {
            "wss://$wssUrl"
        } else wssUrl

        val request = Request.Builder().url(normalizedWss).build()
        val connectLatch = CountDownLatch(1)
        var connectedOk = false
        var activeWs: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                activeWs = webSocket
                // Handshake target host and port to PC transmitter
                webSocket.send("CONNECT $targetHost:$targetPort")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.trim() == "OK") {
                    connectedOk = true
                    connectLatch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val data = bytes.toByteArray()
                    outStream.write(data)
                    outStream.flush()
                } catch (_: Exception) {
                    webSocket.close(1000, null)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connectLatch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connectLatch.countDown()
                try { socket.close() } catch (_: Exception) {}
            }
        }

        val ws = okHttpClient.newWebSocket(request, listener)

        try {
            connectLatch.await(10, TimeUnit.SECONDS)
            if (!connectedOk || activeWs == null) {
                if (isSocks) sendSocksReply(outStream, 0x05) else sendHttpError(outStream, 502, "Bad Gateway")
                ws.cancel()
                return
            }

            // Connection established through PC!
            if (isSocks) {
                sendSocksReply(outStream, 0x00)
            } else if (prefixBytes == null) {
                val okResponse = "HTTP/1.1 200 Connection Established\r\nProxy-Agent: OpenNow-PcTunnel\r\n\r\n"
                outStream.write(okResponse.toByteArray(Charsets.US_ASCII))
                outStream.flush()
            }

            prefixBytes?.let {
                ws.send(it.toByteString())
            }

            // Stream client socket data into WebSocket binary frames
            val buf = ByteArray(BUFFER_SIZE)
            while (isRunning && !socket.isClosed) {
                val count = inStream.read(buf)
                if (count < 0) break
                ws.send(ByteString.of(buf, 0, count))
            }
        } catch (e: Exception) {
            Log.d(TAG, "WebSocket tunnel to $targetHost:$targetPort ended: ${e.message}")
        } finally {
            try { ws.close(1000, "Done") } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun routeDirect(
        socket: Socket,
        inStream: InputStream,
        outStream: OutputStream,
        targetHost: String,
        targetPort: Int,
        isSocks: Boolean,
        prefixBytes: ByteArray? = null
    ) {
        var remoteSocket: Socket? = null
        try {
            remoteSocket = Socket()
            remoteSocket.tcpNoDelay = true
            remoteSocket.connect(java.net.InetSocketAddress(targetHost, targetPort), 12000)
            remoteSocket.soTimeout = 120000

            val remoteIn = remoteSocket.getInputStream()
            val remoteOut = remoteSocket.getOutputStream()

            if (isSocks) {
                sendSocksReply(outStream, 0x00)
            } else if (prefixBytes == null) {
                val okResponse = "HTTP/1.1 200 Connection Established\r\nProxy-Agent: OpenNow-Direct\r\n\r\n"
                outStream.write(okResponse.toByteArray(Charsets.US_ASCII))
                outStream.flush()
            }

            prefixBytes?.let {
                remoteOut.write(it)
                remoteOut.flush()
            }

            val latch = CountDownLatch(1)
            threadPool.submit {
                try {
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = inStream.read(buf)
                        if (count < 0) break
                        remoteOut.write(buf, 0, count)
                        remoteOut.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }

            threadPool.submit {
                try {
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = remoteIn.read(buf)
                        if (count < 0) break
                        outStream.write(buf, 0, count)
                        outStream.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }

            latch.await()
        } catch (_: Exception) {
            if (isSocks) sendSocksReply(outStream, 0x05) else sendHttpError(outStream, 502, "Bad Gateway")
        } finally {
            try { remoteSocket?.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun skipHeaders(inStream: InputStream) {
        var prev1 = 0; var prev2 = 0; var prev3 = 0
        while (true) {
            val b = inStream.read()
            if (b < 0) break
            if (b == '\n'.code && prev1 == '\r'.code && prev2 == '\n'.code && prev3 == '\r'.code) break
            if (b == '\n'.code && prev1 == '\n'.code) break
            prev3 = prev2; prev2 = prev1; prev1 = b
        }
    }

    private fun sendSocksReply(out: OutputStream, replyCode: Byte) {
        try {
            out.write(byteArrayOf(0x05, replyCode, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            out.flush()
        } catch (_: Exception) {}
    }

    private fun sendHttpError(out: OutputStream, code: Int, message: String) {
        try {
            val msg = "HTTP/1.1 $code $message\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n$message"
            out.write(msg.toByteArray(Charsets.US_ASCII))
            out.flush()
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        for (s in activeSockets) {
            try { s.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
        threadPool.shutdownNow()
    }
}
