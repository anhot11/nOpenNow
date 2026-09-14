package com.opencloudgaming.opennow.browser

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ServerSocket

object SshTunnelManager {

    private var activeSession: Session? = null
    private var activeSocksPort: Int = -1
    private var socksServer: SshSocksServer? = null
    private var activeProfile: VpsProfile? = null

    private fun createSession(profile: VpsProfile): Session {
        val jsch = JSch()
        if (profile.isKeyAuth()) {
            jsch.addIdentity("vps_key", profile.sshPrivateKey.toByteArray(), null, null)
        }

        return jsch.getSession(profile.sshUser, profile.getCleanHost(), profile.sshPort).apply {
            if (!profile.isKeyAuth()) {
                setPassword(profile.sshPassword)
            }
            setConfig("StrictHostKeyChecking", "no")
            setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
            serverAliveInterval = 10000
            serverAliveCountMax = 3
            connect(15000)
        }
    }

    /**
     * Start Dynamic SOCKS5 Proxy through encrypted SSH tunnel (for Native Mobile Browser Egress).
     */
    suspend fun startSocksProxy(profile: VpsProfile): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sessionWasReconnected = activeSession?.isConnected != true || activeProfile?.host != profile.host
            if (sessionWasReconnected) {
                stopTunnel()
                activeSession = createSession(profile)
                activeProfile = profile
            }

            val session = activeSession!!
            if (!sessionWasReconnected && activeSocksPort > 0 && socksServer?.isRunning() == true) {
                return@withContext Result.success(activeSocksPort)
            }

            socksServer?.stop()
            val freePort = findFreePort()
            val server = SshSocksServer(session, freePort)
            server.start()
            socksServer = server
            activeSocksPort = freePort

            Result.success(freePort)
        } catch (e: Exception) {
            stopTunnel()
            Result.failure(e)
        }
    }

    fun isSocksProxyActive(): Boolean {
        return activeSession?.isConnected == true && activeSocksPort > 0 && socksServer?.isRunning() == true
    }

    fun getSocksPort(): Int = activeSocksPort

    fun getActiveHost(): String? = activeProfile?.host

    fun stopTunnel() {
        try {
            socksServer?.stop()
        } catch (_: Exception) {
        }
        socksServer = null

        try {
            activeSession?.disconnect()
        } catch (_: Exception) {
        } finally {
            activeSession = null
            activeSocksPort = -1
            activeProfile = null
        }
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }
}
