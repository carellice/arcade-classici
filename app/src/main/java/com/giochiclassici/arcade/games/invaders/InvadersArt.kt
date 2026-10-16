package com.giochiclassici.arcade.games.invaders

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.giochiclassici.arcade.ui.Neon
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

/** Sprite in pixel-art: ogni carattere diverso da '.' è un "voxel" del colore indicato in [palette]. */
class Sprite(val frames: List<List<String>>, val palette: Map<Char, Color>, val main: Color) {
    val w = frames[0][0].length
    val h = frames[0].size
}

private val EYE = Color.White

val ALIEN_A = Sprite(
    listOf(
        listOf(
            "....X....",
            "...XXX...",
            "..XXXXX..",
            ".XXoXoXX.",
            ".XXXXXXX.",
            "..X.X.X..",
            ".X.X.X.X.",
            "X.......X",
        ),
        listOf(
            "....X....",
            "...XXX...",
            "..XXXXX..",
            ".XXoXoXX.",
            ".XXXXXXX.",
            "..X.X.X..",
            "..X...X..",
            ".X.X.X.X.",
        ),
    ),
    mapOf('X' to Neon.Pink, 'o' to EYE), Neon.Pink,
)

val ALIEN_B = Sprite(
    listOf(
        listOf(
            "X.........X",
            ".X.XXXXX.X.",
            "..XXXXXXX..",
            ".XXoXXXoXX.",
            "XXXXXXXXXXX",
            "..XXXXXXX..",
            ".X.X...X.X.",
            "X.........X",
        ),
        listOf(
            "..X.....X..",
            ".X.XXXXX.X.",
            "X.XXXXXXX.X",
            "XXXoXXXoXXX",
            "XXXXXXXXXXX",
            "..XXXXXXX..",
            "..X.....X..",
            ".X.......X.",
        ),
    ),
    mapOf('X' to Neon.Cyan, 'o' to EYE), Neon.Cyan,
)

val ALIEN_C = Sprite(
    listOf(
        listOf(
            "....XXXX....",
            ".XXXXXXXXXX.",
            "XXXXXXXXXXXX",
            "XXXoXXXXoXXX",
            "XXXXXXXXXXXX",
            "..XX.XX.XX..",
            ".XX......XX.",
            "XX........XX",
        ),
        listOf(
            "....XXXX....",
            ".XXXXXXXXXX.",
            "XXXXXXXXXXXX",
            "XXXoXXXXoXXX",
            "XXXXXXXXXXXX",
            ".XX.XXXX.XX.",
            "XX..X..X..XX",
            "..XX....XX..",
        ),
    ),
    mapOf('X' to Neon.Green, 'o' to EYE), Neon.Green,
)

val SPRITES = listOf(ALIEN_A, ALIEN_B, ALIEN_C)

val SHIP = Sprite(
    listOf(
        listOf(
            "......c......",
            ".....XcX.....",
            ".....XXX.....",
            "..r.XXXXX.r..",
            ".rXXXXXXXXXr.",
            "XXXXXXcXXXXXX",
            "XXXXXXXXXXXXX",
            "XX..XX.XX..XX",
        ),
    ),
    mapOf('X' to Color(0xFFDDE3FF), 'c' to Neon.Cyan, 'r' to Neon.Pink), Neon.Cyan,
)

val UFO = Sprite(
    listOf(
        listOf(
            ".....XXXXXX.....",
            "...XXXXXXXXXX...",
            "..XXXXXXXXXXXX..",
            ".XXyXXyXXyXXyXX.",
            "XXXXXXXXXXXXXXXX",
            "..XXX..XX..XXX..",
            "...X........X...",
        ),
        listOf(
            ".....XXXXXX.....",
            "...XXXXXXXXXX...",
            "..XXXXXXXXXXXX..",
            ".XyXXyXXyXXyXXX.",
            "XXXXXXXXXXXXXXXX",
            "..XXX..XX..XXX..",
            "...X........X...",
        ),
    ),
    mapOf('X' to Neon.Red, 'y' to Neon.Yellow), Neon.Red,
)

/** Disegna uno sprite a voxel 3D: alone, fianchi scuri, facce con gradiente. */
fun DrawScope.drawSprite(sp: Sprite, frame: Int, topLeft: Offset, p: Float, glow: Boolean = true) {
    val rows = sp.frames[frame % sp.frames.size]
    val w = sp.w * p
    val h = sp.h * p
    if (glow) {
        // Alone ellittico che sfuma entro il margine della bitmap (vedi renderSprite)
        val c = topLeft + Offset(w / 2, h / 2)
        val r = h / 2 + p * 3.4f
        scale(1.3f, 1f, pivot = c) {
            drawCircle(Brush.radialGradient(listOf(sp.main.copy(alpha = 0.32f), Color.Transparent), c, r), r, c)
        }
    }
    val d = p * 0.3f
    val g = p * 0.05f
    for ((y, row) in rows.withIndex()) for ((x, ch) in row.withIndex()) {
        if (ch == '.') continue
        val col = sp.palette[ch] ?: continue
        drawRect(lerp(col, Color.Black, 0.55f), Offset(topLeft.x + x * p + g, topLeft.y + y * p + d), Size(p - 2 * g, p - g))
    }
    for ((y, row) in rows.withIndex()) for ((x, ch) in row.withIndex()) {
        if (ch == '.') continue
        val col = sp.palette[ch] ?: continue
        val x0 = topLeft.x + x * p + g
        val y0 = topLeft.y + y * p + g
        drawRect(
            Brush.verticalGradient(listOf(lerp(col, Color.White, 0.5f), col), y0, y0 + p),
            Offset(x0, y0), Size(p - 2 * g, p - 2 * g),
        )
    }
}

