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

// Samsung One UI–inspired palette. Neutral grey canvas, white rounded cards,
// one blue accent. The legacy token names are kept so every screen picks the
// new look up automatically:
//   ink900/ink800 = accent surface (primary buttons, highlight cards)
//   lime          = content drawn ON that accent surface (white)
//   onInkSoft     = secondary content on the accent surface
val LightMizanColors = MizanColors(
    paper        = Color(0xFFF6F6F8),
    paperOuter   = Color(0xFFEBEBEF),
    white        = Color(0xFFFFFFFF),
    line         = Color(0xFFE3E3E8),
    ink          = Color(0xFF111114),
    inkSoft      = Color(0xFF6B6B72),
    inkFaint     = Color(0xFF85858C),
    ink900       = Color(0xFF2F6FED),
    ink800       = Color(0xFF2F6FED),
    onInkSoft    = Color(0xFFDCE7FD),
    indigo       = Color(0xFF2F6FED),
    indigoDeep   = Color(0xFF1F57C7),
    indigoSoft   = Color(0xFFE7EFFE),
    lime         = Color(0xFFFFFFFF),
    success      = Color(0xFF168A4A),
    danger       = Color(0xFFE5383B),
    amber        = Color(0xFFE08600),
    purple       = Color(0xFF7C4DDB),
    isDark       = false,
)

val DarkMizanColors = MizanColors(
    // One UI dark: true-black canvas, charcoal cards.
    paper        = Color(0xFF000000),
    paperOuter   = Color(0xFF232326),
    white        = Color(0xFF17171A),
    line         = Color(0xFF2B2B30),
    ink          = Color(0xFFF2F2F5),
    inkSoft      = Color(0xFFA2A2A8),
    inkFaint     = Color(0xFF8A8A91),
    ink900       = Color(0xFF3478F6),
    ink800       = Color(0xFF3478F6),
    onInkSoft    = Color(0xFFDCE7FD),
    indigo       = Color(0xFF5B9BFF),
    indigoDeep   = Color(0xFF3478F6),
    indigoSoft   = Color(0xFF14243F),
    lime         = Color(0xFFFFFFFF),
    success      = Color(0xFF34C77B),
    danger       = Color(0xFFFF5A5F),
    amber        = Color(0xFFFFB340),
    purple       = Color(0xFFA78BFA),
    isDark       = true,
)
