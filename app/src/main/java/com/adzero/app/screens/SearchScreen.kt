package com.adzero.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adzero.app.components.VideoCard
import com.adzero.app.components.VoiceSearchSheet
import com.adzero.app.data.ExtractionManager
import com.adzero.app.data.HistoryManager
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
fun SearchScreen(
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var searchResultsState by remember { mutableStateOf<List<Video>>(emptyList()) }
    var liveSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var realTrendingSearches by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var searchNextPage by remember { mutableStateOf<org.schabi.newpipe.extractor.Page?>(null) }
    var isMoreLoading by remember { mutableStateOf(false) }

    // State for Voice Search Modal Sheet (Speech-to-Text UI)
    var showVoiceSearchSheet by remember { mutableStateOf(false) }

    val searchListState = rememberLazyListState()

    // Fetch REAL Live YouTube Trending Searches from YouTube's Trending Kiosk
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val trendingKiosk = KioskInfo.getInfo(service, "https://www.youtube.com/feed/trending")
                val trendingTopics = trendingKiosk.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { item ->
                        item.name?.takeIf { it.isNotBlank() }
                    }
                    .distinct()
                    .take(8)

                withContext(Dispatchers.Main) {
                    if (trendingTopics.isNotEmpty()) {
                        realTrendingSearches = trendingTopics
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Debounced Live Search Suggestions
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank() || showResults) {
            liveSuggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300) // 300ms debounce
        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery(searchQuery, emptyList(), "")
                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                val suggestions = searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { it.name }
                    .distinct()
                    .take(5)
                withContext(Dispatchers.Main) {
                    liveSuggestions = suggestions
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Fetch initial search results when user triggers search
    LaunchedEffect(showResults, searchQuery) {
        if (showResults && searchQuery.isNotBlank()) {
            isSearching = true
            HistoryManager.addSearchQuery(context, searchQuery.trim())
            withContext(Dispatchers.IO) {
                try {
                    val service = ServiceList.YouTube
                    val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                        .fromQuery(searchQuery, emptyList(), "")
                    val searchInfo = SearchInfo.getInfo(service, queryHandler)
                    val videos = searchInfo.relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toVideo() }
                    val nextPage = searchInfo.nextPage

                    withContext(Dispatchers.Main) {
                        searchResultsState = videos
                        searchNextPage = nextPage
                        isSearching = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isSearching = false
                    }
                }
            }
        }
    }

    fun loadMoreSearchResults() {
        if (isMoreLoading || searchNextPage == null || searchQuery.isBlank()) return
        isMoreLoading = true
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val service = ServiceList.YouTube
                val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery(searchQuery, emptyList(), "")
                val moreInfo = SearchInfo.getMoreItems(service, queryHandler, searchNextPage)
                val newVideos = moreInfo.items
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo() }
                val nextPage = moreInfo.nextPage

                withContext(Dispatchers.Main) {
                    val currentIds = searchResultsState.map { it.id }.toSet()
                    val filteredNew = newVideos.filterNot { currentIds.contains(it.id) }
                    searchResultsState = searchResultsState + filteredNew
                    searchNextPage = nextPage
                    isMoreLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isMoreLoading = false }
            }
        }
    }

    val shouldLoadMoreSearch = remember {
        derivedStateOf {
            val totalItems = searchListState.layoutInfo.totalItemsCount
            val lastVisibleItem = searchListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMoreSearch.value) {
        if (shouldLoadMoreSearch.value && showResults && !isSearching && !isMoreLoading && searchResultsState.isNotEmpty()) {
            loadMoreSearchResults()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Search input box
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search YouTube...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 15.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        showResults = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (searchQuery.isNotBlank()) showResults = true
                                    }),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = ""; showResults = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Official YouTube-Style Voice Search Mic Button (Speech-to-Text UI)
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { showVoiceSearchSheet = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (showResults) {
                val results = searchResultsState

                if (results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found for \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(results, key = { it.id }, contentType = { "video_card" }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { 
                                    ExtractionManager.startExtraction(video)
                                    onVideoClick(video) 
                                },
                                onChannelClick = onChannelClick
                            )
                        }

                        if (isMoreLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.adzero.app.components.YouTubeLoading()
                                }
                            }
                        }
                    }
                }
            } else {
                // Show History and Suggestions lists
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Live Debounced Suggestions
                    if (liveSuggestions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Live Suggestions",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        items(liveSuggestions) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = suggestion
                                        showResults = true
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.TrendingUp, contentDescription = "Suggestion", tint = MaterialTheme.colorScheme.primary)
                                Text(text = suggestion, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }

                    // Real User Recent Searches
                    if (HistoryManager.searchHistory.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                TextButton(onClick = { HistoryManager.clearSearchHistory(context) }) {
                                    Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        items(HistoryManager.searchHistory) { historyItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = historyItem
                                        showResults = true
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.History, contentDescription = "History", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = historyItem,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { HistoryManager.removeSearchQuery(context, historyItem) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Remove search item",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // REAL Live YouTube Trending Searches Section
                    val trendingList = realTrendingSearches
                    if (trendingList.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Trending Searches 🔥",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(trendingList) { trendingItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = trendingItem
                                        showResults = true
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.TrendingUp, contentDescription = "Trending", tint = Color(0xFFFF0055))
                                Text(
                                    text = trendingItem,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for Voice Search (Speech-to-Text UI)
    if (showVoiceSearchSheet) {
        VoiceSearchSheet(
            onDismiss = { showVoiceSearchSheet = false },
            onSearchResult = { spokenText ->
                searchQuery = spokenText
                showResults = true
            }
        )
    }
}
