package com.adzero.app.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = CrimsonRed,
    secondary = PremiumBlue,
    background = Color.White,
    surface = LightGray,
    onPrimary = Color.White,
    onBackground = JetBlack,
    onSurface = JetBlack
)

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonRed,
    secondary = PremiumBlue,
    background = JetBlack,
    surface = DarkGray,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val AmoledColorScheme = darkColorScheme(
    primary = CrimsonRed,
    secondary = PremiumBlue,
    background = PureBlack,
    surface = JetBlack,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

enum class ThemeMode {
    LIGHT, DARK, AMOLED, SYSTEM
}

@Composable
fun AdZeroTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.AMOLED -> AmoledColorScheme
        ThemeMode.SYSTEM -> if (isSystemDark) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            
            val insetsController = WindowCompat.getInsetsController(window, view)
            val isLight = colorScheme == LightColorScheme
            insetsController.isAppearanceLightStatusBars = isLight
            insetsController.isAppearanceLightNavigationBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
