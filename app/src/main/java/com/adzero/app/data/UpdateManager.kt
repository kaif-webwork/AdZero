package com.adzero.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.adzero.app.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class UpdateInfo(
    val hasUpdate: Boolean = false,
    val versionName: String = "",
    val releaseTitle: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloaded: Boolean = false,
    val localApkFile: File? = null,
    val error: String? = null
)

object UpdateManager {
    private const val REPO_RELEASES_URL = "https://api.github.com/repos/kaif-webwork/AdZero/releases/latest"

    private val _updateState = MutableStateFlow(UpdateInfo())
    val updateState: StateFlow<UpdateInfo> = _updateState

    suspend fun checkForUpdates(context: Context, isManualCheck: Boolean = false): UpdateInfo {
        return withContext(Dispatchers.IO) {
            try {
                val currentVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                } catch (e: Exception) {
                    "1.0.0"
                }

                val request = Request.Builder()
                    .url(REPO_RELEASES_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "AdZero-App")
                    .build()

                val response = App.okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val info = UpdateInfo(error = "Unable to check for updates")
                    if (isManualCheck) _updateState.value = info
                    return@withContext info
                }

                val jsonStr = response.body?.string() ?: ""
                if (jsonStr.isBlank()) return@withContext UpdateInfo()

                val json = JSONObject(jsonStr)
                val tagName = json.optString("tag_name", "").trimStart('v')
                val title = json.optString("name", "New Release")
                val body = json.optString("body", "Bug fixes and performance improvements.")
                
                var apkUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val hasNewerVersion = isVersionNewer(tagName, currentVersion)
                val info = UpdateInfo(
                    hasUpdate = hasNewerVersion && apkUrl.isNotBlank(),
                    versionName = tagName,
                    releaseTitle = title,
                    changelog = body,
                    downloadUrl = apkUrl
                )

                _updateState.value = info
                return@withContext info
            } catch (e: Exception) {
                e.printStackTrace()
                val info = UpdateInfo(error = e.localizedMessage)
                if (isManualCheck) _updateState.value = info
                return@withContext info
            }
        }
    }

    suspend fun downloadAndInstallApk(context: Context, downloadUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                _updateState.value = _updateState.value.copy(isDownloading = true, downloadProgress = 0.05f)

                val destinationFile = File(context.externalCacheDir ?: context.cacheDir, "AdZero_update.apk")
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "AdZero-App")
                    .build()

                val response = App.okHttpClient.newCall(request).execute()
                val body = response.body ?: throw Exception("Empty APK payload")
                val contentLength = body.contentLength().coerceAtLeast(1L)

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            _updateState.value = _updateState.value.copy(downloadProgress = progress)
                        }
                    }
                }

                _updateState.value = _updateState.value.copy(
                    isDownloading = false,
                    isDownloaded = true,
                    localApkFile = destinationFile,
                    downloadProgress = 1.0f
                )

                withContext(Dispatchers.Main) {
                    installApk(context, destinationFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = _updateState.value.copy(
                    isDownloading = false,
                    error = "Download failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.provider"
            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, authority, apkFile)
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value.downloadUrl.takeIf { it.isNotBlank() }?.let { url ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateInfo(hasUpdate = false)
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val latestClean = latest.replace(Regex("[^0-9.]"), "")
        val currentClean = current.replace(Regex("[^0-9.]"), "")

        val lParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val lVal = lParts.getOrElse(i) { 0 }
            val cVal = cParts.getOrElse(i) { 0 }
            if (lVal > cVal) return true
            if (lVal < cVal) return false
        }
        return false
    }
}
