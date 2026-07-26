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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAuthSheet(
    onDismiss: () -> Unit,
    onSuccess: (name: String, email: String, avatar: String?) -> Unit
) {
    val context = LocalContext.current
    var isPageLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("https://accounts.google.com/ServiceLogin?service=youtube") }

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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sign in to YouTube",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (isPageLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // WebView for Google / YouTube Account Sign In
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = true
                        }
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isPageLoading = true
                                if (url != null) currentUrl = url
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false

                                // Check if user successfully logged into YouTube or Google
                                val cookies = CookieManager.getInstance().getCookie(url)
                                if (url != null && (url.contains("youtube.com") || url.contains("myaccount.google.com")) && cookies != null) {
                                    if (cookies.contains("SAPISID") || cookies.contains("LOGIN_INFO") || cookies.contains("SID")) {
                                        // Attempt to fetch avatar / handle via JS
                                        view?.evaluateJavascript(
                                            "(function() { try { return document.querySelector('#avatar-btn img, yt-img-shadow img').src; } catch(e) { return ''; } })()"
                                        ) { avatarResult ->
                                            val cleanAvatar = avatarResult?.replace("\"", "")?.takeIf { it.startsWith("http") }
                                            
                                            // Save account state
                                            com.adzero.app.data.UserAccountManager.saveAccount(
                                                ctx,
                                                name = "YouTube Connected User",
                                                email = "Live Account Synced",
                                                avatarUrl = cleanAvatar,
                                                cookies = cookies
                                            )

                                            // Trigger Live Account Data Sync
                                            CoroutineScope(Dispatchers.IO).launch {
                                                com.adzero.app.data.RealAccountSyncManager.syncAccountWithCookies(ctx, cookies)
                                            }

                                            onSuccess("YouTube Connected User", "Live Account Synced", cleanAvatar)
                                        }
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
