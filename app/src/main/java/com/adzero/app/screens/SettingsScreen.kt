package com.adzero.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adzero.app.data.ContentLanguageManager
import com.adzero.app.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    var sponsorBlockEnabled by remember { mutableStateOf(true) }
    var adBlockerEnabled by remember { mutableStateOf(true) }
    var backgroundPlayEnabled by remember { mutableStateOf(true) }
    var highQualityAudioEnabled by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf(ContentLanguageManager.getCurrentLanguage(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // General & Theme Section
            item {
                SettingsSectionHeader("Appearance & General")
            }

            item {
                SettingsActionRow(
                    icon = Icons.Default.Palette,
                    title = "App Theme",
                    subtitle = "Current: ${currentTheme.displayName}",
                    onClick = {
                        val nextTheme = when (currentTheme) {
                            ThemeMode.AMOLED, ThemeMode.DARK -> ThemeMode.LIGHT
                            else -> ThemeMode.AMOLED
                        }
                        onThemeChange(nextTheme)
                    }
                )
            }

            item {
                var showLangMenu by remember { mutableStateOf(false) }

                Box {
                    SettingsActionRow(
                        icon = Icons.Default.Language,
                        title = "Content Language",
                        subtitle = "${selectedLanguage.name} (${selectedLanguage.nativeName})",
                        onClick = { showLangMenu = true }
                    )

                    DropdownMenu(
                        expanded = showLangMenu,
                        onDismissRequest = { showLangMenu = false }
                    ) {
                        ContentLanguageManager.supportedLanguages.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("${lang.name} (${lang.nativeName})")
                                        if (lang.languageCode == selectedLanguage.languageCode) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedLanguage = lang
                                    ContentLanguageManager.setContentLanguage(context, lang)
                                    showLangMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Ad Blocking & SponsorBlock Section
            item {
                SettingsSectionHeader("Ad Blocking & SponsorBlock")
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.Block,
                    title = "Ad Zero Engine",
                    subtitle = "Block all video ads, banners, and popup ads completely",
                    checked = adBlockerEnabled,
                    onCheckedChange = { adBlockerEnabled = it }
                )
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.Shield,
                    title = "SponsorBlock Integration",
                    subtitle = "Automatically skip sponsored segments, intros & outros",
                    checked = sponsorBlockEnabled,
                    onCheckedChange = { sponsorBlockEnabled = it }
                )
            }

            // Media & Playback Section
            item {
                SettingsSectionHeader("Media & Playback")
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.PlayCircle,
                    title = "Background Playback",
                    subtitle = "Continue playing audio when app is minimized or screen off",
                    checked = backgroundPlayEnabled,
                    onCheckedChange = { backgroundPlayEnabled = it }
                )
            }

            item {
                SettingsToggleRow(
                    icon = Icons.Default.Speed,
                    title = "High Quality Audio",
                    subtitle = "Stream high-bitrate Opus/AAC audio tracks when available",
                    checked = highQualityAudioEnabled,
                    onCheckedChange = { highQualityAudioEnabled = it }
                )
            }

            // About Section
            item {
                SettingsSectionHeader("About")
            }

            item {
                SettingsActionRow(
                    icon = Icons.Default.Info,
                    title = "AdZero Version",
                    subtitle = "4.0 (Build 2026)",
                    onClick = {}
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
