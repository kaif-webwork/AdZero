package com.adzero.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.adzero.app.navigation.MainAppNavigation
import com.adzero.app.theme.AdZeroTheme
import com.adzero.app.theme.ThemeMode

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Edge-to-Edge support for Android 15 + modern layouts
        enableEdgeToEdge()

        // Request notification permission on Android 13+ (API 33+) for Lock Screen media controls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.AMOLED) }

            AdZeroTheme(themeMode = themeMode) {
                MainAppNavigation(
                    currentTheme = themeMode,
                    onThemeChange = { newMode -> themeMode = newMode }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipModeIfPlaying()
    }

    private fun enterPipModeIfPlaying() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val player = com.adzero.app.data.GlobalPlayerManager.getPlayer(this)
            if (player.isPlaying) {
                try {
                    val aspectRatio = android.util.Rational(16, 9)
                    val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(aspectRatio)
                            .setAutoEnterEnabled(true)
                            .build()
                    } else {
                        android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(aspectRatio)
                            .build()
                    }
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
