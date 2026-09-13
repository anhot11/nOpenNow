package com.opencloudgaming.opennow

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "PersistentAccountVault"
private val VAULT_MAGIC = byteArrayOf(0x4E, 0x4F, 0x50, 0x4E) // "NOPN"
private const val GCM_IV_LENGTH = 12
private const val GCM_TAG_LENGTH = 128

@Serializable
internal data class VaultAccountPackage(
    val version: Int = 1,
    val savedAt: Long = System.currentTimeMillis(),
    val deviceId: String? = null,
    val authState: PersistedAuthState,
)

/**
 * Provides persistent account session storage in shared/public storage
 * (Documents/nOpenNow and Download/nOpenNow) that survives app uninstallation
 * and reinstallation, ensuring users never lose their GeForce NOW login sessions.
 */
internal object PersistentAccountVault {
    private val lock = Any()

    private fun getVaultFiles(context: Context): List<File> {
        val candidates = mutableListOf<File>()

        // 1. Documents/nOpenNow/.session_vault.bin
        runCatching {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            candidates.add(File(File(docs, "nOpenNow"), ".session_vault.bin"))
        }

        // 2. Download/nOpenNow/.session_vault.bin
        runCatching {
            val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            candidates.add(File(File(dl, "nOpenNow"), ".session_vault.bin"))
        }

        // 3. Direct sdcard paths (survives app uninstall across all Android releases)
        candidates.add(File("/sdcard/Documents/nOpenNow/.session_vault.bin"))
        candidates.add(File("/sdcard/Download/nOpenNow/.session_vault.bin"))

        // 4. Secondary external app directory fallback
        runCatching {
            context.getExternalFilesDir(null)?.let {
                candidates.add(File(it, ".session_vault.bin"))
            }
        }

        return candidates.distinctBy { it.absolutePath }
    }

    private fun deriveKey(context: Context): SecretKeySpec {
        val seed = "nOpenNow_AccountVault_v1_${context.packageName}".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(seed)
        return SecretKeySpec(digest, "AES")
    }

    fun backup(context: Context, state: PersistedAuthState, deviceId: String?): Boolean = synchronized(lock) {
        if (state.sessions.isEmpty()) {
            clear(context)
            return true
        }

        var successCount = 0
        try {
            val pkg = VaultAccountPackage(
                version = 1,
                savedAt = System.currentTimeMillis(),
                deviceId = deviceId,
                authState = state,
            )
            val json = OpenNowJson.encodeToString(pkg)
            val plaintext = json.toByteArray(Charsets.UTF_8)

            val key = deriveKey(context)
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ciphertext = cipher.doFinal(plaintext)

            val payload = ByteArray(VAULT_MAGIC.size + GCM_IV_LENGTH + ciphertext.size)
            System.arraycopy(VAULT_MAGIC, 0, payload, 0, VAULT_MAGIC.size)
            System.arraycopy(iv, 0, payload, VAULT_MAGIC.size, GCM_IV_LENGTH)
            System.arraycopy(ciphertext, 0, payload, VAULT_MAGIC.size + GCM_IV_LENGTH, ciphertext.size)

            for (target in getVaultFiles(context)) {
                try {
                    target.parentFile?.mkdirs()
                    val temp = File(target.parentFile, "${target.name}.tmp")
                    temp.writeBytes(payload)
                    if (target.exists()) target.delete()
                    if (temp.renameTo(target) || (target.writeBytes(payload).run { temp.delete(); true })) {
                        successCount++
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not write vault file to ${target.absolutePath}: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to encrypt and backup account session: ${e.message}")
        }

        Log.i(TAG, "Account session backed up to $successCount persistent storage locations.")
        return successCount > 0
    }

    fun restore(context: Context): Pair<PersistedAuthState, String?>? = synchronized(lock) {
        val key = deriveKey(context)

        for (target in getVaultFiles(context)) {
            if (!target.exists() || !target.isFile || target.length() < (VAULT_MAGIC.size + GCM_IV_LENGTH + 16)) {
                continue
            }

            try {
                val bytes = target.readBytes()
                if (bytes.size < VAULT_MAGIC.size + GCM_IV_LENGTH + 16) continue

                // Check magic header
                val hasMagic = bytes[0] == VAULT_MAGIC[0] &&
                    bytes[1] == VAULT_MAGIC[1] &&
                    bytes[2] == VAULT_MAGIC[2] &&
                    bytes[3] == VAULT_MAGIC[3]

                if (!hasMagic) {
                    // Try fallback JSON parse in case of unencrypted format
                    runCatching {
                        val parsed = OpenNowJson.decodeFromString<VaultAccountPackage>(String(bytes, Charsets.UTF_8))
                        if (parsed.authState.sessions.isNotEmpty()) {
                            Log.i(TAG, "Restored session from unencrypted vault: ${target.absolutePath}")
                            return parsed.authState to parsed.deviceId
                        }
                    }
                    continue
                }

                val iv = ByteArray(GCM_IV_LENGTH)
                System.arraycopy(bytes, VAULT_MAGIC.size, iv, 0, GCM_IV_LENGTH)

                val ciphertextOffset = VAULT_MAGIC.size + GCM_IV_LENGTH
                val ciphertextSize = bytes.size - ciphertextOffset
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

                val decryptedBytes = cipher.doFinal(bytes, ciphertextOffset, ciphertextSize)
                val json = String(decryptedBytes, Charsets.UTF_8)
                val pkg = OpenNowJson.decodeFromString<VaultAccountPackage>(json)

                if (pkg.authState.sessions.isNotEmpty()) {
                    Log.i(TAG, "Successfully restored account session for user ${pkg.authState.activeUserId} from ${target.absolutePath}")
                    return pkg.authState to pkg.deviceId
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed decrypting vault from ${target.absolutePath}: ${e.message}")
            }
        }

        return null
    }

    fun clear(context: Context): Boolean = synchronized(lock) {
        var anyDeleted = false
        for (target in getVaultFiles(context)) {
            try {
                if (target.exists() && target.delete()) {
                    anyDeleted = true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not delete vault file ${target.absolutePath}: ${e.message}")
            }
        }
        Log.i(TAG, "Persistent account vault cleared.")
        return anyDeleted
    }

    fun hasBackup(context: Context): Boolean = synchronized(lock) {
        getVaultFiles(context).any { it.exists() && it.isFile && it.length() > 32 }
    }
}