/** Sprite pre-renderizzato in una bitmap, con il margine usato per l'alone. */
class SpriteImage(val image: ImageBitmap, val pad: Float)

fun renderSprite(sp: Sprite, frame: Int, p: Float, density: Float): SpriteImage {
    val pad = ceil(p * 4f)
    val w = ceil(sp.w * p + 2 * pad).toInt()
    val h = ceil(sp.h * p + p * 0.4f + 2 * pad).toInt()
    val img = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
        drawSprite(sp, frame, Offset(pad, pad), p)
    }
    return SpriteImage(img, pad)
}

/** Disegna gli scudi (coordinate già in pixel, [cell] = lato di una cella). */
fun DrawScope.drawShieldCells(game: InvadersGame, s: Float) {
    val c = game.shieldCell * s
    val d = c * 0.3f
    for (pass in 0..1) for (i in 0 until 4) for (cy in 0 until game.shieldRows) for (cx in 0 until game.shieldCols) {
        if (!game.shieldCells[(i * game.shieldRows + cy) * game.shieldCols + cx]) continue
        val x = (game.shieldX[i] + cx * game.shieldCell) * s
        val y = (game.shieldY + cy * game.shieldCell) * s
        if (pass == 0) {
            drawRect(Color(0xFF0B5A33), Offset(x, y + d), Size(c, c))
        } else {
            val f = cy / game.shieldRows.toFloat()
            drawRect(lerp(Color(0xFF8BFFB0), Color(0xFF22C766), f), Offset(x, y), Size(c, c * 0.92f))
        }
    }
}

/** Capsula di un potenziamento. */
fun DrawScope.drawPowerPill(center: Offset, color: Color, s: Float, time: Float) {
    val w = 58f * s; val h = 30f * s
    val tl = center - Offset(w / 2, h / 2)
    val pulse = 0.5f + 0.5f * sin(time * 8f)
    drawRoundRect(color.copy(alpha = 0.18f + 0.12f * pulse), tl - Offset(8f * s, 8f * s), Size(w + 16f * s, h + 16f * s), CornerRadius(h))
    drawRoundRect(lerp(color, Color.Black, 0.55f), tl + Offset(0f, 5f * s), Size(w, h), CornerRadius(h / 2))
    drawRoundRect(Brush.verticalGradient(listOf(Color.White, color), tl.y, tl.y + h), tl, Size(w, h), CornerRadius(h / 2))
}

/** Proiettili: laser del giocatore e saette degli alieni. */
fun DrawScope.drawShot(shot: Shot, s: Float, time: Float) {
    val x = shot.x * s; val y = shot.y * s
    if (!shot.enemy) {
        val len = 26f * s
        drawLine(Neon.Cyan.copy(alpha = 0.3f), Offset(x, y), Offset(x - shot.vx * 0.015f * s, y + len), 16f * s, StrokeCap.Round)
        drawLine(Neon.Cyan, Offset(x, y), Offset(x - shot.vx * 0.015f * s, y + len), 6f * s, StrokeCap.Round)
        drawLine(Color.White, Offset(x, y), Offset(x - shot.vx * 0.01f * s, y + len * 0.6f), 2.5f * s, StrokeCap.Round)
    } else {
        val seg = 8f * s
        val amp = 6f * s
        val ph = sin(time * 30f + shot.seed)
        val path = Path().apply {
            moveTo(x, y - seg * 3)
            lineTo(x + amp * ph, y - seg * 2)
            lineTo(x - amp * ph, y - seg)
            lineTo(x + amp * ph * 0.6f, y)
        }
        drawPath(path, Neon.Yellow.copy(alpha = 0.3f), style = Stroke(12f * s, cap = StrokeCap.Round))
        drawPath(path, Neon.Yellow, style = Stroke(5f * s, cap = StrokeCap.Round))
        drawCircle(Color.White, 3.5f * s, Offset(x + amp * ph * 0.6f, y))
    }
}

/** Fiamma dei motori della navicella. */
fun DrawScope.drawFlame(cx: Float, bottom: Float, s: Float, time: Float) {
    for (side in listOf(-1f, 1f)) {
        val x = cx + side * 26f * s
        val len = (16f + 8f * sin(time * 40f + side)) * s
        val p = Path().apply {
            moveTo(x - 6f * s, bottom); lineTo(x + 6f * s, bottom); lineTo(x, bottom + len); close()
        }
        drawPath(p, Neon.Orange)
        val q = Path().apply {
            moveTo(x - 3f * s, bottom); lineTo(x + 3f * s, bottom); lineTo(x, bottom + len * 0.6f); close()
        }
        drawPath(q, Neon.Yellow)
    }
}
