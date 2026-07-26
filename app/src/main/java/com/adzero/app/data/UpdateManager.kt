package com.adzero.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.adzero.app.BuildConfig
import com.adzero.app.Constants
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String = ""
)

object UpdateManager {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(Constants.UPDATE_JSON_URL)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val info = gson.fromJson(body, UpdateInfo::class.java)
                
                if (info.versionCode > BuildConfig.VERSION_CODE) {
                    return@withContext info
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    fun openDownloadPage(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DEFAULT_APK_URL))
            context.startActivity(fallbackIntent)
        }
    }
}
