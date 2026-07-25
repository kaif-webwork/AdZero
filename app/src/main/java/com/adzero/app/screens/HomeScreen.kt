package com.adzero.app.screens

import androidx.compose.animation.AnimatedVisibility
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    fun refreshHomeFeed() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val service = ServiceList.YouTube
                    val randomTopics = listOf(
                        "Trending India 2026", "New Music Videos", "Tech Reviews", 
                        "Gaming Highlights 2026", "Standup Comedy Special", "Top Podcasts India",
                        "Cricket Highlights", "Unboxing Gadgets", "Lofi Beats 24/7",
                        "Movie Trailers 2026", "Latest News Headlines", "Car Reviews"
                    )
                    val topic1 = randomTopics.random()
                    val topic2 = (randomTopics - topic1).random()
                    
                    val handler1 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic1, emptyList(), "")
                    val handler2 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic2, emptyList(), "")
                    
                    val res1 = try { SearchInfo.getInfo(service, handler1).relatedItems } catch(e: Exception) { emptyList() }
                    val res2 = try { SearchInfo.getInfo(service, handler2).relatedItems } catch(e: Exception) { emptyList() }
                    
                    val combined = (res1 + res2).filterIsInstance<StreamInfoItem>().map { it.toVideo() }.shuffled()
                    val freshVideos = if (combined.isNotEmpty()) combined else createFallbackCategoryVideos(selectedCategory).shuffled()
                    
                    withContext(Dispatchers.Main) {
                        videosState = freshVideos
                        isRefreshing = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        videosState = createFallbackCategoryVideos(selectedCategory).shuffled()
                        isRefreshing = false
                    }
                }
            }
        }
    }

    fun loadMoreVideos() {
        if (isMoreLoading) return
        isMoreLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val randomTopics = listOf(
                    "Trending India", "New Music Videos", "Tech Reviews", "Gaming 2026", 
                    "Standup Comedy", "Podcasts", "Cricket", "Gadgets", "Lofi Music", "World News"
                )
                val topic = if (selectedCategory == "All") randomTopics.random() else "$selectedCategory trending"
                val handler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic, emptyList(), "")
                val res = try { SearchInfo.getInfo(service, handler).relatedItems } catch(e: Exception) { emptyList() }
                val fetched = res.filterIsInstance<StreamInfoItem>().map { it.toVideo() }.shuffled()
                val newItems = if (fetched.isNotEmpty()) fetched else createFallbackCategoryVideos(selectedCategory).shuffled()

                withContext(Dispatchers.Main) {
                    val existingIds = videosState.map { it.id }.toSet()
                    val uniqueNew = newItems.filter { it.id !in existingIds }
                    videosState = videosState + if (uniqueNew.isNotEmpty()) uniqueNew else newItems
                    isMoreLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    videosState = videosState + createFallbackCategoryVideos(selectedCategory).shuffled()
                    isMoreLoading = false
                }
            }
        }
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && !isMoreLoading && videosState.isNotEmpty()) {
            loadMoreVideos()
        }
    }

    LaunchedEffect(selectedCategory) {
        val fastCached = com.adzero.app.data.FastContentStore.getFeed(context, selectedCategory)
        val warmCached = com.adzero.app.data.WarmFeedCache.getFeed(selectedCategory)
        val initialFeed = if (fastCached.isNotEmpty()) fastCached else warmCached
        if (!initialFeed.isNullOrEmpty()) {
            videosState = initialFeed
            isLoading = false
        } else {
            isLoading = true
        }

        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val items = if (selectedCategory == "For You 👤") {
                    val subChannels = com.adzero.app.data.SubscriptionManager.subscribedChannels.keys.toList()
                    val topic1 = if (subChannels.isNotEmpty()) "${subChannels.random()} videos" else "MicroG recommended videos"
                    val topic2 = if (subChannels.size > 1) "${(subChannels - topic1).random()} latest" else "Trending top videos 2026"

                    val handler1 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic1, emptyList(), "")
                    val handler2 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic2, emptyList(), "")

                    val res1 = try { SearchInfo.getInfo(service, handler1).relatedItems } catch (e: Exception) { emptyList() }
                    val res2 = try { SearchInfo.getInfo(service, handler2).relatedItems } catch (e: Exception) { emptyList() }

                    (res1 + res2).filterIsInstance<StreamInfoItem>().map { it.toVideo() }.shuffled()
                } else if (selectedCategory == "All") {
                    val randomTopics = listOf(
                        "Trending India", "New Music Videos 2026", "Tech Reviews", 
                        "Gaming Highlights 2026", "Standup Comedy Special", "Top Podcasts India",
                        "Cricket Highlights 2026", "Unboxing Gadgets", "Lofi Beats",
                        "Movie Trailers 2026", "Latest News Headlines", "Car Reviews 2026"
                    )
                    val topic1 = randomTopics.random()
                    val topic2 = (randomTopics - topic1).random()
                    
                    val handler1 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic1, emptyList(), "")
                    val handler2 = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(topic2, emptyList(), "")
                    
                    val res1 = try { SearchInfo.getInfo(service, handler1).relatedItems } catch(e: Exception) { emptyList() }
                    val res2 = try { SearchInfo.getInfo(service, handler2).relatedItems } catch(e: Exception) { emptyList() }
                    
                    val combined = (res1 + res2).shuffled()
                    if (combined.isEmpty()) {
                        val extractor = service.kioskList.defaultKioskExtractor
                        KioskInfo.getInfo(extractor).relatedItems.shuffled()
                    } else {
                        combined
                    }
                } else {
                    val queryTerm = if (selectedCategory.equals("Live", ignoreCase = true)) "live stream" else selectedCategory
                    val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                        .fromQuery(queryTerm, emptyList(), "")
                    SearchInfo.getInfo(service, queryHandler).relatedItems.shuffled()
                }

                val fetchedVideos = items.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
                val filteredVideos = if (selectedCategory.equals("Live", ignoreCase = true)) {
                    fetchedVideos.filter { it.isLive || it.duration == "LIVE" }
                } else {
                    fetchedVideos
                }
                val videos = if (filteredVideos.isEmpty()) createFallbackCategoryVideos(selectedCategory) else filteredVideos

                val shortsQueryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery("#shorts trending", emptyList(), "")
                val shortsSearchInfo = SearchInfo.getInfo(service, shortsQueryHandler)
                val fetchedShorts = shortsSearchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo() }
                    .take(8)
                val shorts = if (fetchedShorts.isEmpty()) createFallbackCategoryVideos("shorts").take(6) else fetchedShorts

                withContext(Dispatchers.Main) {
                    videosState = videos
                    shortsState = shorts
                    com.adzero.app.data.FastContentStore.saveFeed(context, selectedCategory, videos)
                    videos.take(10).forEach { video ->
                        ExtractionManager.startExtraction(video, isSpeculative = true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    videosState = createFallbackCategoryVideos(selectedCategory)
                    shortsState = createFallbackCategoryVideos("shorts").take(6)
                }
            }
        }
        isLoading = false
    }

    Scaffold(
        containerColor = Color(0xFF000000),
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
                    // Cast icon
                    IconButton(onClick = {}) {
                        Icon(imageVector = Icons.Default.Cast, contentDescription = "Cast",
                            modifier = Modifier.size(23.dp))
                    }
                    // Notifications with red badge
                    IconButton(onClick = {}) {
                        Box {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications",
                                modifier = Modifier.size(24.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                        }
                    }
                    // Search
                    IconButton(onClick = onSearchClick) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search",
                            modifier = Modifier.size(24.dp))
                    }
                    // Profile avatar
                    IconButton(onClick = onProfileClick) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
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

                if (isLoading && videosState.isEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(5) { SkeletonLoader() }
                    }
                } else {
                    if (videosState.isEmpty() && !isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            YouTubeLoading()
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            itemsIndexed(
                                items = videosState,
                                key = { index, video -> "${video.id}_$index" }
                            ) { index, video ->
                                VideoCard(
                                    video = video,
                                    onClick = {
                                        ExtractionManager.startExtraction(video)
                                        onVideoClick(video)
                                    },
                                    onChannelClick = onChannelClick
                                )

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
}

fun createFallbackCategoryVideos(category: String): List<Video> {
    val items = when (category.lowercase()) {
        "gaming" -> listOf(
            Triple("GTA 6 Official Gameplay Reveal & Secret Details", "Rockstar Games", "gta6_reveal"),
            Triple("Minecraft 1.21 Update Showcase - Everything New!", "MumboJumbo", "mc_showcase"),
            Triple("Elden Ring Shadow of the Erdtree Boss Guide", "VaatiVidya", "elden_guide"),
            Triple("BGMI 3.2 Update New Features & Gameplay", "Mortal", "bgmi_gameplay"),
            Triple("VALORANT Champions 2026 Grand Finals", "VALORANT Champions", "valorant_finals")
        )
        "music" -> listOf(
            Triple("Lo-fi Beats to Relax / Study to 24/7", "Lofi Girl", "lofi_study"),
            Triple("Arijit Singh Best Romantic Hits Live 2026", "T-Series", "arijit_romantic"),
            Triple("Taylor Swift - The Eras Tour Live Performance", "Taylor Swift", "taylor_eras_live"),
            Triple("Coke Studio Season 15 Full Episode 1", "Coke Studio", "coke_studio_s15"),
            Triple("Coldplay - Yellow (Live in Tokyo 2026)", "Coldplay", "coldplay_yellow")
        )
        "live" -> listOf(
            Triple("🔴 ISRO Gaganyaan Mission Launch Live Streaming", "ISRO Official", "isro_gaganyaan"),
            Triple("🔴 Aaj Tak Live News 24x7 Stream", "Aaj Tak", "aajtak_news_live"),
            Triple("🔴 Lofi Hip Hop Radio - Beats to Sleep to", "Lofi Girl", "lofi_sleep_radio"),
            Triple("🔴 Techno Gamerz GTA V Live Gameplay", "Techno Gamerz", "techno_gta5_live"),
            Triple("🔴 NASA Earth From Space Live HD Stream", "NASA", "nasa_earth_live")
        )
        "podcasts" -> listOf(
            Triple("The Ranveer Show #350 - AI Future & Tech Breakthroughs", "BeerBiceps", "beerbiceps_350"),
            Triple("Joe Rogan Experience #2150 - Quantum Physics & Cosmos", "PowerfulJRE", "jre_2150"),
            Triple("Raj Shamani Figuring Out Ep 120 - Building Startups", "Raj Shamani", "raj_startups"),
            Triple("Lex Fridman Podcast #420 - Sam Altman on Future AI", "Lex Fridman", "lex_sam_altman"),
            Triple("Prakhar ke Pravachan - Human Psychology Explained", "Prakhar ke Pravachan", "prakhar_psych")
        )
        "technology" -> listOf(
            Triple("iPhone 16 Pro Max Unboxing & Real Review", "Marques Brownlee", "mkbhd_iphone16"),
            Triple("Building a $5000 Ultimate Gaming PC Setup 2026", "Linus Tech Tips", "ltt_5k_pc"),
            Triple("Android 15 Top 20 Secret Features You Didn't Know!", "Android Authority", "android15_secrets"),
            Triple("Tesla Cybercab Full Self Driving Test Drive", "MKBHD", "cybercab_mkbhd"),
            Triple("Google Gemini Pro 1.5 vs GPT-4o Full Comparison", "Fireship", "fireship_ai")
        )
        "sports" -> listOf(
            Triple("India vs Australia T20 World Cup Match Highlights", "Star Sports", "ind_aus_t20_hl"),
            Triple("Real Madrid vs Barcelona El Clasico Goals 2026", "LaLiga", "el_clasico_2026"),
            Triple("Virat Kohli Iconic 100 Run Knock Highlights", "BCCI", "kohli_100_hl")
        )
        "shorts" -> listOf(
            Triple("How To Actually Make Viral Shorts", "Luc Boulch", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
            Triple("3 Secret AI Tools You Must Try Today!", "Tech Burner", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"),
            Triple("BGMI Clutch 1v4 Insane Gameplay Moment!", "Mortal", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"),
            Triple("Top 5 Unbelievable Facts About Space 🚀", "GetsetflySCIENCE", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"),
            Triple("Crazy Standup Comedy Joke 😂", "Samay Raina", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdown.mp4"),
            Triple("Mind-blowing Magic Trick Exposed! ✨", "Sujan Zaveri", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4")
        )
        else -> listOf(
            Triple("Top 10 Mind-Blowing Discoveries of 2026", "Veritasium", "veritasium_discoveries"),
            Triple("How India Built The World's Fastest Highway Network", "Mohak Mangal", "mohak_highways"),
            Triple("Samay Raina Unfiltered Standup Comedy Special", "Samay Raina", "samay_special"),
            Triple("24 Hours Surviving in a Secret Underground Bunker", "MrBeast", "mrbeast_bunker_24h"),
            Triple("How Quantum Computers Will Change The World Forever", "Kurzgesagt", "kurzgesagt_quantum")
        )
    }

    val isLiveCategory = category.equals("live", ignoreCase = true)

    return items.mapIndexed { index, item ->
        Video(
            id = "fallback_${category.lowercase()}_$index",
            title = item.first,
            videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            thumbnailUrl = "https://picsum.photos/seed/${category.lowercase()}$index/640/360",
            channelName = item.second,
            channelAvatarUrl = "https://picsum.photos/seed/avatar_${item.third}/100/100",
            duration = if (isLiveCategory) "LIVE" else "${(4..18).random()}:${(10..59).random()}",
            views = "${(40..980).random()}K views",
            uploadDate = if (isLiveCategory) "Started streaming" else "${(1..5).random()} days ago",
            description = "Explore the best of ${item.first} on YouTube 2026.",
            isLive = isLiveCategory
        )
    }
}
