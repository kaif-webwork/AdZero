package com.adzero.app

import android.app.Application
import com.adzero.app.data.GlobalPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.RequestBody.Companion.toRequestBody

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

class App : Application(), ImageLoaderFactory {

    companion object {
        val isExtractorInitialized = AtomicBoolean(false)
        lateinit var okHttpClient: OkHttpClient
            private set
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024L * 1024L) // 100 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        
        okHttpClient = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        // Initialize NewPipe and Player on background threads
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userLang = com.adzero.app.data.ContentLanguageManager.getCurrentLanguage(this@App)
                val loc = Localization(userLang.languageCode, userLang.countryCode)
                NewPipe.init(NewPipeDownloader.getInstance(okHttpClient), loc)
                isExtractorInitialized.set(true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Warm up the player, initialize MicroGManager & pre-warm feed cache
        com.adzero.app.data.MicroGManager.init(this)
        com.adzero.app.data.WarmFeedCache.prewarm(this)
        GlobalPlayerManager.getPlayer(this)
    }
    
    override fun onTerminate() {
        GlobalPlayerManager.release()
        super.onTerminate()
    }
}

/**
 * NewPipe Extractor requires an HTTP Downloader implementation.
 * We implement it using OkHttp.
 */
class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Safari/537.36"

        fun getInstance(client: OkHttpClient): NewPipeDownloader {
            return NewPipeDownloader(client)
        }
    }

    @Throws(Exception::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody: okhttp3.RequestBody? = dataToSend?.let {
            it.toRequestBody()
        }

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        for ((headerName, headerValueList) in headers) {
            for (headerValue in headerValueList) {
                requestBuilder.addHeader(headerName, headerValue)
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBody = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            latestUrl
        )
    }
}
