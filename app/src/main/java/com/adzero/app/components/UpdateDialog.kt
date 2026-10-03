package com.adzero.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SecurityUpdateGood
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adzero.app.data.UpdateInfo

/**
 * Apple Liquid Glass Update Dialog
 * Features frosted obsidian glassmorphism, animated specular liquid orb,
 * dynamic progress bar, and haptic interactive liquid controls.
 */
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onUpdateClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    if (!updateInfo.hasUpdate && !updateInfo.isDownloading && !updateInfo.isDownloaded) return

    val haptic = LocalHapticFeedback.current

    // ── Apple Liquid Glass Color Palette ─────────────────────────────────────
    val liquidPink = Color(0xFFFF2661)
    val liquidViolet = Color(0xFF8B5CF6)
    val liquidCyan = Color(0xFF06B6D4)
    val obsidianGlass = Color(0xF2101018) // Deep translucent obsidian frosted surface
    val subSurfaceGlass = Color(0x18FFFFFF) // Ultra-clean frosted sub-card
    val specularTop = Color.White.copy(alpha = 0.40f)
    val specularMid = Color.White.copy(alpha = 0.08f)
    val specularGlow = liquidPink.copy(alpha = 0.25f)
    val textPrimary = Color(0xFFF9FAFB)
    val textSecondary = Color(0xFF9CA3AF)

    // ── Fluid Animations ─────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "liquidAura")

    // Ambient liquid aura pulsation
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    Dialog(
        onDismissRequest = { if (!updateInfo.isDownloading) onDismissClick() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !updateInfo.isDownloading,
            dismissOnClickOutside = !updateInfo.isDownloading
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // ── Background Ambient Radial Liquid Aura ────────────────────────
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    liquidPink.copy(alpha = 0.22f * auraScale),
                                    liquidViolet.copy(alpha = 0.12f * auraScale),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height * 0.35f),
                                radius = size.width * 0.65f * auraScale
                            )
                        )
                    }
            )

            // ── Frosted Apple Liquid Glass Modal Container ───────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(obsidianGlass)
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.verticalGradient(
                                listOf(
                                    specularTop,
                                    specularMid,
                                    specularGlow
                                )
                            )
                        ),
                        RoundedCornerShape(32.dp)
                    )
                    .padding(26.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── 1. Floating Apple Liquid Glass Orb Header ────────────
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(86.dp)
                            .drawBehind {
                                // Outer specular glow ring
                                drawCircle(
                                    color = liquidPink.copy(alpha = 0.30f * auraScale),
                                    radius = (size.minDimension / 2f) + 8.dp.toPx(),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                                drawCircle(
                                    color = liquidViolet.copy(alpha = 0.15f * auraScale),
                                    radius = (size.minDimension / 2f) + 16.dp.toPx(),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                    ) {
                        // Liquid Orb Sphere
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            liquidPink,
                                            liquidViolet,
                                            Color(0xFF4C1D95)
                                        ),
                                        center = Offset(0.35f, 0.35f)
                                    )
                                )
                                .border(
                                    BorderStroke(
                                        1.5.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.60f),
                                                Color.White.copy(alpha = 0.10f)
                                            )
                                        )
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (updateInfo.isDownloaded) Icons.Outlined.SecurityUpdateGood
                                              else if (updateInfo.isDownloading) Icons.Outlined.FileDownload
                                              else Icons.Outlined.RocketLaunch,
                                contentDescription = "Update Icon",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // ── 2. Apple Mini Capsule Badge ──────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(
                                BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (updateInfo.isDownloaded) Color(0xFF10B981) else liquidPink)
                        )
                        Text(
                            text = if (updateInfo.isDownloaded) "READY TO INSTALL"
                                   else if (updateInfo.isDownloading) "DOWNLOADING IN-APP"
                                   else "OFFICIAL UPDATE AVAILABLE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ── 3. App Title & Subtitle ──────────────────────────────
                    Text(
                        text = "AdZero ${updateInfo.versionName}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (updateInfo.isDownloaded) "Update downloaded! Tap below to install."
                               else if (updateInfo.isDownloading) "Streaming update directly into the app..."
                               else "Experience 120Hz smooth scrolling & 0-lag video playback.",
                        fontSize = 13.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── 4. Main Body: Progress Bar OR Frosted Changelog ──────
                    if (updateInfo.isDownloading) {
                        // Liquid Glass Progress Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(subSurfaceGlass)
                                .border(
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading APK",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                                Text(
                                    text = "${(updateInfo.downloadProgress * 100).toInt()}%",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = liquidPink
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Liquid Capsule Progress Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                val progressFraction = updateInfo.downloadProgress.coerceIn(0.05f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    liquidPink,
                                                    Color(0xFFE040FB),
                                                    liquidCyan
                                                )
                                            )
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Auto-installer will open immediately once done",
                                fontSize = 11.sp,
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Frosted Glass Changelog Card
                        if (updateInfo.changelog.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(subSurfaceGlass)
                                    .border(
                                        BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "What's Included",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary,
                                        letterSpacing = 0.3.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = updateInfo.changelog,
                                        fontSize = 12.sp,
                                        color = Color(0xFFD1D5DB),
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Error notice (if any)
                    if (updateInfo.error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Red.copy(alpha = 0.12f))
                                .border(BorderStroke(1.dp, Color.Red.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            Text(text = updateInfo.error, fontSize = 11.sp, color = Color(0xFFFF8A80), maxLines = 2)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── 5. Apple Liquid Glass Action Buttons ─────────────────
                    if (!updateInfo.isDownloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Primary CTA: Vibrant Liquid Gradient Pill
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                liquidPink,
                                                Color(0xFFFF3370),
                                                Color(0xFFFF5252)
                                            )
                                        )
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.55f),
                                                    Color.White.copy(alpha = 0.12f)
                                                )
                                            )
                                        ),
                                        RoundedCornerShape(26.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onUpdateClick()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (updateInfo.isDownloaded) Icons.Outlined.SecurityUpdateGood
                                                      else Icons.Outlined.FileDownload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (updateInfo.isDownloaded) "Install Update Now"
                                               else "Download & Update",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp
                                    )
                                }
                            }

                            // Secondary CTA: Translucent Frosted Glass Dismiss Pill
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(
                                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                        RoundedCornerShape(22.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDismissClick()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Remind Me Later",
                                    color = textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
