package com.mizan.money.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalMizanColors = staticCompositionLocalOf<MizanColors> {
    error("MizanColors not provided. Wrap your tree in ProvideMizanTheme.")
}

object MizanTheme {
    val colors: MizanColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMizanColors.current
}

@Composable
fun ProvideMizanTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (isDark) DarkMizanColors else LightMizanColors
    // Status bar blends into the canvas and the navigation bar into the bottom
    // tab bar, with icon contrast following the theme (like One UI).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = colors.paper.toArgb()
            window.navigationBarColor = colors.white.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }
    CompositionLocalProvider(
        LocalMizanColors provides colors,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
