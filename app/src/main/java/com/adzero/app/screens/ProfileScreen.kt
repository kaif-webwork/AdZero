package com.adzero.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.adzero.app.data.HistoryManager
import com.adzero.app.models.Video
import com.adzero.app.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onVideoClick: (Video) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    var showEmailDialog by remember { mutableStateOf(false) }
    var userEmailInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "You",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ── MicroG Google Account Section ──────────────────────────────────────────
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                val isMicroGInstalled = remember { com.adzero.app.data.MicroGManager.isMicroGInstalled(context) }

                LaunchedEffect(Unit) {
                    com.adzero.app.data.MicroGManager.autoDetectAndConnectMicroGAccount(context)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (com.adzero.app.data.MicroGManager.isLoggedIn && com.adzero.app.data.MicroGManager.avatarUrl != null) {
                                AsyncImage(
                                    model = com.adzero.app.data.MicroGManager.avatarUrl,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.size(52.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4285F4)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = com.adzero.app.data.MicroGManager.accountName.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = com.adzero.app.data.MicroGManager.accountName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = com.adzero.app.data.MicroGManager.accountEmail,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // MicroG installation status badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1B5E20).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "MicroG Connected ✓",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Your Google/YouTube account is automatically synced via MicroG (ReVanced Session Active).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        val coroutineScope = rememberCoroutineScope()
                        val accountPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                                val selectedEmail = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
                                if (!selectedEmail.isNullOrBlank()) {
                                    val name = selectedEmail.substringBefore("@").replace(".", " ").capitalize()
                                    com.adzero.app.data.MicroGManager.saveAccount(context, name, selectedEmail)
                                    coroutineScope.launch {
                                        com.adzero.app.data.RealAccountSyncManager.syncRealYouTubeAccount(context, selectedEmail.substringBefore("@"))
                                        android.widget.Toast.makeText(context, "Connected to $selectedEmail! Live data synced.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = com.adzero.app.data.MicroGManager.createAccountPickerIntent()
                                        accountPickerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        showEmailDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                            ) {
                                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select MicroG Account", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showEmailDialog = true },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                            }

                            OutlinedButton(
                                onClick = { com.adzero.app.data.MicroGManager.launchMicroGAccountSetup(context) },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                            }
                        }

                        if (showEmailDialog) {
                            AlertDialog(
                                onDismissRequest = { showEmailDialog = false },
                                title = { Text("Link Google Account Email", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Enter your Google Account email added in MicroG to sync your exact feed & playlists:", fontSize = 13.sp)
                                        OutlinedTextField(
                                            value = userEmailInput,
                                            onValueChange = { userEmailInput = it },
                                            label = { Text("Google Account Email") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (userEmailInput.isNotBlank()) {
                                                val name = userEmailInput.substringBefore("@").replace(".", " ").capitalize()
                                                com.adzero.app.data.MicroGManager.saveAccount(context, name, userEmailInput)
                                                coroutineScope.launch {
                                                    com.adzero.app.data.RealAccountSyncManager.syncRealYouTubeAccount(context, userEmailInput.substringBefore("@"))
                                                }
                                            }
                                            showEmailDialog = false
                                        }
                                    ) {
                                        Text("Save & Sync")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showEmailDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Quick Access Row (History, Watch Later, Playlists) ──────────
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        YouQuickAccessCard(
                            icon = Icons.Default.History,
                            label = "History",
                            thumbnail = HistoryManager.watchHistory.firstOrNull()?.thumbnailUrl,
                            onClick = {}
                        )
                    }
                    item {
                        YouQuickAccessCard(
                            icon = Icons.Outlined.WatchLater,
                            label = "Watch later",
                            thumbnail = null,
                            onClick = {}
                        )
                    }
                    item {
                        YouQuickAccessCard(
                            icon = Icons.Default.VideoLibrary,
                            label = "Liked videos",
                            thumbnail = null,
                            onClick = {}
                        )
                    }
                    item {
                        YouQuickAccessCard(
                            icon = Icons.Default.PlaylistPlay,
                            label = "Playlists",
                            thumbnail = null,
                            onClick = {}
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }

            // ── Watch History Section ─────────────────────────────────────
            if (HistoryManager.watchHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Watch history", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground)
                        TextButton(onClick = {}) {
                            Text("View all", fontSize = 13.sp)
                        }
                    }
                }
                items(HistoryManager.watchHistory.take(5)) { video ->
                    YouHistoryVideoRow(video = video, onClick = { onVideoClick(video) })
                }
            }

            // ── Connected YouTube Playlists Section ──────────────────────────
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playlists", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground)
                    TextButton(onClick = {}) {
                        Text("View all", fontSize = 13.sp)
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(com.adzero.app.data.PlaylistManager.userPlaylists) { playlist ->
                        Column(
                            modifier = Modifier
                                .width(150.dp)
                                .clickable { }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = playlist.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Text("${playlist.itemCount} videos", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = playlist.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (playlist.isPrivate) "🔒 Private" else "Public",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ── Settings & More ───────────────────────────────────────────
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                YouSettingsRow(icon = Icons.Outlined.Settings, label = "Settings", onClick = onSettingsClick)
                YouSettingsRow(icon = Icons.Default.Shield, label = "Ad-Free Mode (Active 🛡️)", onClick = {})
                YouSettingsRow(icon = Icons.Default.Help, label = "Help & feedback", onClick = {})
                YouSettingsRow(icon = Icons.Outlined.Info, label = "About AdZero", onClick = {})
            }

            // ── Theme Switcher ────────────────────────────────────────────
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Appearance", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.AMOLED, ThemeMode.LIGHT).forEach { mode ->
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> "Device"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.AMOLED -> "AMOLED"
                                ThemeMode.LIGHT -> "Light"
                            }
                            val isSelected = currentTheme == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onThemeChange(mode) },
                                label = { Text(label, fontSize = 13.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YouQuickAccessCard(
    icon: ImageVector,
    label: String,
    thumbnail: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            }
            Icon(imageVector = icon, contentDescription = label,
                tint = if (thumbnail != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun YouHistoryVideoRow(video: Video, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(120.dp).height(68.dp)) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            if (video.duration.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(video.duration, color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 17.sp
            )
            Text(
                text = video.channelName,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun YouSettingsRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
    }
}
