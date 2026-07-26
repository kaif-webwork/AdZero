package com.adzero.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adzero.app.components.SkeletonLoader
import com.adzero.app.components.VideoCard
import com.adzero.app.data.RealAccountSyncManager
import com.adzero.app.data.SubscriptionManager
import com.adzero.app.data.UserAccountManager
import com.adzero.app.models.Creator
import com.adzero.app.models.Video
import com.adzero.app.models.toVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isLoggedIn = UserAccountManager.isLoggedIn
    val isSyncing = RealAccountSyncManager.isSyncing
    val syncError = RealAccountSyncManager.lastSyncError

    // Real subscribed channels from the user's account
    val subscribedChannels = SubscriptionManager.subscribedChannels.values.toList()
    // Real subscription feed videos from the user's account
    val realFeedVideos = SubscriptionManager.subscriptionFeedVideos.toList()

    // Fallback state — used only when NOT logged in
    var fallbackCreators by remember { mutableStateOf<List<Creator>>(emptyList()) }
    var fallbackFeedVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isFallbackLoading by remember { mutableStateOf(false) }

    // Load fallback content only when user is not logged in
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn && fallbackFeedVideos.isEmpty()) {
            isFallbackLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val service = ServiceList.YouTube
                    val channelQueryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                        .fromQuery("tech channels", emptyList(), "")
                    val channelSearchInfo = SearchInfo.getInfo(service, channelQueryHandler)

                    val extractedCreators = channelSearchInfo.relatedItems
                        .filterIsInstance<ChannelInfoItem>()
                        .take(8)
                        .mapIndexed { index, item ->
                            Creator(
                                id = item.url,
                                name = item.name ?: "Channel",
                                avatarUrl = item.thumbnails?.firstOrNull()?.url
                                    ?: "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png",
                                isLive = index == 0,
                                hasStory = index < 4
                            )
                        }

                    val feedQueryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                        .fromQuery("technology news", emptyList(), "")
                    val feedSearchInfo = SearchInfo.getInfo(service, feedQueryHandler)
                    val feedVideos = feedSearchInfo.relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toVideo() }

                    withContext(Dispatchers.Main) {
                        fallbackCreators = extractedCreators
                        fallbackFeedVideos = feedVideos
                        isFallbackLoading = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) { isFallbackLoading = false }
                }
            }
        }
    }

    // Determine what to display
    val displayCreators: List<Any> = when {
        isLoggedIn && subscribedChannels.isNotEmpty() -> subscribedChannels
        !isLoggedIn -> fallbackCreators
        else -> emptyList()
    }
    val displayVideos: List<Video> = when {
        isLoggedIn && realFeedVideos.isNotEmpty() -> realFeedVideos
        !isLoggedIn -> fallbackFeedVideos
        else -> emptyList()
    }
    val isLoading = isSyncing || (!isLoggedIn && isFallbackLoading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Subscriptions",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (isLoggedIn && subscribedChannels.isNotEmpty()) {
                            Text(
                                text = "${subscribedChannels.size} channels",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    // Refresh button — only shown when logged in
                    if (isLoggedIn) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val cookies = UserAccountManager.userCookies
                                    if (!cookies.isNullOrBlank()) {
                                        RealAccountSyncManager.syncAccountWithCookies(context, cookies)
                                    }
                                }
                            },
                            enabled = !isSyncing
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = if (isSyncing) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                       else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "List",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Sync loading bar ───────────────────────────────────────────
            if (isSyncing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFF0000)
                )
            }

            // ── Sync error banner ──────────────────────────────────────────
            if (!syncError.isNullOrBlank() && isLoggedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = syncError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── "Sign in" prompt when not logged in ────────────────────────
            if (!isLoggedIn) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Sign in to see videos from your subscribed channels",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Channel avatar row ─────────────────────────────────────────
            if (displayCreators.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isLoggedIn) {
                        items(subscribedChannels) { channel ->
                            RealChannelAvatarItem(
                                name = channel.name,
                                avatarUrl = channel.avatarUrl,
                                onClick = { onChannelClick(channel.name) }
                            )
                        }
                    } else {
                        items(fallbackCreators) { creator ->
                            CreatorAvatarItem(
                                creator = creator,
                                onChannelClick = onChannelClick
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            }

            // ── Video feed ─────────────────────────────────────────────────
            when {
                isLoading -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(5) { SkeletonLoader() }
                    }
                }

                isLoggedIn && realFeedVideos.isEmpty() && !isSyncing -> {
                    // Logged in but no feed data yet
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Your subscription feed is loading…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            if (!syncError.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val cookies = UserAccountManager.userCookies
                                            if (!cookies.isNullOrBlank()) {
                                                RealAccountSyncManager.syncAccountWithCookies(context, cookies)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF0000)
                                    )
                                ) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Sync")
                                }
                            }
                        }
                    }
                }

                displayVideos.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No videos available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 64.dp)
                    ) {
                        items(displayVideos, key = { it.id }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onChannelClick = onChannelClick
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

/** Avatar item for a real subscribed channel from the user's account. */
@Composable
fun RealChannelAvatarItem(
    name: String,
    avatarUrl: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .width(64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = Color(0xFFFF0000),
                    shape = CircleShape
                )
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Avatar item for a fallback / public channel (not from user's account). */
@Composable
fun CreatorAvatarItem(
    creator: Creator,
    onChannelClick: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onChannelClick(creator.name) }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(
                    width = if (creator.hasStory) 2.5.dp else 0.dp,
                    color = if (creator.isLive) Color.Red else MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .padding(if (creator.hasStory) 3.dp else 0.dp)
        ) {
            AsyncImage(
                model = creator.avatarUrl,
                contentDescription = creator.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            if (creator.isLive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Red)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = creator.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}
