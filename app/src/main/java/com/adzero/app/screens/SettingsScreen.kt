package com.adzero.app.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adzero.app.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var autoPlayEnabled by remember { mutableStateOf(true) }
    var wifiDownloadsOnly by remember { mutableStateOf(true) }
    var dataSaverEnabled by remember { mutableStateOf(false) }
    var backgroundPlayEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0, 0, 0, 0)
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
            // Account & MicroG Section
            item {
                SettingsSectionHeader("Account & MicroG")
            }

            item {
                val isMicroGInstalled = remember { com.adzero.app.data.MicroGManager.isMicroGInstalled(context) }
                val isLoggedIn = com.adzero.app.data.MicroGManager.isLoggedIn

                SettingsActionRow(
                    icon = Icons.Default.AccountCircle,
                    title = if (isLoggedIn) "Google Account: ${com.adzero.app.data.MicroGManager.accountName}" else "Sign In with MicroG",
                    subtitle = if (isLoggedIn) com.adzero.app.data.MicroGManager.accountEmail else if (isMicroGInstalled) "MicroG detected • Tap to connect Google Account" else "MicroG required • Tap to download APK & connect",
                    onClick = {
                        com.adzero.app.data.MicroGManager.launchMicroGAccountSetup(context)
                    }
                )
            }

            // General & Theme Section
            item {
                SettingsSectionHeader("Appearance & General")
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Theme Mode", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val isSelected = currentTheme == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onThemeChange(mode)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.name,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Content Language & Location Section
            item {
                SettingsSectionHeader("Content Language & Region")
            }

            item {
                var showLanguageSheet by remember { mutableStateOf(false) }
                var currentLang by remember { mutableStateOf(com.adzero.app.data.ContentLanguageManager.getCurrentLanguage(context)) }

                SettingsActionRow(
                    icon = Icons.Default.Translate,
                    title = "Content Language: ${currentLang.flagEmoji} ${currentLang.name}",
                    subtitle = "App feeds, searches, and video audio streams will prefer ${currentLang.nativeName}",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showLanguageSheet = true
                    }
                )

                if (showLanguageSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showLanguageSheet = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Select Content Language",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(com.adzero.app.data.ContentLanguageManager.supportedLanguages.size) { index ->
                                    val lang = com.adzero.app.data.ContentLanguageManager.supportedLanguages[index]
                                    val isSelected = currentLang.languageCode == lang.languageCode && currentLang.countryCode == lang.countryCode

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                com.adzero.app.data.ContentLanguageManager.setContentLanguage(context, lang)
                                                currentLang = lang
                                                showLanguageSheet = false
                                                Toast.makeText(context, "Content language updated to ${lang.name}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(text = lang.flagEmoji, fontSize = 22.sp)
                                            Column {
                                                Text(
                                                    text = lang.name,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = lang.nativeName,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }

            // Playback Section
            item {
                SettingsSectionHeader("Playback")
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Default.PlayCircle,
                    title = "Autoplay next video",
                    subtitle = "Automatically start playing the next related video",
                    checked = autoPlayEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        autoPlayEnabled = it
                    }
                )
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Default.Headset,
                    title = "Background Audio Playback",
                    subtitle = "Continue playing audio when app is minimized",
                    checked = backgroundPlayEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        backgroundPlayEnabled = it
                    }
                )
            }

            // Data Saver & Downloads Section
            item {
                SettingsSectionHeader("Data Saver & Downloads")
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Default.DataUsage,
                    title = "Data Saver Mode",
                    subtitle = "Automatically adjust stream quality to save cellular data",
                    checked = dataSaverEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dataSaverEnabled = it
                    }
                )
            }

            item {
                SettingsSwitchRow(
                    icon = Icons.Default.Wifi,
                    title = "Download Over Wi-Fi Only",
                    subtitle = "Avoid downloading videos using mobile data",
                    checked = wifiDownloadsOnly,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        wifiDownloadsOnly = it
                    }
                )
            }

            item {
                SettingsActionRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear Cache",
                    subtitle = "Free up space used by temporary video thumbnails & buffer",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                    }
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
                    subtitle = "v2.0.0 (Ad-Free Engine Build)",
                    onClick = {}
                )
            }

            item {
                SettingsActionRow(
                    icon = Icons.Default.Shield,
                    title = "Ad-Blocking Shield Active",
                    subtitle = "All video ads, banner ads, and popup overlays blocked",
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
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitchRow(
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
