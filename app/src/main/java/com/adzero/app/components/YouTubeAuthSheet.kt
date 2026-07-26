package com.adzero.app.components

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val ALLOWED_HTTPS_HOSTS = setOf(
    "accounts.google.com",
    "myaccount.google.com",
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "google.com",
    "www.google.com"
)

/** Returns true if the URL is safe to load (HTTPS + known Google/YouTube host). */
private fun isSafeUrl(url: String?): Boolean {
    if (url == null) return false
    return try {
        val uri = android.net.Uri.parse(url)
        uri.scheme?.lowercase() == "https" &&
            ALLOWED_HTTPS_HOSTS.any { uri.host?.endsWith(it) == true }
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAuthSheet(
    onDismiss: () -> Unit,
    onSuccess: (name: String, email: String, avatar: String?) -> Unit
) {
    val context = LocalContext.current
    var isPageLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("https://accounts.google.com/ServiceLogin?service=youtube") }

    // Displayed in the security indicator header
    val displayHost by remember(currentUrl) {
        derivedStateOf {
            try { android.net.Uri.parse(currentUrl).host ?: "" } catch (e: Exception) { "" }
        }
    }
    val isSecure by remember(currentUrl) {
        derivedStateOf { currentUrl.startsWith("https://") }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sign in to YouTube",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    // Security indicator: shows lock + domain so user can verify authenticity
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = if (isSecure) "Secure" else "Not secure",
                            tint = if (isSecure) Color(0xFF4CAF50) else Color(0xFFE53935),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = displayHost.ifBlank { "Loading…" },
                            fontSize = 11.sp,
                            color = if (isSecure) Color(0xFF4CAF50) else Color(0xFFE53935),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (isPageLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ── Security warning bar if somehow a non-HTTPS page loads ────────
            if (!isSecure && displayHost.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE53935))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(
                        "This page is not secure. Do not enter your password.",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            // ── WebView for Google / YouTube Account Sign In ──────────────────
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Restrict file system access
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // Enable Safe Browsing (API 26+)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = true
                        }
                        // Block mixed content (HTTPS pages loading HTTP sub-resources)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                        // Block third-party cookies so auth cookies are isolated to Google/YouTube
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        CookieManager.getInstance().setAcceptCookie(true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isPageLoading = true
                                if (url != null) currentUrl = url
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                if (url != null) currentUrl = url

                                // Only capture cookies from a verified HTTPS Google/YouTube URL
                                if (!isSafeUrl(url)) return

                                val cookies = CookieManager.getInstance().getCookie(url)
                                val isLoggedIn = cookies != null &&
                                    (cookies.contains("SAPISID") ||
                                     cookies.contains("LOGIN_INFO") ||
                                     cookies.contains("SID"))

                                if (!isLoggedIn) return

                                // Extract real account name, email, and avatar via JS
                                view?.evaluateJavascript("""
                                    (function() {
                                        try {
                                            var name = '';
                                            var email = '';
                                            var avatar = '';
                                            // Try YouTube account button
                                            var avatarEl = document.querySelector('#avatar-btn img, yt-img-shadow img, .ytd-topbar-menu-button-renderer img');
                                            if (avatarEl) avatar = avatarEl.src || '';
                                            // Try account name from aria-label on avatar button
                                            var btn = document.querySelector('#avatar-btn');
                                            if (btn) {
                                                var label = btn.getAttribute('aria-label') || '';
                                                // aria-label format is usually: "Account: Name Email"
                                                var parts = label.replace('Account: ', '').split(/\n|\u00b7/);
                                                if (parts.length >= 2) {
                                                    name = parts[0].trim();
                                                    email = parts[1].trim();
                                                } else if (parts.length === 1) {
                                                    name = parts[0].trim();
                                                }
                                            }
                                            // Fallback: try Google account page
                                            if (!name) {
                                                var nameEl = document.querySelector('[data-email]');
                                                if (nameEl) email = nameEl.getAttribute('data-email') || '';
                                                var nameEl2 = document.querySelector('.gb_lb, .gb_mb, [data-name]');
                                                if (nameEl2) name = nameEl2.textContent || nameEl2.getAttribute('data-name') || '';
                                            }
                                            return JSON.stringify({ name: name.trim(), email: email.trim(), avatar: avatar });
                                        } catch(e) {
                                            return JSON.stringify({ name: '', email: '', avatar: '' });
                                        }
                                    })()
                                """.trimIndent()) { result ->
                                    val cleanResult = result?.trim()?.removeSurrounding("\"")
                                        ?.replace("\\\"", "\"") ?: "{}"

                                    val finalName: String
                                    val finalEmail: String
                                    val finalAvatar: String?

                                    try {
                                        val json = org.json.JSONObject(cleanResult)
                                        finalName = json.optString("name").ifBlank { "YouTube User" }
                                        finalEmail = json.optString("email").ifBlank { "Google Account" }
                                        val avatarRaw = json.optString("avatar")
                                        finalAvatar = avatarRaw.takeIf { it.startsWith("http") }
                                    } catch (e: Exception) {
                                        return@evaluateJavascript
                                    }

                                    // Persist to secure storage
                                    com.adzero.app.data.UserAccountManager.saveAccount(
                                        ctx,
                                        name = finalName,
                                        email = finalEmail,
                                        avatarUrl = finalAvatar,
                                        cookies = cookies
                                    )

                                    // Trigger live account data sync in background
                                    CoroutineScope(Dispatchers.IO).launch {
                                        com.adzero.app.data.RealAccountSyncManager.syncAccountWithCookies(ctx, cookies)
                                    }

                                    onSuccess(finalName, finalEmail, finalAvatar)
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                val uri = request.url

                                // Force all navigations to HTTPS
                                if (uri.scheme?.lowercase() == "http") {
                                    val httpsUrl = url.replaceFirst("http://", "https://")
                                    view?.loadUrl(httpsUrl)
                                    return true // block original HTTP load
                                }

                                // Block navigations to completely unrelated hosts
                                val host = uri.host ?: return false
                                val isKnownHost = ALLOWED_HTTPS_HOSTS.any { host.endsWith(it) }
                                if (!isKnownHost) {
                                    // Let it load for OAuth redirects like gstatic, but don't allow arbitrary sites
                                    return false
                                }

                                return false
                            }
                        }

                        // Always start from a safe HTTPS URL
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
