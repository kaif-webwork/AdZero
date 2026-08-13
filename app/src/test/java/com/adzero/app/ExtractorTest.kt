package com.adzero.app

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

class ExtractorTest {

    class TestDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val httpMethod = request.httpMethod()
            val url = request.url()
            val headers = request.headers()
            val dataToSend = request.dataToSend()

            val requestBody: okhttp3.RequestBody? = dataToSend?.let { it.toRequestBody() }

            val requestBuilder = okhttp3.Request.Builder()
                .method(httpMethod, requestBody)
                .url(url)

            for ((headerName, headerValueList) in headers) {
                for (headerValue in headerValueList) {
                    requestBuilder.addHeader(headerName, headerValue)
                }
            }

            if (headers["User-Agent"] == null) {
                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            val response = client.newCall(requestBuilder.build()).execute()
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

    @Test
    fun testYouTubeSearchAndExtraction() {
        val okHttpClient = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()

        NewPipe.init(TestDownloader(okHttpClient), Localization.DEFAULT)

        println("Testing SearchInfo...")
        val handler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery("music", emptyList(), "")
        val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, handler)
        println("Search results count: ${searchInfo.relatedItems.size}")
        assert(searchInfo.relatedItems.isNotEmpty())

        val firstItem = searchInfo.relatedItems.first()
        println("First item: ${firstItem.name} - ${firstItem.url}")

        println("Testing StreamInfo extraction for ${firstItem.url}...")
        val streamInfo = StreamInfo.getInfo(firstItem.url)
        println("Stream title: ${streamInfo.name}")
        println("Audio streams count: ${streamInfo.audioStreams?.size ?: 0}")
        println("Video streams count: ${streamInfo.videoStreams?.size ?: 0}")
    }
}
