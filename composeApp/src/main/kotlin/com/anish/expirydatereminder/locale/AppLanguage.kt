package com.anish.expirydatereminder.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The languages the app ships translations for, plus "follow the system".
 *
 * Backing the Settings picker. Uses [AppCompatDelegate.setApplicationLocales] rather than
 * recreating the Activity by hand, so the choice persists across launches and is mirrored
 * into the system per-app language screen on Android 13+.
 */
enum class AppLanguage(val tag: String?, val endonym: String) {
    SYSTEM(null, ""),
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español"),
    ITALIAN("it", "Italiano"),
    DUTCH("nl", "Nederlands"),
    PORTUGUESE("pt", "Português"),
    POLISH("pl", "Polski"),
    ;

    companion object {
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                .substringBefore(',')
                .substringBefore('-')
                .takeIf { it.isNotBlank() }
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }

        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList(),
            )
        }
    }
}
