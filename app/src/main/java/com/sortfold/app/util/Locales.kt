package com.sortfold.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

data class AppLocale(val tag: String?, val nativeName: String, val englishName: String)

/**
 * Per-app language switching. Uses AppCompatDelegate (works back to Android 5,
 * delegates to the system LocaleManager on Android 13+) so the change applies
 * to every activity, dialog, notification and service instantly.
 */
object Locales {

    val available: List<AppLocale> = listOf(
        AppLocale(null, "System", "System default"),
        AppLocale("en", "English", "English"),
        AppLocale("ms", "Bahasa Melayu", "Malay"),
        AppLocale("in", "Bahasa Indonesia", "Indonesian"),
        AppLocale("ar", "العربية", "Arabic (RTL)"),
        AppLocale("zh-CN", "简体中文", "Chinese (Simplified)"),
    )

    fun currentTag(): String? =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { null }

    /** Empty tag resets to the system language. */
    fun apply(tag: String?) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag ?: ""))
    }
}
