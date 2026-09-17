package com.mizan.money.ui.theme

import androidx.compose.runtime.Composable
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
    CompositionLocalProvider(
        LocalMizanColors provides if (isDark) DarkMizanColors else LightMizanColors,
        content = content,
    )
}
