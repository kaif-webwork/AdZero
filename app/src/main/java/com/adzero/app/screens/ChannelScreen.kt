package com.adzero.app.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adzero.app.components.YouTubeLoading
import com.adzero.app.components.SkeletonLoader
import com.adzero.app.components.VideoCard
import com.adzero.app.data.ExtractionManager
import com.adzero.app.models.Video
import com.adzero.app.models.toVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelName: String,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var isSubscribed by remember { mutableStateOf(false) }
    var isBellActive by remember { mutableStateOf(true) }
    var isGridView by remember { mutableStateOf(false) }
    var channelVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isMoreLoading by remember { mutableStateOf(false) }
    var channelNextPage by remember { mutableStateOf<org.schabi.newpipe.extractor.Page?>(null) }
    val channelListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val tabs = listOf("Videos", "Shorts", "Playlists", "Community", "About")

    LaunchedEffect(channelName) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery(channelName, emptyList(), "")
                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                val items = searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo() }
                
                withContext(Dispatchers.Main) {
                    channelVideos = items
                    channelNextPage = searchInfo.nextPage
                    isLoading = false
                    items.take(6).forEach { video ->
                        ExtractionManager.startExtraction(video, isSpeculative = true)
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

    fun loadMoreChannelVideos() {
        if (isMoreLoading || channelNextPage == null || channelName.isBlank()) return
        isMoreLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery(channelName, emptyList(), "")
                val moreInfo = SearchInfo.getMoreItems(service, queryHandler, channelNextPage)
                val existingIds = channelVideos.map { it.id }.toSet()
                val newItems = moreInfo.items
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo() }
                    .filter { it.id !in existingIds }

                withContext(Dispatchers.Main) {
                    channelVideos = channelVideos + newItems
                    channelNextPage = moreInfo.nextPage
                    isMoreLoading = false
                    newItems.take(4).forEach { video ->
                        ExtractionManager.startExtraction(video, isSpeculative = true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isMoreLoading = false }
            }
        }
    }

    val shouldLoadMoreChannel = remember {
        derivedStateOf {
            val totalItems = channelListState.layoutInfo.totalItemsCount
            val lastVisibleItem = channelListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMoreChannel.value) {
        if (shouldLoadMoreChannel.value && !isLoading && !isMoreLoading && channelVideos.isNotEmpty()) {
            loadMoreChannelVideos()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = channelName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.Cast, contentDescription = "Cast")
                    }
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search channel")
                    }
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Channel Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                            )
                        )
                    )
            ) {
                AsyncImage(
                    model = channelVideos.firstOrNull()?.thumbnailUrl ?: "",
                    contentDescription = "Channel Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )
            }

            // Channel Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Channel Avatar
                    AsyncImage(
                        model = channelVideos.firstOrNull()?.channelAvatarUrl ?: "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png",
                        contentDescription = channelName,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channelName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "@${channelName.lowercase().replace(" ", "")} • 1.2M subscribers • ${channelVideos.size} videos",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Subscribe Button & Bell Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isSubscribed = !isSubscribed
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else Color.Red,
                            contentColor = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "SUBSCRIBED" else "SUBSCRIBE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (isSubscribed) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isBellActive = !isBellActive
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = if (isBellActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                contentDescription = "Notifications",
                                tint = if (isBellActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Tabs Header Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = index
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Tab Content Body
            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(4) { SkeletonLoader() }
                    }
                } else {
                    when (selectedTab) {
                        0 -> { // Videos Tab
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Latest Videos (${channelVideos.size})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { isGridView = !isGridView },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                                            contentDescription = "Toggle Grid/List",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isGridView) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(channelVideos) { video ->
                                            GridVideoCard(video = video, onClick = { onVideoClick(video) })
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        state = channelListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 80.dp)
                                    ) {
                                        items(channelVideos, key = { it.id }) { video ->
                                            VideoCard(video = video, onClick = { onVideoClick(video) })
                                        }

                                        if (isMoreLoading) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    YouTubeLoading()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> { // Shorts Tab
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Shorts by $channelName available in Shorts tab", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        2 -> { // Playlists Tab
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No public playlists found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        3 -> { // Community Tab
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Community posts not available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        4 -> { // About Tab
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Description", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Text("Welcome to the official $channelName channel! Stay tuned for high quality video content, tutorials, and tech reviews.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))
                                Text("Channel Stats", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Text("Joined: Jan 2021", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Total Views: 154,820,100 views", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Location: Worldwide", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GridVideoCard(
    video: Video,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (video.duration.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(text = video.duration, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = video.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.views,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
