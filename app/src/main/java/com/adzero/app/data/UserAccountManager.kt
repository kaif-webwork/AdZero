package com.adzero.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object UserAccountManager {
    private const val PREFS_NAME = "adzero_user_account"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_NAME = "user_name"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_AVATAR = "user_avatar"
    private const val KEY_COOKIES = "user_cookies"

    var isLoggedIn by mutableStateOf(false)
        private set

    var userName by mutableStateOf("YouTube Guest")
        private set

    var userEmail by mutableStateOf("Not connected")
        private set

    var userAvatarUrl by mutableStateOf<String?>(null)
        private set

    var userCookies by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        isLoggedIn = SecureStorage.getBoolean(context, KEY_IS_LOGGED_IN, false)
        userName = SecureStorage.getSecureString(context, KEY_NAME, "YouTube Guest") ?: "YouTube Guest"
        userEmail = SecureStorage.getSecureString(context, KEY_EMAIL, "Not connected") ?: "Not connected"
        userAvatarUrl = SecureStorage.getSecureString(context, KEY_AVATAR, null)
        userCookies = SecureStorage.getSecureString(context, KEY_COOKIES, null)
    }

    fun saveAccount(
        context: Context,
        name: String,
        email: String,
        avatarUrl: String? = null,
        cookies: String? = null
    ) {
        userName = name.ifBlank { "YouTube User" }
        userEmail = email.ifBlank { "Connected Account" }
        userAvatarUrl = avatarUrl
        userCookies = cookies
        isLoggedIn = true

        SecureStorage.putBoolean(context, KEY_IS_LOGGED_IN, true)
        SecureStorage.putSecureString(context, KEY_NAME, userName)
        SecureStorage.putSecureString(context, KEY_EMAIL, userEmail)
        SecureStorage.putSecureString(context, KEY_AVATAR, userAvatarUrl)
        SecureStorage.putSecureString(context, KEY_COOKIES, userCookies)
    }

    fun logout(context: Context) {
        isLoggedIn = false
        userName = "YouTube Guest"
        userEmail = "Not connected"
        userAvatarUrl = null
        userCookies = null

        SecureStorage.clearAll(context)

        // Clear webview cookies
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
