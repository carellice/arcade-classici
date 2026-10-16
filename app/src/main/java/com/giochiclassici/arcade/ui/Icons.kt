package com.giochiclassici.arcade.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Icone disegnate a mano, scalate sulla dimensione del DrawScope. */
object NeonIcons {
    private fun DrawScope.sw() = size.minDimension * 0.12f

    private fun DrawScope.slash(c: Color) {
        val w = size.width; val h = size.height
        drawLine(Neon.Bg0, Offset(w * 0.1f, h * 0.1f), Offset(w * 0.9f, h * 0.9f), sw() * 2.2f, StrokeCap.Round)
        drawLine(c, Offset(w * 0.1f, h * 0.1f), Offset(w * 0.9f, h * 0.9f), sw(), StrokeCap.Round)
    }

    fun DrawScope.back(c: Color) {
        val w = size.width; val h = size.height
        val p = Path().apply { moveTo(w * 0.62f, h * 0.15f); lineTo(w * 0.28f, h * 0.5f); lineTo(w * 0.62f, h * 0.85f) }
        drawPath(p, c, style = Stroke(sw(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun DrawScope.pause(c: Color) {
        val w = size.width; val h = size.height
        drawRoundRect(c, Offset(w * 0.2f, h * 0.15f), Size(w * 0.2f, h * 0.7f), CornerRadius(w * 0.06f))
        drawRoundRect(c, Offset(w * 0.6f, h * 0.15f), Size(w * 0.2f, h * 0.7f), CornerRadius(w * 0.06f))
    }

    fun DrawScope.help(c: Color) {
        val w = size.width; val h = size.height
        val r = w * 0.24f
        drawArc(
            c, 200f, 250f, false, Offset(w * 0.5f - r, h * 0.3f - r), Size(r * 2, r * 2),
            style = Stroke(sw(), cap = StrokeCap.Round),
        )
        drawLine(c, Offset(w * 0.5f, h * 0.54f), Offset(w * 0.5f, h * 0.64f), sw(), StrokeCap.Round)
        drawCircle(c, sw() * 0.7f, Offset(w * 0.5f, h * 0.86f))
    }

    fun DrawScope.restart(c: Color) {
        val w = size.width; val h = size.height
        val r = w * 0.34f
        drawArc(
            c, -60f, 290f, false, Offset(w * 0.5f - r, h * 0.5f - r), Size(r * 2, r * 2),
            style = Stroke(sw(), cap = StrokeCap.Round),
        )
        val p = Path().apply {
            moveTo(w * 0.62f, h * 0.06f); lineTo(w * 0.86f, h * 0.2f); lineTo(w * 0.62f, h * 0.36f); close()
        }
        drawPath(p, c)
    }

    fun DrawScope.music(c: Color, on: Boolean) {
        val w = size.width; val h = size.height
        drawCircle(c, w * 0.15f, Offset(w * 0.32f, h * 0.76f))
        drawCircle(c, w * 0.15f, Offset(w * 0.74f, h * 0.66f))
        drawLine(c, Offset(w * 0.45f, h * 0.76f), Offset(w * 0.45f, h * 0.18f), sw() * 0.8f)
        drawLine(c, Offset(w * 0.87f, h * 0.66f), Offset(w * 0.87f, h * 0.08f), sw() * 0.8f)
        drawLine(c, Offset(w * 0.45f, h * 0.2f), Offset(w * 0.87f, h * 0.1f), sw() * 1.3f)
        if (!on) slash(c)
    }

    fun DrawScope.sound(c: Color, on: Boolean) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.08f, h * 0.36f); lineTo(w * 0.28f, h * 0.36f); lineTo(w * 0.52f, h * 0.14f)
            lineTo(w * 0.52f, h * 0.86f); lineTo(w * 0.28f, h * 0.64f); lineTo(w * 0.08f, h * 0.64f); close()
        }
        drawPath(p, c)
        if (on) {
            for ((i, r) in listOf(0.2f, 0.36f).withIndex()) {
                drawArc(
                    c.copy(alpha = 1f - i * 0.3f), -45f, 90f, false,
                    Offset(w * 0.5f - w * r, h * 0.5f - h * r), Size(w * r * 2, h * r * 2),
                    style = Stroke(sw() * 0.8f, cap = StrokeCap.Round),
                )
            }
        } else slash(c)
    }

    fun DrawScope.vibrate(c: Color, on: Boolean) {
        val w = size.width; val h = size.height
        drawRoundRect(
            c, Offset(w * 0.32f, h * 0.1f), Size(w * 0.36f, h * 0.8f), CornerRadius(w * 0.08f),
            style = Stroke(sw() * 0.9f),
        )
        for (side in listOf(-1f, 1f)) {
            val x = w * 0.5f + side * w * 0.36f
            val p = Path().apply {
                moveTo(x, h * 0.25f); lineTo(x + side * w * 0.08f, h * 0.38f)
                lineTo(x - side * w * 0.02f, h * 0.5f); lineTo(x + side * w * 0.08f, h * 0.62f); lineTo(x, h * 0.75f)
            }
            drawPath(p, c, style = Stroke(sw() * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (!on) slash(c)
    }

    fun DrawScope.flag(c: Color) {
        val w = size.width; val h = size.height
        drawLine(c, Offset(w * 0.3f, h * 0.1f), Offset(w * 0.3f, h * 0.9f), sw(), StrokeCap.Round)
        val p = Path().apply { moveTo(w * 0.3f, h * 0.1f); lineTo(w * 0.85f, h * 0.3f); lineTo(w * 0.3f, h * 0.52f); close() }
        drawPath(p, c)
        drawLine(c, Offset(w * 0.12f, h * 0.9f), Offset(w * 0.55f, h * 0.9f), sw(), StrokeCap.Round)
    }

    fun DrawScope.dig(c: Color) {
        val w = size.width; val h = size.height
        drawRoundRect(c, Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.76f), CornerRadius(w * 0.16f), style = Stroke(sw()))
        drawCircle(c, w * 0.14f, Offset(w * 0.5f, h * 0.5f))
    }
}
