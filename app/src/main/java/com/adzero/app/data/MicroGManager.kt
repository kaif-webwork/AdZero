package com.adzero.app.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MicroGManager {
    // MicroG package names used by ReVanced, Vanced, and MicroG Project
    val MICROG_PACKAGES = listOf(
        "app.revanced.android.gms",
        "org.microg.gms",
        "com.mgoogle.android.gms",
        "com.google.android.gms",
        "com.lsa.microg",
        "com.vanced.android.apps.youtube.music"
    )

    const val MICROG_DOWNLOAD_URL = "https://github.com/ReVanced/GmsCore/releases"

    var isLoggedIn by mutableStateOf(false)
        private set

    var accountName by mutableStateOf("AdZero User")
        private set

    var accountEmail by mutableStateOf("Not connected")
        private set

    var avatarUrl by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("microg_prefs", Context.MODE_PRIVATE)
        isLoggedIn = prefs.getBoolean("is_logged_in", false)
        accountName = prefs.getString("account_name", "AdZero User") ?: "AdZero User"
        accountEmail = prefs.getString("account_email", "Not connected") ?: "Not connected"
        avatarUrl = prefs.getString("avatar_url", null)

        autoDetectAndConnectMicroGAccount(context)
    }

    fun autoDetectAndConnectMicroGAccount(context: Context) {
        if (isMicroGInstalled(context)) {
            var foundEmail: String? = null
            try {
                val accountManager = android.accounts.AccountManager.get(context)
                val accountTypes = listOf("com.mgoogle", "org.microg", "com.google", "com.google.android.gm")
                for (type in accountTypes) {
                    try {
                        val accounts = accountManager.getAccountsByType(type)
                        if (!accounts.isNullOrEmpty()) {
                            foundEmail = accounts[0].name
                            break
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (foundEmail.isNullOrBlank()) {
                    try {
                        val all = accountManager.accounts
                        val google = all.firstOrNull { it.name.contains("@") }
                        if (google != null) {
                            foundEmail = google.name
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!foundEmail.isNullOrBlank()) {
                val formattedName = foundEmail.substringBefore("@")
                    .replace(".", " ")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                saveAccount(context, formattedName, foundEmail)
            } else if (!isLoggedIn) {
                saveAccount(context, "Google Account", "user@gmail.com")
            }

            // Auto-trigger ReVanced style account feed & playlist sync
            CoroutineScope(Dispatchers.IO).launch {
                RealAccountSyncManager.syncRealYouTubeAccount(context, accountEmail.substringBefore("@"))
            }
        }
    }

    fun isMicroGInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in MICROG_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: Exception) {
                // Continue check
            }
        }

        try {
            val am = android.accounts.AccountManager.get(context)
            val types = listOf("com.mgoogle", "org.microg", "com.google")
            for (t in types) {
                val accs = am.getAccountsByType(t)
                if (!accs.isNullOrEmpty()) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }

    fun getInstalledMicroGPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in MICROG_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: PackageManager.NameNotFoundException) {
                // Ignore
            }
        }
        return null
    }

    fun launchMicroGAccountSetup(context: Context): Boolean {
        val installedPkg = getInstalledMicroGPackage(context)
        
        if (installedPkg != null) {
            try {
                // Try launching MicroG settings activity directly
                val intent = Intent().apply {
                    component = ComponentName(installedPkg, "org.microg.gms.ui.SettingsActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                try {
                    // Try launching standard Add Account intent with com.mgoogle account type
                    val addAccountIntent = Intent("android.settings.ADD_ACCOUNT").apply {
                        putExtra("account_types", arrayOf("com.mgoogle", "org.microg"))
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(addAccountIntent)
                    return true
                } catch (e2: Exception) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(installedPkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            return true
                        }
                    } catch (e3: Exception) {
                        e3.printStackTrace()
                    }
                }
            }
        }

        // MicroG not installed or intent failed: Open download URL in browser
        openMicroGDownloadPage(context)
        return false
    }

    fun createAccountPickerIntent(): Intent {
        return android.accounts.AccountManager.newChooseAccountIntent(
            null, null, arrayOf("com.mgoogle", "org.microg", "com.google"),
            false, null, null, null, null
        )
    }

    fun openMicroGDownloadPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MICROG_DOWNLOAD_URL)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun saveAccount(context: Context, name: String, email: String, avatar: String? = null) {
        accountName = name
        accountEmail = email
        avatarUrl = avatar
        isLoggedIn = true

        val prefs = context.getSharedPreferences("microg_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("account_name", name)
            .putString("account_email", email)
            .putString("avatar_url", avatar)
            .apply()
    }

    fun logout(context: Context) {
        accountName = "AdZero User"
        accountEmail = "Not connected"
        avatarUrl = null
        isLoggedIn = false

        val prefs = context.getSharedPreferences("microg_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
