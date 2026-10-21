package dev.plexlite.tv.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object AppLocale {

    val supported: List<Pair<String, String>> = listOf(
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "it" to "Italiano",
        "pt" to "Português",
        "ru" to "Русский",
        "zh" to "中文",
        "ar" to "العربية",
        "hi" to "हिन्दी",
    )

    private val systemLocale: Locale = Resources.getSystem().configuration.locales[0]

    fun resolve(tag: String?): Locale = if (tag.isNullOrEmpty()) systemLocale else Locale.forLanguageTag(tag)

    fun wrap(base: Context, tag: String?): Context {
        val locale = resolve(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
