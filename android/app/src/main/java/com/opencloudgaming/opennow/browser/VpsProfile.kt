package com.opencloudgaming.opennow.browser

import java.util.UUID

data class VpsProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Mi VPS",
    var host: String = "",
    var sshPort: Int = 22,
    var sshUser: String = "root",
    var sshPassword: String = "",
    var sshPrivateKey: String = "",
    var browserPort: Int = 3000,
    var browserMode: String = "native_mobile"
) {
    fun getCleanHost(): String {
        return host.trim().removePrefix("http://").removePrefix("https://").split(":")[0]
    }

    fun isKeyAuth(): Boolean = sshPrivateKey.isNotBlank()
}
