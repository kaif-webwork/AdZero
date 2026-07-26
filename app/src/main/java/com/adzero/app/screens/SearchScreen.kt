package com.adzero.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adzero.app.Constants
import com.adzero.app.components.VideoCard
import com.adzero.app.data.ExtractionManager
import com.adzero.app.data.HistoryManager
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
fun SearchScreen(
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showResults by remember { mutableStateOf(false) }
    var searchResultsState by remember { mutableStateOf<List<Video>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isMoreLoading by remember { mutableStateOf(false) }
    var searchNextPage by remember { mutableStateOf<org.schabi.newpipe.extractor.Page?>(null) }
    var liveSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val searchListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Working Voice Search Launcher via RecognizerIntent
    val speechLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val spokenResults = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenResults?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                searchQuery = spokenText
                showResults = true
            }
        }
    }

    fun launchVoiceSearch() {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to search YouTube...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Voice search not available on this device", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 300ms Debounced live search suggestions
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank() || showResults) {
            liveSuggestions = emptyList()
            return@LaunchedEffect
        }

        kotlinx.coroutines.delay(300) // 300ms debounce delay
        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery(searchQuery, emptyList(), "")
                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                val suggestions = searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.name }
                    .filter { it.isNotBlank() }
                    .take(7)

                withContext(Dispatchers.Main) {
                    liveSuggestions = suggestions
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(showResults, searchQuery) {
        if (showResults && searchQuery.isNotBlank()) {
            isSearching = true
            HistoryManager.addSearchQuery(searchQuery.trim())
            withContext(Dispatchers.IO) {
                try {
                    val service = ServiceList.YouTube
                    val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                        .fromQuery(searchQuery, emptyList(), "")
                    val searchInfo = SearchInfo.getInfo(service, queryHandler)
                    val items = searchInfo.relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toVideo() }
                    
                    withContext(Dispatchers.Main) {
                        searchResultsState = items
                        searchNextPage = searchInfo.nextPage
                        
                        items.take(3).forEach { video ->
                            ExtractionManager.startExtraction(video, isSpeculative = true)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isSearching = false
        }
    }

    fun loadMoreSearchResults() {
        if (isMoreLoading || searchNextPage == null || searchQuery.isBlank()) return
        isMoreLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val queryHandler = YoutubeSearchQueryHandlerFactory.getInstance()
                    .fromQuery(searchQuery, emptyList(), "")
                val moreInfo = SearchInfo.getMoreItems(service, queryHandler, searchNextPage)
                val existingIds = searchResultsState.map { it.id }.toSet()
                val newItems = moreInfo.items
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo() }
                    .filter { it.id !in existingIds }

                withContext(Dispatchers.Main) {
                    searchResultsState = searchResultsState + newItems
                    searchNextPage = moreInfo.nextPage
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }

                TextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        showResults = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    placeholder = { Text("Search YouTube...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        Row {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; showResults = false }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                            IconButton(onClick = { launchVoiceSearch() }) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice search", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (searchQuery.isNotBlank()) showResults = true
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
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
                        items(results, key = { it.id }) { video ->
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

                    // Recent Searches
                    if (HistoryManager.searchHistory.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recent Searches",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                    onClick = { HistoryManager.removeSearchQuery(historyItem) },
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

                    // Trending Section
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Trending Searches",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(Constants.TRENDING_SEARCHES) { trendingItem ->
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
                            Icon(imageVector = Icons.Default.TrendingUp, contentDescription = "Trending", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = trendingItem, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
