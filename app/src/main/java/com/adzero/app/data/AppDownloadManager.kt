package com.adzero.app.data

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.adzero.app.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer

/**
 * Handles background file downloads (MP4 Video with Audio, MP3/M4A Audio, and Clips)
 * directly to the user's device storage. Automatically muxes DASH video + audio streams into a single MP4 file.
 */
object AppDownloadManager {

    /**
     * Sanitizes a string for safe use as a filename on Android storage.
     */
    private fun sanitizeFilename(input: String): String {
        return input.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(100)
    }

    /**
     * Downloads an MP4 video with full audio stream.
     * Automatically handles muxing DASH video-only and audio streams into a single MP4 file.
     */
    fun downloadVideoWithAudio(
        context: Context,
        videoTitle: String,
        videoUrl: String,
        audioUrl: String?,
        qualityOrTypeLabel: String,
        isVideoOnly: Boolean
    ) {
        if (videoUrl.isBlank()) {
            Toast.makeText(context, "Download link unavailable for this quality", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanTitle = sanitizeFilename(videoTitle.ifBlank { "AdZero_Video" })
        val cleanLabel = qualityOrTypeLabel.replace("\\s+".toRegex(), "_")
        val finalFilename = "${cleanTitle}_${cleanLabel}.mp4"

        // Case 1: Progressive stream (already has video + audio merged in single file) or no audio stream available
        if (!isVideoOnly || audioUrl.isNullOrBlank()) {
            downloadStream(
                context = context,
                videoTitle = videoTitle,
                url = videoUrl,
                qualityOrTypeLabel = qualityOrTypeLabel,
                isAudioOnly = false,
                extension = "mp4"
            )
            return
        }

        // Case 2: DASH video-only stream -> Download video + audio in background & mux with MediaMuxer
        Toast.makeText(context, "⏬ Downloading $qualityOrTypeLabel MP4 Video + Audio...", Toast.LENGTH_LONG).show()

        CoroutineScope(Dispatchers.IO).launch {
            val cacheDir = context.cacheDir
            val tempVideoFile = File(cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
            val tempAudioFile = File(cacheDir, "temp_audio_${System.currentTimeMillis()}.m4a")
            val tempMergedFile = File(cacheDir, "temp_merged_${System.currentTimeMillis()}.mp4")

            try {
                // Download video and audio files concurrently using OkHttp
                val vSuccess = downloadFileWithOkHttp(videoUrl, tempVideoFile)
                val aSuccess = downloadFileWithOkHttp(audioUrl, tempAudioFile)

                if (!vSuccess || !tempVideoFile.exists() || tempVideoFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to download video stream", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Mux video and audio into a single MP4 file if audio was downloaded
                val canMux = aSuccess && tempAudioFile.exists() && tempAudioFile.length() > 0L
                val fileToSave = if (canMux) {
                    val muxed = muxVideoAndAudio(tempVideoFile, tempAudioFile, tempMergedFile)
                    if (muxed && tempMergedFile.exists() && tempMergedFile.length() > 0L) {
                        tempMergedFile
                    } else {
                        tempVideoFile
                    }
                } else {
                    tempVideoFile
                }

                // Save merged file to user's Downloads/AdZero folder
                val savedUri = saveToDownloads(context, fileToSave, finalFilename, "video/mp4")

                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        Toast.makeText(context, "✅ Download Complete: $cleanTitle\nSaved to Downloads/AdZero", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save download file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // Clean up temporary cache files
                try { if (tempVideoFile.exists()) tempVideoFile.delete() } catch (e: Exception) {}
                try { if (tempAudioFile.exists()) tempAudioFile.delete() } catch (e: Exception) {}
                try { if (tempMergedFile.exists()) tempMergedFile.delete() } catch (e: Exception) {}
            }
        }
    }

    private fun downloadFileWithOkHttp(url: String, targetFile: File): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .build()

            App.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                targetFile.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        return try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            var videoTrackIndex = -1
            var audioTrackIndex = -1

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    break
                }
            }

            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }

            if (videoTrackIndex == -1) return false

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            videoExtractor.selectTrack(videoTrackIndex)
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val muxerVideoTrack = muxer.addTrack(videoFormat)

            val muxerAudioTrack = if (audioTrackIndex != -1) {
                audioExtractor.selectTrack(audioTrackIndex)
                val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
                muxer.addTrack(audioFormat)
            } else -1

            muxer.start()

            val bufferSize = 1024 * 1024
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy Video Samples
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Copy Audio Samples
            if (audioTrackIndex != -1 && muxerAudioTrack != -1) {
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            videoExtractor?.release()
            audioExtractor?.release()
            try { muxer?.release() } catch (e: Exception) {}
        }
    }

    private fun saveToDownloads(context: Context, sourceFile: File, filename: String, mimeType: String): Uri? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AdZero")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return uri
            }
        }

        // Legacy fallback
        val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AdZero")
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, filename)
        sourceFile.copyTo(destFile, overwrite = true)

        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
        return Uri.fromFile(destFile)
    }

    /**
     * Initiates a background download via system DownloadManager.
     */
    fun downloadStream(
        context: Context,
        videoTitle: String,
        url: String,
        qualityOrTypeLabel: String,
        isAudioOnly: Boolean = false,
        extension: String = if (isAudioOnly) "mp3" else "mp4"
    ): Boolean {
        if (url.isBlank()) {
            Toast.makeText(context, "Download link unavailable for this quality", Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                Toast.makeText(context, "System Download Manager unavailable", Toast.LENGTH_SHORT).show()
                return false
            }

            val cleanTitle = sanitizeFilename(videoTitle.ifBlank { "AdZero_Media" })
            val cleanLabel = qualityOrTypeLabel.replace("\\s+".toRegex(), "_")
            val filename = "${cleanTitle}_${cleanLabel}.$extension"
            val destinationDir = if (isAudioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(cleanTitle)
                setDescription("Downloading $qualityOrTypeLabel ad-free media")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(destinationDir, "AdZero/$filename")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                if (isAudioOnly) {
                    setMimeType("audio/mpeg")
                } else {
                    setMimeType("video/mp4")
                }
            }

            downloadManager.enqueue(request)
            val storageLoc = if (isAudioOnly) "Music/AdZero" else "Downloads/AdZero"
            Toast.makeText(context, "⏬ Download started: $cleanTitle\nSaved to $storageLoc", Toast.LENGTH_LONG).show()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}
