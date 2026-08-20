package org.walkguard.app.ui.i18n

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.walkguard.app.R

enum class WalkGuardAppLanguage(val tag: String?) {
    /** Follow device system locale. */
    SYSTEM(null),
    ENGLISH("en"),
    CHINESE("zh-CN");

    companion object {
        fun fromTag(tag: String?): WalkGuardAppLanguage = when (tag) {
            null, "" -> SYSTEM
            "en" -> ENGLISH
            "zh-CN", "zh" -> CHINESE
            else -> SYSTEM
        }

        fun current(): WalkGuardAppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val first = locales[0]?.toLanguageTag() ?: return SYSTEM
            return fromTag(first)
        }
    }
}

@StringRes
fun WalkGuardAppLanguage.labelRes(): Int = when (this) {
    WalkGuardAppLanguage.SYSTEM -> R.string.language_option_system
    WalkGuardAppLanguage.ENGLISH -> R.string.language_option_english
    WalkGuardAppLanguage.CHINESE -> R.string.language_option_chinese
}

fun applyWalkGuardAppLanguage(language: WalkGuardAppLanguage) {
    val locales = when (language) {
        WalkGuardAppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
        WalkGuardAppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        WalkGuardAppLanguage.CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
    }
    AppCompatDelegate.setApplicationLocales(locales)
}