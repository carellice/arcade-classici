package com.giochiclassici.arcade.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope

object Neon {
    val Bg0 = Color(0xFF07061A)
    val Bg1 = Color(0xFF140B36)
    val Panel = Color(0xFF181240)
    val PanelHi = Color(0xFF241A5C)
    val Cyan = Color(0xFF22F2FF)
    val Pink = Color(0xFFFF3EA5)
    val Yellow = Color(0xFFFFE14D)
    val Green = Color(0xFF3DFF8B)
    val Orange = Color(0xFFFF8A3D)
    val Purple = Color(0xFFA56BFF)
    val Red = Color(0xFFFF4D5E)
    val Teal = Color(0xFF2DE2B5)
    val Text = Color(0xFFEDEBFF)
    val TextDim = Color(0xFF9C97C9)
}

/** Cerchio con alone luminoso. */
fun DrawScope.glowCircle(
    color: Color,
    center: Offset,
    radius: Float,
    glow: Float = radius * 1.6f,
    intensity: Float = 0.55f,
) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = intensity), Color.Transparent), center, radius + glow),
        radius + glow,
        center,
    )
    drawCircle(color, radius, center)
}

/** Rettangolo arrotondato con alone a strati. */
fun DrawScope.glowRoundRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    corner: Float,
    glow: Float = 10f,
    intensity: Float = 0.3f,
    fill: Brush? = null,
) {
    for (i in 3 downTo 1) {
        val g = glow * i / 3f
        drawRoundRect(
            color.copy(alpha = intensity / 3f),
            Offset(topLeft.x - g, topLeft.y - g),
            Size(size.width + 2 * g, size.height + 2 * g),
            CornerRadius(corner + g),
        )
    }
    drawRoundRect(fill ?: SolidColor(color), topLeft, size, CornerRadius(corner))
}

fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

fun easeOutCubic(t: Float): Float {
    val x = 1f - t
    return 1f - x * x * x
}
