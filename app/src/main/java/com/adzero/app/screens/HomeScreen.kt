package com.adzero.app.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adzero.app.components.CategoryChips
import com.adzero.app.components.ShortsShelf
import com.adzero.app.components.SkeletonLoader
import com.adzero.app.components.VideoCard
import com.adzero.app.components.YouTubeLoading
import com.adzero.app.data.ExtractionManager
import com.adzero.app.models.Video
import com.adzero.app.models.toVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onSearchClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onShortsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var selectedCategory by remember { mutableStateOf("All") }
    var videosState by remember { mutableStateOf<List<Video>>(emptyList()) }
    var shortsState by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isMoreLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Per-category cursor for true next-page pagination (NewPipe Page token)
    val nextPageMap = remember { mutableStateMapOf<String, Page?>() }
    // Topic used for the last search, so loadMore appends the right category
    var lastSearchTopic by remember { mutableStateOf("") }

    fun refreshHomeFeed() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val service = ServiceList.YouTube
                    val topics = getCategorySearchQueries(selectedCategory)
                    val topic1 = topics.random()
                    val topic2 = (topics - topic1).firstOrNull() ?: topic1
                    
                    val handler1 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic1, emptyList(), "")
                    val handler2 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic2, emptyList(), "")
                    
                    val res1 = try { SearchInfo.getInfo(service, handler1).relatedItems } catch(e: Exception) { emptyList() }
                    val res2 = try { SearchInfo.getInfo(service, handler2).relatedItems } catch(e: Exception) { emptyList() }
                    
                    val combined = (res1 + res2).filterIsInstance<StreamInfoItem>().map { it.toVideo() }.shuffled()
                    val filtered = if (selectedCategory.equals("Live", ignoreCase = true)) {
                        combined.filter { it.isLive || it.duration.equals("LIVE", ignoreCase = true) }
                    } else combined

                    val freshVideos = filtered
                    
                    withContext(Dispatchers.Main) {
                        if (freshVideos.isNotEmpty()) {
                            videosState = freshVideos
                            com.adzero.app.data.FastContentStore.saveFeed(context, selectedCategory, freshVideos)
                        }
                        isRefreshing = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isRefreshing = false
                    }
                }
            }
        }
    }

    // ── Load more using NextPage cursor (true pagination, no duplicates) ─────
    fun loadMoreVideos() {
        if (isMoreLoading) return
        isMoreLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val existingIds = videosState.map { it.id }.toSet()
                var newItems: List<Video> = emptyList()

                val storedPage = nextPageMap[selectedCategory]
                if (storedPage != null && lastSearchTopic.isNotBlank()) {
                    // ✔ True next-page: continue the same search with cursor
                    try {
                        val handler = YoutubeSearchQueryHandlerFactory.getInstance()
                            .fromQuery(lastSearchTopic, emptyList(), "")
                        val moreInfo = SearchInfo.getMoreItems(service, handler, storedPage)
                        newItems = moreInfo.items
                            .filterIsInstance<StreamInfoItem>()
                            .map { it.toVideo() }
                            .filter { it.id !in existingIds }
                        nextPageMap[selectedCategory] = moreInfo.nextPage
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Fallback: search next targeted query for this specific category
                if (newItems.isEmpty()) {
                    val catTopics = getCategorySearchQueries(selectedCategory)
                    val topic = catTopics.random()
                    lastSearchTopic = topic
                    val handler = YoutubeSearchQueryHandlerFactory.getInstance()
                        .fromQuery(topic, emptyList(), "")
                    val res = try { SearchInfo.getInfo(service, handler) } catch (e: Exception) { null }
                    val rawFetched = (res?.relatedItems ?: emptyList())
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toVideo() }
                    
                    val filtered = if (selectedCategory.equals("Live", ignoreCase = true)) {
                        rawFetched.filter { it.isLive || it.duration.equals("LIVE", ignoreCase = true) }
                    } else rawFetched

                    newItems = filtered.filter { it.id !in existingIds }
                    if (res?.nextPage != null) nextPageMap[selectedCategory] = res.nextPage
                }

                val appended = newItems

                withContext(Dispatchers.Main) {
                    if (appended.isNotEmpty()) {
                        videosState = videosState + appended
                        // Speculatively extract next batch
                        appended.take(4).forEach { ExtractionManager.startExtraction(it, isSpeculative = true) }
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

    // Trigger load more when 6 items from the end (earlier prefetch = smoother scroll)
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && !isMoreLoading && videosState.isNotEmpty()) {
            loadMoreVideos()
        }
    }

    LaunchedEffect(selectedCategory) {
        isLoading = true
        videosState = emptyList() // Immediately clear old category videos to prevent showing wrong data
        shortsState = emptyList()

        val fastCached = com.adzero.app.data.FastContentStore.getFeed(context, selectedCategory)
        val warmCached = com.adzero.app.data.WarmFeedCache.getFeed(selectedCategory)
        val initialFeed = if (fastCached.isNotEmpty()) fastCached else warmCached
        if (!initialFeed.isNullOrEmpty()) {
            videosState = initialFeed
            isLoading = false
        }

        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val catQueries = getCategorySearchQueries(selectedCategory)
                val q1 = catQueries.random()
                val q2 = (catQueries - q1).firstOrNull() ?: q1
                lastSearchTopic = q1

                val handler1 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(q1, emptyList(), "")
                val handler2 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(q2, emptyList(), "")

                val search1Deferred = async { try { SearchInfo.getInfo(service, handler1) } catch(e: Exception) { null } }
                val search2Deferred = async { try { SearchInfo.getInfo(service, handler2) } catch(e: Exception) { null } }

                val search1 = search1Deferred.await()
                val search2 = search2Deferred.await()

                if (search1?.nextPage != null) {
                    nextPageMap[selectedCategory] = search1.nextPage
                }

                val res1 = search1?.relatedItems ?: emptyList()
                val res2 = search2?.relatedItems ?: emptyList()

                val combinedItems = (res1 + res2).filterIsInstance<StreamInfoItem>().map { it.toVideo() }.distinctBy { it.id }

                val filteredVideos = if (selectedCategory.equals("Live", ignoreCase = true)) {
                    combinedItems.filter { it.isLive || it.duration.equals("LIVE", ignoreCase = true) }
                } else {
                    combinedItems
                }

                val shortsQueryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery("#shorts trending", emptyList(), "")
                val shortsSearchInfo = try { SearchInfo.getInfo(service, shortsQueryHandler) } catch(e: Exception) { null }
                val fetchedShorts = (shortsSearchInfo?.relatedItems ?: emptyList())
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo() }
                    .take(8)

                withContext(Dispatchers.Main) {
                    if (filteredVideos.isNotEmpty()) {
                        videosState = filteredVideos
                        com.adzero.app.data.FastContentStore.saveFeed(context, selectedCategory, filteredVideos)
                        filteredVideos.take(3).forEach { video ->
                            ExtractionManager.startExtraction(video, isSpeculative = true)
                        }
                    }
                    if (fetchedShorts.isNotEmpty()) {
                        shortsState = fetchedShorts
                    }
                    isLoading = false
                    isRefreshing = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // ── YouTube 2026 Top Bar ──────────────────────────────────────
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // YouTube-style logo: Red play button + text
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Red),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AdZero",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                actions = {
                    // Search icon
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshHomeFeed() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Category chips
                CategoryChips(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )

                if (isLoading || videosState.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(5) { SkeletonLoader() }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                            itemsIndexed(
                                items = videosState,
                                key = { _, video -> video.id }
                            ) { index, video ->
                                VideoCard(
                                    video = video,
                                    onClick = {
                                        ExtractionManager.startExtraction(video)
                                        onVideoClick(video)
                                    },
                                    onChannelClick = onChannelClick
                                )

                                // Speculative prefetch: extract next 3 videos as user scrolls past index 4+
                                if (index >= 4) {
                                    val nextBatch = videosState.drop(index + 1).take(3)
                                    LaunchedEffect(index) {
                                        nextBatch.forEach { ExtractionManager.startExtraction(it, isSpeculative = true) }
                                    }
                                }

                                // Inject Shorts shelf after the 2nd video (YouTube-style)
                                if (index == 1 && shortsState.isNotEmpty()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                    ShortsShelf(
                                        shorts = shortsState,
                                        onShortClick = onVideoClick,
                                        onSeeAll = onShortsClick
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                }
                            }

                            // Bottom Pagination Loader (Infinite Scroll)
                            if (isMoreLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
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
        }
}

fun getCategorySearchQueries(category: String): List<String> {
    return when (category.lowercase().replace("👤", "").trim()) {
        "gaming" -> listOf(
            "GTA 6 gameplay official", "Minecraft 1.21 update gameplay", "Elden Ring Erdtree boss",
            "BGMI 3.2 gameplay clutch", "VALORANT Champions 2026 highlights", "Techno Gamerz GTA V"
        )
        "music" -> listOf(
            "New Music Video 2026", "Arijit Singh romantic hits live", "Coke Studio Season 15",
            "Lofi beats to relax study 24/7", "Taylor Swift Eras tour live", "Top Bollywood songs 2026"
        )
        "live" -> listOf(
            "Live stream 24/7", "Aaj Tak Live News stream", "Lofi Hip Hop Radio Live",
            "ISRO Launch Live Stream", "Techno Gamerz Live gameplay"
        )
        "podcasts" -> listOf(
            "The Ranveer Show podcast", "Joe Rogan Experience podcast", "Raj Shamani Figuring Out episode",
            "Lex Fridman podcast Sam Altman", "Prakhar ke Pravachan podcast"
        )
        "technology" -> listOf(
            "MKBHD Smartphone Review 2026", "Linus Tech Tips PC build", "Android 15 features review",
            "Tesla Cybercab review", "Google Gemini vs GPT-4o"
        )
        "education" -> listOf(
            "Veritasium science experiment", "Kurzgesagt in a nutshell", "Physics Wallah lecture",
            "Mohak Mangal documentary", "Khan Academy math science"
        )
        "movies" -> listOf(
            "Official Movie Trailer 2026", "New Hindi Movie Teaser 2026", "Marvel Studios Official Trailer",
            "Behind The Scenes Movie Making", "Top Action Movie Scenes"
        )
        "news" -> listOf(
            "Aaj Tak Live News Today", "World News Headlines 2026", "NDTV India News Live",
            "BBC News World Update", "Financial Markets News 2026"
        )
        "sports" -> listOf(
            "India vs Australia Cricket Highlights", "Real Madrid vs Barcelona El Clasico Goals",
            "T20 World Cup Match Highlights", "Virat Kohli 100 Run Knock Highlights", "Formula 1 Grand Prix Highlights"
        )
        "for you" -> {
            val subChannels = com.adzero.app.data.SubscriptionManager.subscribedChannels.keys.toList()
            if (subChannels.isNotEmpty()) {
                listOf("${subChannels.random()} videos", "${subChannels.last()} latest")
            } else listOf("Trending India 2026", "Top Recommended Videos")
        }
        else -> listOf(
            "Trending India 2026", "New Music Videos 2026", "Tech Reviews 2026",
            "Gaming Highlights 2026", "Standup Comedy Special", "Top Podcasts 2026"
        )
    }
}
