package com.adzero.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adzero.app.data.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onUpdateClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    if (!updateInfo.hasUpdate && !updateInfo.isDownloading && !updateInfo.isDownloaded) return

    // Accent colors — Google-style teal/blue
    val accentColor = Color(0xFF1A73E8)      // Google Blue
    val surfaceDark = Color(0xFF1C1B1F)
    val surfaceCard = Color(0xFF2B2930)
    val subtleText = Color(0xFF9E9E9E)
    val whiteText = Color(0xFFF1F1F1)

    // Pulsing glow animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "glowAlpha"
    )

    Dialog(
        onDismissRequest = { if (!updateInfo.isDownloading) onDismissClick() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Hero Icon with animated glow ring ──
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .drawBehind {
                            // Outer glow ring
                            drawCircle(
                                color = accentColor.copy(alpha = glowAlpha),
                                radius = size.minDimension / 2 + 8.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = accentColor.copy(alpha = glowAlpha * 0.4f),
                                radius = size.minDimension / 2 + 16.dp.toPx(),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        accentColor,
                                        Color(0xFF4285F4) // Lighter Google Blue
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(Float.MAX_VALUE, Float.MAX_VALUE)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (updateInfo.isDownloaded) Icons.Outlined.Verified
                                          else Icons.Outlined.NewReleases,
                            contentDescription = "Update",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Title ──
                Text(
                    text = if (updateInfo.isDownloaded) "Ready to Install"
                           else if (updateInfo.isDownloading) "Downloading Update..."
                           else "Update Available",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = whiteText,
                    letterSpacing = (-0.3).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ── Subtitle with version ──
                Text(
                    text = "AdZero ${updateInfo.versionName} is available",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = subtleText
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Download Progress Section ──
                if (updateInfo.isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Circular progress with percentage
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { updateInfo.downloadProgress },
                                modifier = Modifier.size(72.dp),
                                color = accentColor,
                                trackColor = surfaceCard,
                                strokeWidth = 5.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                text = "${(updateInfo.downloadProgress * 100).toInt()}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = whiteText
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Please don't close the app",
                            fontSize = 12.sp,
                            color = subtleText,
                            fontWeight = FontWeight.Normal
                        )
                    }
                } else {
                    // ── Release Notes Card ──
                    if (updateInfo.changelog.isNotBlank()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "What's new",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(surfaceCard)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = updateInfo.changelog,
                                    fontSize = 13.sp,
                                    color = Color(0xFFCAC4D0),
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Action Buttons ──
                if (!updateInfo.isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Primary CTA — full width, prominent
                        Button(
                            onClick = onUpdateClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 0.dp
                            )
                        ) {
                            Icon(
                                imageVector = if (updateInfo.isDownloaded) Icons.Outlined.Verified
                                              else Icons.Outlined.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (updateInfo.isDownloaded) "Install Now" else "Download & Install",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                letterSpacing = 0.sp
                            )
                        }

                        // Secondary — text-only dismiss
                        TextButton(
                            onClick = onDismissClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Not now",
                                color = subtleText,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
