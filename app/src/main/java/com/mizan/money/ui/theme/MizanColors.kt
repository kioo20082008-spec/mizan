package com.mizan.money.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class MizanColors(
    val paper: Color,
    val paperOuter: Color,
    val white: Color,
    val line: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val ink900: Color,
    val ink800: Color,
    val onInkSoft: Color,
    val indigo: Color,
    val indigoDeep: Color,
    val indigoSoft: Color,
    val lime: Color,
    val success: Color,
    val danger: Color,
    val amber: Color,
    val purple: Color,
    val isDark: Boolean,
)

val LightMizanColors = MizanColors(
    paper        = Color(0xFFFAF9F6),
    paperOuter   = Color(0xFFF0EEE7),
    white        = Color(0xFFFFFFFF),
    line         = Color(0xFFE9E6DE),
    ink          = Color(0xFF15141A),
    inkSoft      = Color(0xFF6F6D76),
    inkFaint     = Color(0xFF85838C),
    ink900       = Color(0xFF121017),
    ink800       = Color(0xFF1E1B26),
    onInkSoft    = Color(0xFFACA9B8),
    indigo       = Color(0xFF4F46E5),
    indigoDeep   = Color(0xFF3730A3),
    indigoSoft   = Color(0xFFEEEEFD),
    lime         = Color(0xFFD7F26B),
    success      = Color(0xFF22C55E),
    danger       = Color(0xFFF43F5E),
    amber        = Color(0xFFF59E0B),
    purple       = Color(0xFF8B5CF6),
    isDark       = false,
)

val DarkMizanColors = MizanColors(
    // Background layers go from very dark (paper) to slightly lighter (paperOuter),
    // then to "cards" (white). The original Ink900/Ink800 stay dark for hero
    // surfaces — they just need to feel even deeper against the dark paper.
    paper        = Color(0xFF0D0D12),
    paperOuter   = Color(0xFF17171E),
    white        = Color(0xFF1C1C25),
    line         = Color(0xFF2A2A36),
    ink          = Color(0xFFF0EFEA),
    inkSoft      = Color(0xFFA8A6B0),
    inkFaint     = Color(0xFF8A8894),
    ink900       = Color(0xFF08080C),
    ink800       = Color(0xFF141420),
    onInkSoft    = Color(0xFF9A98A6),
    // Signature colors are nudged brighter so they stay legible on a dark
    // background without changing the app's visual identity.
    indigo       = Color(0xFF7C72F0),
    indigoDeep   = Color(0xFF5B51D1),
    indigoSoft   = Color(0xFF1F1F38),
    lime         = Color(0xFFD7F26B),
    success      = Color(0xFF34D399),
    danger       = Color(0xFFFF6B7D),
    amber        = Color(0xFFFFB84D),
    purple       = Color(0xFFA78BFA),
    isDark       = true,
)
