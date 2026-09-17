package com.mizan.money.ui.theme

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class LanguageMode { SYSTEM, ARABIC, ENGLISH }

object LanguagePreference {
    private const val PREFS_NAME = "mizan_prefs"
    private const val KEY = "language_mode"

    fun load(ctx: Context): LanguageMode {
        val name = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, LanguageMode.SYSTEM.name) ?: LanguageMode.SYSTEM.name
        return try { LanguageMode.valueOf(name) } catch (e: Exception) { LanguageMode.SYSTEM }
    }

    fun save(ctx: Context, mode: LanguageMode) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }

    fun localeFor(mode: LanguageMode): Locale? = when (mode) {
        LanguageMode.ARABIC -> Locale.forLanguageTag("ar")
        LanguageMode.ENGLISH -> Locale.forLanguageTag("en")
        LanguageMode.SYSTEM -> null
    }
}

// Returns a Context whose resources are in the user's chosen app language, or
// `base` unchanged for SYSTEM. The Application context keeps the *system*
// locale, so anything that reads strings outside the Activity's localized
// context (notifications, the background worker) must go through this to avoid
// showing Arabic text to an English user.
fun localizedContext(base: Context): Context {
    val locale = LanguagePreference.localeFor(LanguagePreference.load(base)) ?: return base
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}
