package com.adzero.app.models

data class VideoStream(
    val url: String,
    val quality: String,
    val format: String = "mp4",
    val isVideoOnly: Boolean = false,
    val isHls: Boolean = false,
    val displayName: String = "",      // Human-readable track name, e.g. "English", "Hindi (Dubbed)"
    val isOriginalTrack: Boolean = false // true for original audio, false for dubbed tracks
)

data class Video(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val views: String,
    val uploadDate: String,
    val duration: String,
    val isVerified: Boolean = false,
    val subscriberCount: String = "0",
    val likes: String = "0",
    val description: String = "",
    val videoUrl: String = "",
    val availableStreams: List<VideoStream> = emptyList(),
    val isLive: Boolean = false
)

data class Comment(
    val id: String,
    val author: String,
    val authorAvatarUrl: String,
    val text: String,
    val time: String,
    val likeCount: Long, // Changed to Long for consistency
    val hasHeart: Boolean = false,
    val repliesCount: Int = 0
)

data class Creator(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val isLive: Boolean = false,
    val notificationCount: Int = 0,
    val hasStory: Boolean = false
)

fun formatNumberCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> "%.1fB".format(count / 1_000_000_000.0)
        count >= 1_000_000     -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000         -> "%.0fK".format(count / 1_000.0)
        count > 0              -> count.toString()
        else                   -> "0"
    }
}

fun org.schabi.newpipe.extractor.comments.CommentsInfoItem.toComment(): Comment {
    return Comment(
        id = commentId ?: "",
        author = uploaderName ?: "Anonymous",
        authorAvatarUrl = uploaderAvatars.firstOrNull()?.url ?: "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png",
        text = commentText.content ?: "",
        time = textualUploadDate ?: "",
        likeCount = likeCount.toLong(),
        hasHeart = isHeartedByUploader,
        repliesCount = replyCount
    )
}

fun org.schabi.newpipe.extractor.stream.StreamInfoItem.toVideo(): Video {
    val extractedId = when {
        url.contains("v=") -> url.substringAfter("v=").substringBefore("&").substringBefore("?")
        url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
        else -> url.substringAfterLast("/").substringBefore("?").substringBefore("&")
    }.ifBlank { url }

    val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else if (extractedId.isNotBlank() && !extractedId.startsWith("/")) {
        "https://www.youtube.com/watch?v=$extractedId"
    } else {
        "https://www.youtube.com${if (url.startsWith("/")) url else "/$url"}"
    }

    val isLiveStream = streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM || 
                       streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM

    val durationSec = duration
    val h = durationSec / 3600
    val m = (durationSec % 3600) / 60
    val s = durationSec % 60
    val durationStr = if (isLiveStream) "LIVE"
    else if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)

    val viewCountLong = viewCount
    val viewsStr = when {
        viewCountLong >= 1_000_000_000 -> "%.1fB views".format(viewCountLong / 1_000_000_000.0)
        viewCountLong >= 1_000_000     -> "%.1fM views".format(viewCountLong / 1_000_000.0)
        viewCountLong >= 1_000         -> "%.0fK views".format(viewCountLong / 1_000.0)
        viewCountLong > 0              -> "$viewCountLong views"
        else                           -> ""
    }

    val rawThumb = thumbnails?.maxByOrNull { it.width }?.url
        ?: thumbnails?.lastOrNull()?.url
        ?: "https://i.ytimg.com/vi/$extractedId/hqdefault.jpg"

    val cleanThumb = when {
        rawThumb.startsWith("//") -> "https:$rawThumb"
        !rawThumb.startsWith("http") && extractedId.isNotBlank() -> "https://i.ytimg.com/vi/$extractedId/hqdefault.jpg"
        else -> rawThumb
    }

    return Video(
        id = extractedId,
        title = name ?: "",
        thumbnailUrl = cleanThumb,
        channelName = uploaderName ?: "",
        channelAvatarUrl = uploaderAvatars?.maxByOrNull { it.width }?.url ?: uploaderAvatars?.firstOrNull()?.url ?: "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png",
        views = viewsStr,
        uploadDate = textualUploadDate ?: "",
        duration = durationStr,
        isVerified = false,
        subscriberCount = "",
        likes = "",
        description = "",
        videoUrl = fullUrl,
        isLive = isLiveStream
    )
}

