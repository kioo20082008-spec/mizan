package com.mizan.money.widget

import android.content.Context

// User-selectable widget color (Settings → Appearance). AUTO follows the
// phone's light/dark mode; the solid colors use white text on top.
enum class WidgetTheme(val argb: Long) {
    AUTO(0xFFF7F7F9),
    LIGHT(0xFFF7F7F9),
    DARK(0xFF17171A),
    BLUE(0xFF2F6FED),
    GREEN(0xFF0E9F7E),
    PURPLE(0xFF6B4EE6),
    ROSE(0xFFD9456F),
}

object WidgetThemePreference {
    private const val PREFS_NAME = "mizan_prefs"
    private const val KEY = "widget_theme"

    fun load(ctx: Context): WidgetTheme {
        val name = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, WidgetTheme.AUTO.name) ?: WidgetTheme.AUTO.name
        return runCatching { WidgetTheme.valueOf(name) }.getOrDefault(WidgetTheme.AUTO)
    }

    fun save(ctx: Context, theme: WidgetTheme) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, theme.name).apply()
    }
}
