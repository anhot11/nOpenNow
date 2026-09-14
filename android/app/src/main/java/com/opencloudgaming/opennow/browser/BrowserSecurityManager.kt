package com.opencloudgaming.opennow.browser

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class BookmarkItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

class BrowserSecurityManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "opennow_browser_vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("opennow_browser_vault_fallback", Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    companion object {
        private const val KEY_PROFILE = "vps_profile_data"
        private const val KEY_BOOKMARKS = "browser_bookmarks"
        private const val KEY_HISTORY = "browser_history"
    }

    fun getProfile(): VpsProfile? {
        val json = securePrefs.getString(KEY_PROFILE, null) ?: return null
        return try {
            gson.fromJson(json, VpsProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveProfile(profile: VpsProfile) {
        val json = gson.toJson(profile)
        securePrefs.edit().putString(KEY_PROFILE, json).apply()
    }

    fun clearProfile() {
        securePrefs.edit().remove(KEY_PROFILE).apply()
    }

    fun getBookmarks(): MutableList<BookmarkItem> {
        val json = securePrefs.getString(KEY_BOOKMARKS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<BookmarkItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun addBookmark(title: String, url: String) {
        if (url.isBlank()) return
        val list = getBookmarks()
        list.removeAll { it.url.trim().equals(url.trim(), ignoreCase = true) }
        list.add(0, BookmarkItem(title = title.ifBlank { url }, url = url.trim()))
        val json = gson.toJson(list)
        securePrefs.edit().putString(KEY_BOOKMARKS, json).apply()
    }

    fun deleteBookmark(id: String) {
        val list = getBookmarks()
        list.removeAll { it.id == id }
        val json = gson.toJson(list)
        securePrefs.edit().putString(KEY_BOOKMARKS, json).apply()
    }

    fun getHistory(): MutableList<HistoryItem> {
        val json = securePrefs.getString(KEY_HISTORY, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<HistoryItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val list = getHistory()
        list.removeAll { it.url.trim().equals(url.trim(), ignoreCase = true) }
        list.add(0, HistoryItem(title = title.ifBlank { url }, url = url.trim()))
        if (list.size > 100) {
            list.subList(100, list.size).clear()
        }
        val json = gson.toJson(list)
        securePrefs.edit().putString(KEY_HISTORY, json).apply()
    }

    fun clearHistory() {
        securePrefs.edit().remove(KEY_HISTORY).apply()
    }
}
