package com.adzero.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.adzero.app.navigation.MainAppNavigation
import com.adzero.app.theme.AdZeroTheme
import com.adzero.app.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Edge-to-Edge support for Android 15 + modern layouts
        enableEdgeToEdge()

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
}
