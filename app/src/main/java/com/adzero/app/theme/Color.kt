package com.adzero.app.theme

import androidx.compose.ui.graphics.Color

val CrimsonRed = Color(decodeColor("#FF3B30"))
val DarkRed = Color(decodeColor("#C70039"))
val PureBlack = Color(decodeColor("#000000"))
val JetBlack = Color(decodeColor("#0F0F0F"))
val DarkGray = Color(decodeColor("#1F1F1F"))
val LightGray = Color(decodeColor("#F2F2F2"))
val MediumGray = Color(decodeColor("#8E8E93"))
val PremiumBlue = Color(decodeColor("#007AFF"))

// Decodes standard hex colors safely
private fun decodeColor(colorString: String): Int {
    return android.graphics.Color.parseColor(colorString)
}
