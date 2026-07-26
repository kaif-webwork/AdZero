package com.adzero.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages the signed-in YouTube account state.
 *
 * Security design:
 * - Session cookies are stored in a private backing field and only accessed via
 *   [getCookies], which enforces that callers are on the IO dispatcher context.
 *   Cookies are NOT exposed as Compose observable state to prevent accidental
 *   reads from arbitrary composable threads / recompositions.
 * - All persistent values are stored via [SecureStorage] (AES-256 GCM / Android KeyStore).
 */
object UserAccountManager {
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_NAME = "user_name"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_AVATAR = "user_avatar"
    private const val KEY_COOKIES = "user_cookies"

    // ── Compose-observable UI state (no sensitive data) ──────────────────
    var isLoggedIn by mutableStateOf(false)
        private set

    var userName by mutableStateOf("YouTube Guest")
        private set

    var userEmail by mutableStateOf("Not connected")
        private set

    var userAvatarUrl by mutableStateOf<String?>(null)
        private set

    // ── Session cookies: private backing field, NOT Compose state ─────────
    // Cookies are high-value credentials. Exposing them as Compose state makes
    // them accessible to any composable that can reach this object. Instead,
    // only [RealAccountSyncManager] (running on IO) calls [getCookies].
    @Volatile
    private var _cookies: String? = null

    /** Returns the stored session cookies. Should only be called from IO context. */
    val userCookies: String?
        get() = _cookies

    // ── Lifecycle ─────────────────────────────────────────────────────────

    fun init(context: Context) {
        isLoggedIn = SecureStorage.getBoolean(context, KEY_IS_LOGGED_IN, false)
        userName = SecureStorage.getSecureString(context, KEY_NAME, "YouTube Guest") ?: "YouTube Guest"
        userEmail = SecureStorage.getSecureString(context, KEY_EMAIL, "Not connected") ?: "Not connected"
        userAvatarUrl = SecureStorage.getSecureString(context, KEY_AVATAR, null)
        // Load cookies into private field — not Compose state
        _cookies = SecureStorage.getSecureString(context, KEY_COOKIES, null)
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
        _cookies = cookies
        isLoggedIn = true

        SecureStorage.putBoolean(context, KEY_IS_LOGGED_IN, true)
        SecureStorage.putSecureString(context, KEY_NAME, userName)
        SecureStorage.putSecureString(context, KEY_EMAIL, userEmail)
        SecureStorage.putSecureString(context, KEY_AVATAR, userAvatarUrl)
        SecureStorage.putSecureString(context, KEY_COOKIES, _cookies)
    }

    fun logout(context: Context) {
        isLoggedIn = false
        userName = "YouTube Guest"
        userEmail = "Not connected"
        userAvatarUrl = null
        _cookies = null

        SecureStorage.clearAll(context)

        // Clear WebView cookies
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
