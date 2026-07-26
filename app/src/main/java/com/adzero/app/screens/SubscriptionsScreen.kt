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
import com.adzero.app.data.SubscriptionManager
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
    onChannelClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Subscribed channels map from SubscriptionManager (local user subscriptions)
    val subscribedChannels = SubscriptionManager.subscribedChannels.values.toList()

    var channelsState by remember { mutableStateOf<List<Creator>>(emptyList()) }
    var feedVideosState by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun loadSubscriptionContent() {
        isLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val service = ServiceList.YouTube
                    val channelQueryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                        .fromQuery("tech channels", emptyList(), "")
                    val channelSearchInfo = SearchInfo.getInfo(service, channelQueryHandler)

                    val extractedCreators = channelSearchInfo.relatedItems
                        .filterIsInstance<ChannelInfoItem>()
                        .take(10)
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
                        channelsState = extractedCreators
                        feedVideosState = feedVideos
                        isLoading = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadSubscriptionContent()
    }

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
                        if (subscribedChannels.isNotEmpty()) {
                            Text(
                                text = "${subscribedChannels.size} channels",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { loadSubscriptionContent() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Local Subscribed Channels / Creators Row ──────────────────────
            val displayChannels = if (subscribedChannels.isNotEmpty()) {
                subscribedChannels.map {
                    Creator(
                        id = it.name,
                        name = it.name,
                        avatarUrl = it.avatarUrl,
                        hasStory = true
                    )
                }
            } else channelsState

            if (displayChannels.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(displayChannels) { creator ->
                        SubscribedCreatorAvatarItem(
                            creator = creator,
                            onChannelClick = onChannelClick
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            }

            // ── Video Feed ─────────────────────────────────────────────────
            when {
                isLoading -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(5) { SkeletonLoader() }
                    }
                }

                feedVideosState.isEmpty() -> {
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
                        items(feedVideosState, key = { it.id }) { video ->
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

@Composable
fun SubscribedCreatorAvatarItem(
    creator: Creator,
    onChannelClick: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onChannelClick(creator.name) }
            .width(64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(
                    width = if (creator.hasStory) 2.dp else 0.dp,
                    color = if (creator.isLive) Color.Red else Color(0xFFFF0000),
                    shape = CircleShape
                )
                .padding(if (creator.hasStory) 2.dp else 0.dp)
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
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
