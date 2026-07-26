package com.adzero.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlin.OptIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.adzero.app.Constants
import com.adzero.app.models.Video
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun YouTubeLoading(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(com.adzero.app.R.raw.loading_pulse))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(48.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────
// YouTube 2026 Category Chips (Dynamic Light/Dark Theme Adaption)
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun CategoryChips(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Constants.CATEGORIES.forEach { category ->
            val isSelected = category == selectedCategory
            val chipBg by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onBackground
                              else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(200),
                label = "chipBg"
            )
            val chipTextColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.background
                              else MaterialTheme.colorScheme.onBackground,
                animationSpec = tween(200),
                label = "chipText"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(chipBg)
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        RoundedCornerShape(50)
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = category,
                    color = chipTextColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// YouTube 2026 Video Card (Dynamic Theme Card Background & High-Contrast Text)
// ─────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    onMenuClick: () -> Unit = {},
    onChannelClick: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ),
                RoundedCornerShape(22.dp)
            )
            .clickable { onClick() }
    ) {
        // Thumbnail with rounded top corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .graphicsLayer { clip = true }
        ) {
            val context = LocalContext.current
            val thumbModel = remember(video.id, video.thumbnailUrl) {
                val primaryUrl = if (video.thumbnailUrl.startsWith("//")) "https:${video.thumbnailUrl}" else video.thumbnailUrl
                coil.request.ImageRequest.Builder(context)
                    .data(primaryUrl)
                    .crossfade(true)
                    .build()
            }

            AsyncImage(
                model = thumbModel,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Duration or Live Badge
            if (video.duration.isNotBlank()) {
                val isRealLiveCard = video.isLive || video.duration == "LIVE"
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRealLiveCard) Color.Red else Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    if (isRealLiveCard) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                            Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Details Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 0.dp, top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel Avatar
            AsyncImage(
                model = video.channelAvatarUrl,
                contentDescription = video.channelName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onChannelClick(video.channelName) },
                contentScale = ContentScale.Crop
            )

            // Title + Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.channelName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onChannelClick(video.channelName) }
                    )
                    if (video.isVerified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            modifier = Modifier
                                .padding(start = 3.dp)
                                .size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val meta = buildString {
                        if (video.views.isNotBlank()) append(" • ${video.views}")
                        if (video.uploadDate.isNotBlank()) append(" • ${video.uploadDate}")
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 3-dot menu button
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // 3-dot Bottom Sheet Menu
    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(120.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = video.channelName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            VideoMenuOption(icon = Icons.Outlined.WatchLater, label = "Save to Watch later") { showMenu = false }
            VideoMenuOption(icon = Icons.Outlined.PlaylistAdd, label = "Save to playlist") { showMenu = false }
            VideoMenuOption(icon = Icons.Outlined.FileDownload, label = "Download video") { showMenu = false }
            VideoMenuOption(icon = Icons.Outlined.Share, label = "Share") { showMenu = false }
            VideoMenuOption(icon = Icons.Outlined.Block, label = "Not interested") { showMenu = false }
            VideoMenuOption(icon = Icons.Outlined.RemoveCircleOutline, label = "Don't recommend channel") { showMenu = false }
            VideoMenuOption(icon = Icons.Outlined.Flag, label = "Report") { showMenu = false }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun VideoMenuOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Shorts Shelf — YouTube Home feed Shorts row
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun ShortsShelf(
    shorts: List<Video>,
    onShortClick: (Video) -> Unit,
    onSeeAll: () -> Unit = {}
) {
    if (shorts.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Red),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Shorts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(shorts, key = { "short_${it.id}" }) { short ->
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(240.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onShortClick(short) }
                ) {
                    AsyncImage(
                        model = short.thumbnailUrl,
                        contentDescription = short.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                    startY = 200f
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = short.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (short.views.isNotBlank()) {
                            Text(
                                text = short.views,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Skeleton Loader for initial feed loading
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun SkeletonLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            )
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
            }
        }
    }
}
