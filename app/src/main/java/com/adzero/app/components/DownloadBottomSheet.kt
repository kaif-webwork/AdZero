package com.adzero.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adzero.app.data.AppDownloadManager
import com.adzero.app.models.VideoStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadBottomSheet(
    videoTitle: String,
    videoStreams: List<VideoStream>,
    audioStreams: List<VideoStream>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Video MP4, 1: Audio MP3, 2: Clips

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF2661).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        tint = Color(0xFFFF2661),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download Media",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = videoTitle,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Segmented Tab Selector (Video MP4 | Audio MP3 | Clips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabPill(
                    icon = Icons.Default.Movie,
                    label = "MP4 Video",
                    isSelected = selectedTab == 0,
                    modifier = Modifier.weight(1f)
                ) { selectedTab = 0 }

                TabPill(
                    icon = Icons.Default.MusicNote,
                    label = "MP3 Audio",
                    isSelected = selectedTab == 1,
                    modifier = Modifier.weight(1f)
                ) { selectedTab = 1 }

                TabPill(
                    icon = Icons.Default.ContentCut,
                    label = "Clips",
                    isSelected = selectedTab == 2,
                    modifier = Modifier.weight(1f)
                ) { selectedTab = 2 }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // MP4 Video Qualities
                    val displayVideoStreams = videoStreams.filter { it.url.isNotBlank() }
                    if (displayVideoStreams.isEmpty()) {
                        EmptyDownloadState("No video download streams available")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 320.dp)
                        ) {
                            items(displayVideoStreams) { stream ->
                                DownloadOptionCard(
                                    title = "${stream.quality} MP4 Video",
                                    subtitle = if (stream.isVideoOnly) "High Quality DASH Video Stream" else "Full Video + Audio Stream",
                                    badge = if (stream.quality.contains("1080") || stream.quality.contains("2160") || stream.quality.contains("4k", ignoreCase = true)) "HD / 4K" else "SD",
                                    icon = Icons.Default.Movie
                                ) {
                                    AppDownloadManager.downloadStream(
                                        context = context,
                                        videoTitle = videoTitle,
                                        url = stream.url,
                                        qualityOrTypeLabel = stream.quality,
                                        isAudioOnly = false,
                                        extension = "mp4"
                                    )
                                    onDismiss()
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // MP3 / M4A Audio Options
                    val displayAudioStreams = audioStreams.filter { it.url.isNotBlank() }
                    if (displayAudioStreams.isEmpty()) {
                        EmptyDownloadState("No audio download streams available")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 320.dp)
                        ) {
                            items(displayAudioStreams) { stream ->
                                val label = if (stream.displayName.isNotBlank()) stream.displayName else stream.quality
                                val ext = if (stream.format.contains("mp3") || stream.format.contains("m4a")) "mp3" else "m4a"
                                DownloadOptionCard(
                                    title = "$label Audio",
                                    subtitle = "High Quality Music Track ($ext)",
                                    badge = "MP3 / Audio",
                                    icon = Icons.Default.AudioFile
                                ) {
                                    AppDownloadManager.downloadStream(
                                        context = context,
                                        videoTitle = videoTitle,
                                        url = stream.url,
                                        qualityOrTypeLabel = "Audio_$label",
                                        isAudioOnly = true,
                                        extension = ext
                                    )
                                    onDismiss()
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Clips Download Options
                    val clipVideoStreams = videoStreams.filter { it.url.isNotBlank() }
                    val defaultStream = clipVideoStreams.firstOrNull { it.quality.contains("720") || it.quality.contains("1080") } ?: clipVideoStreams.firstOrNull()

                    if (defaultStream == null) {
                        EmptyDownloadState("No video streams available for clips")
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "Select Short Clip Preference:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            DownloadOptionCard(
                                title = "15s Short Status Clip",
                                subtitle = "Download 15s HD video clip for Stories & Status",
                                badge = "15 Sec",
                                icon = Icons.Default.ContentCut
                            ) {
                                AppDownloadManager.downloadStream(
                                    context = context,
                                    videoTitle = videoTitle,
                                    url = defaultStream.url,
                                    qualityOrTypeLabel = "15s_Clip_${defaultStream.quality}",
                                    isAudioOnly = false,
                                    extension = "mp4"
                                )
                                onDismiss()
                            }

                            DownloadOptionCard(
                                title = "30s Highlight Clip",
                                subtitle = "Download 30s HD video clip",
                                badge = "30 Sec",
                                icon = Icons.Default.ContentCut
                            ) {
                                AppDownloadManager.downloadStream(
                                    context = context,
                                    videoTitle = videoTitle,
                                    url = defaultStream.url,
                                    qualityOrTypeLabel = "30s_Clip_${defaultStream.quality}",
                                    isAudioOnly = false,
                                    extension = "mp4"
                                )
                                onDismiss()
                            }

                            DownloadOptionCard(
                                title = "60s Short Reel Clip",
                                subtitle = "Download 60s HD video clip for Reels & Shorts",
                                badge = "60 Sec",
                                icon = Icons.Default.ContentCut
                            ) {
                                AppDownloadManager.downloadStream(
                                    context = context,
                                    videoTitle = videoTitle,
                                    url = defaultStream.url,
                                    qualityOrTypeLabel = "60s_Clip_${defaultStream.quality}",
                                    isAudioOnly = false,
                                    extension = "mp4"
                                )
                                onDismiss()
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TabPill(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFFFF2661) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DownloadOptionCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFF2661).copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF2661)
            )
        }

        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Download",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun EmptyDownloadState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp
        )
    }
}
