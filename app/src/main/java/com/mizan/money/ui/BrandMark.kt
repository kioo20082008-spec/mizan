package com.mizan.money.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp

private val BrandIndigo = Color(0xFF4338CA)
private val BrandLime = Color(0xFFA8DA2B)

/**
 * The Mizan mark: a tilted beam on a fulcrum with a lime coin on the high end.
 * Same drawing as the launcher icon (96-unit design space). [framed] draws the
 * indigo rounded tile behind it; pass false to place it on an indigo surface.
 */
@Composable
fun MizanMark(size: Dp, modifier: Modifier = Modifier, framed: Boolean = true) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 96f
        if (framed) drawRoundRect(BrandIndigo, cornerRadius = CornerRadius(22f * s))
        val tri = Path().apply {
            moveTo(38f * s, 80f * s); lineTo(48f * s, 62f * s); lineTo(58f * s, 80f * s); close()
        }
        drawPath(tri, Color.White)
        rotate(-9f, Offset(48f * s, 60f * s)) {
            drawRoundRect(Color.White, Offset(12f * s, 52f * s), Size(72f * s, 12f * s), CornerRadius(6f * s))
            drawCircle(BrandLime, 10f * s, Offset(72f * s, 40f * s))
        }
    }
}
