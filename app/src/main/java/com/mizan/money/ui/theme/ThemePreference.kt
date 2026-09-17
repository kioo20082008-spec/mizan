package com.mizan.money.ui.theme

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemePreference {
    private const val PREFS_NAME = "mizan_prefs"
    private const val KEY = "theme_mode"

    fun load(ctx: Context): ThemeMode {
        val name = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.SYSTEM }
    }

    fun save(ctx: Context, mode: ThemeMode) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }
}
