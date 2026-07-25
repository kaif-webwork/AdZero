package com.adzero.app.data

import android.content.Context
import android.content.SharedPreferences
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

data class ContentLanguage(
    val name: String,
    val nativeName: String,
    val languageCode: String,
    val countryCode: String,
    val flagEmoji: String
)

object ContentLanguageManager {
    private const val PREF_NAME = "adzero_content_lang_prefs"
    private const val KEY_LANG_CODE = "content_lang_code"
    private const val KEY_COUNTRY_CODE = "content_country_code"

    val supportedLanguages = listOf(
        ContentLanguage("English (US)", "English", "en", "US", "🇺🇸"),
        ContentLanguage("Hindi", "हिंदी", "hi", "IN", "🇮🇳"),
        ContentLanguage("Spanish", "Español", "es", "ES", "🇪🇸"),
        ContentLanguage("French", "Français", "fr", "FR", "🇫🇷"),
        ContentLanguage("German", "Deutsch", "de", "DE", "🇩🇪"),
        ContentLanguage("Japanese", "日本語", "ja", "JP", "🇯🇵"),
        ContentLanguage("Korean", "한국어", "ko", "KR", "🇰🇷"),
        ContentLanguage("Russian", "Русский", "ru", "RU", "🇷🇺"),
        ContentLanguage("Arabic", "العربية", "ar", "SA", "🇸🇦"),
        ContentLanguage("Portuguese", "Português", "pt", "BR", "🇧🇷"),
        ContentLanguage("Bengali", "বাংলা", "bn", "IN", "🇮🇳"),
        ContentLanguage("Marathi", "मराठी", "mr", "IN", "🇮🇳"),
        ContentLanguage("Tamil", "தமிழ்", "ta", "IN", "🇮🇳"),
        ContentLanguage("Telugu", "తెలుగు", "te", "IN", "🇮🇳")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getCurrentLanguage(context: Context): ContentLanguage {
        val prefs = getPrefs(context)
        val langCode = prefs.getString(KEY_LANG_CODE, "en") ?: "en"
        val countryCode = prefs.getString(KEY_COUNTRY_CODE, "US") ?: "US"
        return supportedLanguages.firstOrNull { it.languageCode == langCode && it.countryCode == countryCode }
            ?: supportedLanguages.first()
    }

    fun setContentLanguage(context: Context, language: ContentLanguage) {
        getPrefs(context).edit()
            .putString(KEY_LANG_CODE, language.languageCode)
            .putString(KEY_COUNTRY_CODE, language.countryCode)
            .apply()
        
        applyToNewPipe(context)
    }

    fun applyToNewPipe(context: Context) {
        val current = getCurrentLanguage(context)
        try {
            val localization = Localization(current.languageCode, current.countryCode)
            if (com.adzero.app.App.isExtractorInitialized.get()) {
                NewPipe.init(com.adzero.app.NewPipeDownloader.getInstance(com.adzero.app.App.okHttpClient), localization)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
