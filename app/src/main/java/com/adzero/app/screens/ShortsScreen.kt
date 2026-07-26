package com.adzero.app.screens

import android.widget.FrameLayout
import android.widget.Toast
import android.view.ViewGroup
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.adzero.app.models.Video
import com.adzero.app.models.toVideo
import com.adzero.app.models.toComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var shortsList by remember { mutableStateOf(emptyList<Video>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isMoreLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadMoreShorts() {
        if (isMoreLoading) return
        isMoreLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val randomShortQueries = listOf("youtube shorts", "trending shorts", "viral shorts", "funny shorts", "gaming shorts", "music shorts", "shorts india", "tech shorts", "diy shorts", "dance shorts")
                val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery(randomShortQueries.random(), emptyList(), "")
                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                val fetched = searchInfo.relatedItems
                    ?.filterIsInstance<StreamInfoItem>()
                    ?.map { it.toVideo() }?.shuffled() ?: emptyList()

                val baseItems = fetched

                // Ensure unique IDs so infinite scrolling never stalls or crashes
                val newItems = baseItems.mapIndexed { idx, v ->
                    v.copy(id = "${v.id}_page_${shortsList.size + idx}")
                }

                withContext(Dispatchers.Main) {
                    if (newItems.isNotEmpty()) {
                        shortsList = shortsList + newItems
                        newItems.take(6).forEach { video ->
                            com.adzero.app.data.ExtractionManager.startExtraction(video, isSpeculative = true)
                        }
                    }
                    isMoreLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isMoreLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val fastShorts = com.adzero.app.data.FastContentStore.getFeed(context, "Shorts")
        val warmShorts = com.adzero.app.data.WarmFeedCache.getFeed("Shorts")
        val initialShorts = if (fastShorts.isNotEmpty()) fastShorts else warmShorts
        if (!initialShorts.isNullOrEmpty()) {
            shortsList = initialShorts
            isLoading = false
        }

        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val queries = listOf("youtube shorts", "trending shorts", "viral shorts")
                val initialList = mutableListOf<Video>()

                for (q in queries) {
                    try {
                        val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(q, emptyList(), "")
                        val searchInfo = SearchInfo.getInfo(service, queryHandler)
                        val fetched = searchInfo.relatedItems
                            ?.filterIsInstance<StreamInfoItem>()
                            ?.map { it.toVideo() } ?: emptyList()
                        initialList.addAll(fetched)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val items = initialList.distinctBy { it.id }.shuffled()
                
                withContext(Dispatchers.Main) {
                    if (items.isNotEmpty()) {
                        shortsList = items
                        isLoading = false
                        com.adzero.app.data.FastContentStore.saveFeed(context, "Shorts", items)
                        // Start speculative pre-extraction for instant loading
                        items.take(8).forEach { video ->
                            com.adzero.app.data.ExtractionManager.startExtraction(video, isSpeculative = true)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { shortsList.size })

    // Infinite Shorts Pagination: Trigger when user is within 5 shorts of the end
    val shouldLoadMoreShorts = remember {
        derivedStateOf {
            val total = shortsList.size
            val current = pagerState.currentPage
            total > 0 && current >= total - 5
        }
    }

    LaunchedEffect(shouldLoadMoreShorts.value) {
        if (shouldLoadMoreShorts.value && !isLoading && !isMoreLoading && shortsList.isNotEmpty()) {
            loadMoreShorts()
        }
    }

    // Pre-extract upcoming shorts as user scrolls for ZERO buffering
    LaunchedEffect(pagerState.currentPage, shortsList) {
        val current = pagerState.currentPage
        for (i in (current + 1)..(current + 3)) {
            if (i in shortsList.indices) {
                com.adzero.app.data.ExtractionManager.startExtraction(shortsList[i], isSpeculative = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.adzero.app.components.YouTubeLoading()
            }
        } else if (shortsList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.adzero.app.components.YouTubeLoading()
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val isVisible = page == pagerState.currentPage
                ShortsItem(
                    video = shortsList[page],
                    isVisible = isVisible,
                    onCommentsClick = {
                        Toast.makeText(context, "Comments feature available in player view", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Top Gradient Scrim for crisp header visibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            Text(
                text = "Shorts",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {},
                modifier = Modifier.size(36.dp)
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsItem(
    video: Video,
    isVisible: Boolean,
    onCommentsClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLiked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var showPauseOverlay by remember { mutableStateOf(false) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var commentsList by remember { mutableStateOf<List<com.adzero.app.models.Comment>>(emptyList()) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(showCommentsSheet) {
        if (showCommentsSheet && commentsList.isEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val targetUrl = if (video.videoUrl.startsWith("http")) video.videoUrl else "https://www.youtube.com/watch?v=${video.id}"
                    val info = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(targetUrl)
                    val comments = info.relatedItems.map { it.toComment() }
                    withContext(Dispatchers.Main) { commentsList = comments }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val exoPlayer = remember {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 40_000, 1_200, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
            }
    }

    LaunchedEffect(video, isVisible) {
        if (!isVisible) {
            exoPlayer.pause()
            return@LaunchedEffect
        }

        if (streamUrl == null) {
            // 1. Direct MP4 check (Instant 0ms)
            if (video.videoUrl.endsWith(".mp4") || (video.videoUrl.startsWith("http") && !video.videoUrl.contains("youtube.com"))) {
                streamUrl = video.videoUrl
                exoPlayer.setMediaItem(MediaItem.fromUri(video.videoUrl))
                exoPlayer.prepare()
                exoPlayer.play()
                return@LaunchedEffect
            }

            // 2. Pre-extracted cache check (Instant 0ms with adaptive network quality)
            val cachedInfo = com.adzero.app.data.ExtractionManager.getCachedInfo(video.id)
            if (cachedInfo != null) {
                val optimalUrl = com.adzero.app.data.ExtractionManager.getOptimalStreamForNetwork(context, cachedInfo)
                if (optimalUrl != null) {
                    streamUrl = optimalUrl
                    exoPlayer.setMediaItem(MediaItem.fromUri(optimalUrl))
                    exoPlayer.prepare()
                    exoPlayer.play()
                    return@LaunchedEffect
                }
            }

            // 3. Fast real stream extraction
            withContext(Dispatchers.IO) {
                try {
                    val targetUrl = if (video.videoUrl.startsWith("http")) video.videoUrl else "https://www.youtube.com/watch?v=${video.id}"
                    val info = try { StreamInfo.getInfo(targetUrl) } catch(e: Exception) { null }
                    val optimalUrl = if (info != null) com.adzero.app.data.ExtractionManager.getOptimalStreamForNetwork(context, info) else null

                    if (optimalUrl != null) {
                        withContext(Dispatchers.Main) {
                            streamUrl = optimalUrl
                            exoPlayer.setMediaItem(MediaItem.fromUri(optimalUrl))
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            exoPlayer.play()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) exoPlayer.pause()
            if (event == Lifecycle.Event.ON_RESUME && isVisible && isPlaying) exoPlayer.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        isPlaying = !isPlaying
                        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                        showPauseOverlay = true
                    },
                    onDoubleTap = { isLiked = true }
                )
            }
    ) {
        // High-res full-bleed thumbnail displayed quietly in background until video is ready
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (streamUrl != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 90.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Channel row: avatar + name + subscribe button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = video.channelAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = "@${video.channelName}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val isSubscribed = com.adzero.app.data.SubscriptionManager.isSubscribed(video.channelName)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSubscribed) Color.White.copy(alpha = 0.2f) else Color.White)
                        .clickable {
                            com.adzero.app.data.SubscriptionManager.toggleSubscription(video.channelName, video.channelAvatarUrl)
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isSubscribed) "Subscribed" else "Subscribe",
                        color = if (isSubscribed) Color.White else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Video title
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )

            // Sound/audio row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(
                    text = "Original Audio • ${video.channelName}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right sidebar actions
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ShortsAction(
                icon = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                label = if (video.likes.isNotBlank()) video.likes else "Like",
                tint = if (isLiked) Color(0xFF3EA6FF) else Color.White,
                onClick = { isLiked = !isLiked }
            )
            ShortsAction(icon = Icons.Default.Comment, label = "Comments", onClick = { showCommentsSheet = true })
            ShortsAction(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, video.title)
                        putExtra(android.content.Intent.EXTRA_TEXT, "Watch '${video.title}' on YouTube: https://youtu.be/${video.id}")
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share video via"))
                }
            )
            ShortsAction(icon = Icons.Default.MoreVert, label = "More", onClick = {})

            // Rotating vinyl (sound) disc
            val rotation = rememberInfiniteTransition(label = "disc")
            val angle by rotation.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing)),
                label = "discAngle"
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .graphicsLayer { rotationZ = if (isPlaying) angle else 0f },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = video.channelAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .align(Alignment.Center)
                )
            }
        }

        if (showPauseOverlay) {
            LaunchedEffect(isPlaying) {
                delay(600.milliseconds)
                showPauseOverlay = false
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
        }

        // Real YouTube Comments Sheet for Shorts
        if (showCommentsSheet) {
            com.adzero.app.components.CommentBottomSheet(
                comments = commentsList,
                onClose = { showCommentsSheet = false },
                sheetState = sheetState
            )
        }
    }
}

@Composable
fun ShortsAction(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f)))
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        if (label.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
