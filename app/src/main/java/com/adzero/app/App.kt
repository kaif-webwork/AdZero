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
        lateinit var instance: App
            private set
        val isExtractorInitialized = AtomicBoolean(false)
        lateinit var okHttpClient: OkHttpClient
            private set
        val streamingHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .connectionPool(okhttp3.ConnectionPool(20, 5, TimeUnit.MINUTES))
                .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                .build()
        }
    }

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager
        val isLowRamDevice = activityManager?.isLowRamDevice == true

        return ImageLoader.Builder(this)
            .allowHardware(!isLowRamDevice) // Hardware bitmaps on supported devices for 120Hz rendering
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565) // 50% RAM reduction per bitmap compared to ARGB_8888
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRamDevice) 0.15 else 0.25) // Generous RAM cache prevents re-decoding during scroll
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L) // 150 MB disk cache prevents constant cache eviction
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false) // Instant 0ms render from cache eliminates scroll animation jank
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        okHttpClient = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectionPool(okhttp3.ConnectionPool(50, 10, TimeUnit.MINUTES))
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            .build()

        // Synchronously initialize NewPipe extractor to avoid race condition with UI screens
        try {
            val userLang = com.adzero.app.data.ContentLanguageManager.getCurrentLanguage(this)
            val loc = Localization(userLang.languageCode, userLang.countryCode)
            NewPipe.init(NewPipeDownloader.getInstance(okHttpClient), loc)
            isExtractorInitialized.set(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            com.adzero.app.data.HistoryManager.init(this)
            com.adzero.app.data.PlayerQualityManager.init(this)
            com.adzero.app.data.WarmFeedCache.prewarm(this)
            GlobalPlayerManager.getPlayer(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= TRIM_MEMORY_RUNNING_LOW || level >= TRIM_MEMORY_MODERATE) {
                coil.Coil.imageLoader(this).memoryCache?.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            coil.Coil.imageLoader(this).memoryCache?.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTerminate() {
        GlobalPlayerManager.release()
        com.adzero.app.data.ShortsPlayerManager.release()
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

        for ((headerName, headerValueList) in headers) {
            for (headerValue in headerValueList) {
                requestBuilder.addHeader(headerName, headerValue)
            }
        }

        if (headers["User-Agent"] == null) {
            requestBuilder.header("User-Agent", USER_AGENT)
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
