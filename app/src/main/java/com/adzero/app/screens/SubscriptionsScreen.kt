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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adzero.app.components.SkeletonLoader
import com.adzero.app.components.VideoCard
import com.adzero.app.models.Creator
import com.adzero.app.models.Video
import com.adzero.app.models.toVideo
import kotlinx.coroutines.Dispatchers
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
    var creatorsState by remember { mutableStateOf<List<Creator>>(emptyList()) }
    var feedVideosState by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
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
                            avatarUrl = item.thumbnails?.firstOrNull()?.url ?: "https://www.gstatic.com/youtube/img/creator/avatar/default_64.png",
                            isLive = index == 0,
                            hasStory = index < 4
                        )
                    }

                val creators = if (extractedCreators.isNotEmpty()) extractedCreators else listOf(
                    Creator("1", "Marques Brownlee", "https://yt3.googleusercontent.com/lkH37D712tiAioic8jQf-2_D8g", isLive = true, hasStory = true),
                    Creator("2", "Fireship", "https://yt3.googleusercontent.com/ytc/AIdro_k9", hasStory = true),
                    Creator("3", "Kurzgesagt", "https://yt3.googleusercontent.com/ytc/AIdro_n0", hasStory = true),
                    Creator("4", "Veritasium", "https://yt3.googleusercontent.com/ytc/AIdro_m8", hasStory = false)
                )

                val feedQueryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery("technology news", emptyList(), "")
                val feedSearchInfo = SearchInfo.getInfo(service, feedQueryHandler)
                val feedVideos = feedSearchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo() }

                withContext(Dispatchers.Main) {
                    creatorsState = creators
                    feedVideosState = feedVideos
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Subscriptions",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.List, contentDescription = "List", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (creatorsState.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(creatorsState) { creator ->
                        CreatorAvatarItem(
                            creator = creator,
                            onChannelClick = onChannelClick
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }

            if (isLoading) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(4) {
                        SkeletonLoader()
                    }
                }
            } else if (feedVideosState.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No subscriptions feed available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 64.dp)
                ) {
                    items(feedVideosState) { video ->
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
