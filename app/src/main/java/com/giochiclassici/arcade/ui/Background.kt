package com.giochiclassici.arcade.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float)

/** Sfondo synthwave animato: cielo stellato, sole al neon e griglia in prospettiva. */
@Composable
fun SynthwaveBackground(modifier: Modifier = Modifier) {
    val time by rememberTime()
    val stars = remember {
        val rnd = Random(7)
        List(110) { Star(rnd.nextFloat(), rnd.nextFloat() * 0.64f, rnd.nextFloat() * 1.6f + 0.5f, rnd.nextFloat() * 6.28f, 1f + rnd.nextFloat() * 2f) }
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.68f
        drawRect(Brush.verticalGradient(listOf(Neon.Bg0, Color(0xFF1A0B3D), Color(0xFF3A0F55)), 0f, horizon))
        for (s in stars) {
            val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * s.speed + s.phase))
            drawCircle(Color.White, s.r * density * 0.6f, Offset(s.x * w, s.y * h), alpha = a * 0.8f)
        }
        // Sole
        val sunR = w * 0.3f
        val sunC = Offset(w / 2f, horizon - sunR * 0.35f)
        drawCircle(
            Brush.radialGradient(listOf(Neon.Pink.copy(alpha = 0.35f), Color.Transparent), sunC, sunR * 1.9f),
            sunR * 1.9f, sunC,
        )
        val sunPath = Path().apply { addOval(androidx.compose.ui.geometry.Rect(sunC, sunR)) }
        clipPath(sunPath) {
            drawRect(
                Brush.verticalGradient(listOf(Neon.Yellow, Neon.Orange, Neon.Pink), sunC.y - sunR, sunC.y + sunR * 0.4f),
                Offset(sunC.x - sunR, sunC.y - sunR), Size(sunR * 2, sunR * 2),
            )
            var y = sunC.y - sunR * 0.05f
            var band = sunR * 0.035f
            val scroll = (time * 0.25f) % 1f
            while (y < sunC.y + sunR) {
                drawRect(Color(0xFF3A0F55), Offset(sunC.x - sunR, y + band * scroll * 4f), Size(sunR * 2, band))
                y += band * 4f
                band *= 1.3f
            }
        }
        // Terreno
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF12062E), Neon.Bg0), horizon, h),
            Offset(0f, horizon), Size(w, h - horizon),
        )
        val gridColor = Neon.Pink
        val n = 14
        val t = (time * 0.5f) % 1f
        for (i in 0 until n) {
            val p = ((i + t) / n).pow(2.2f)
            val y = horizon + (h - horizon) * p
            drawLine(gridColor, Offset(0f, y), Offset(w, y), (1f + p * 2.5f) * density * 0.6f, alpha = 0.15f + 0.6f * p)
        }
        for (k in -12..12) {
            val xb = w / 2f + k * w * 0.16f
            drawLine(gridColor, Offset(w / 2f + k * w * 0.012f, horizon), Offset(xb, h), density * 1.1f, alpha = 0.45f)
        }
        drawLine(Neon.Cyan, Offset(0f, horizon), Offset(w, horizon), density * 2f, alpha = 0.9f)
        drawRect(
            Brush.verticalGradient(listOf(Neon.Cyan.copy(alpha = 0.25f), Color.Transparent), horizon - 30f * density, horizon),
            Offset(0f, horizon - 30f * density), Size(w, 30f * density),
        )
    }
}

/** Sfondo più sobrio per le schermate di gioco. */
@Composable
fun GameBackground(accent: Color, modifier: Modifier = Modifier) {
    val time by rememberTime()
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Neon.Bg0, Neon.Bg1, Neon.Bg0)))
        val step = 34f * density
        val off = (time * 8f * density) % step
        var y = -step + off
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawCircle(accent, 1.1f * density, Offset(x, y), alpha = 0.12f)
                x += step
            }
            y += step
        }
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent), Offset(size.width / 2, size.height * 0.15f), size.width),
            size.width, Offset(size.width / 2, size.height * 0.15f),
        )
    }
}
