package com.adzero.app.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

/**
 * Handles background file downloads (MP4 Video, MP3/M4A Audio, and Clips)
 * directly to the user's device storage using Android's system DownloadManager.
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
