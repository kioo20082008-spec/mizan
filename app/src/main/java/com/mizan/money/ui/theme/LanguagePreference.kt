package com.mizan.money.ui.theme

import android.content.Context

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
}
