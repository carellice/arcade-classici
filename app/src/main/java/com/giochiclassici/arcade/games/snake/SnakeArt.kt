package com.giochiclassici.arcade.games.snake

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.easeOutBack
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object SnakeColors {
    val Head = Color(0xFF9CF25B)
    val Tail = Color(0xFF12B5A2)
    val Outline = Color(0xFF07322A)
    val Spot = Color(0xFF0A6B52)
    val DeadHead = Color(0xFFFF7A86)
    val DeadTail = Color(0xFF6E2335)
}

/** Campionamento della linea centrale del serpente. */
private class Samples(n: Int) {
    val x = FloatArray(n); val y = FloatArray(n); val r = FloatArray(n); val f = FloatArray(n); val d = FloatArray(n)
    var count = 0
}

private fun sample(points: List<Offset>, cell: Float, bulges: List<Float>): Samples {
    var total = 0f
    for (i in 1 until points.size) total += (points[i] - points[i - 1]).getDistance()
    val step = cell * 0.15f
    val s = Samples((total / step).toInt() + 3)
    fun add(p: Offset, dist: Float) {
        if (s.count >= s.x.size) return
        val f = if (total > 0f) dist / total else 0f
        var taper = 1f
        if (f > 0.5f) taper = 1f - 0.62f * ((f - 0.5f) / 0.5f).pow(1.25f)
        var r = cell * 0.39f * taper
        val dc = dist / cell
        for (b in bulges) {
            val k = dc - b
            r *= 1f + 0.34f * exp(-(k * k) / 0.32f)
        }
        val i = s.count++
        s.x[i] = p.x; s.y[i] = p.y; s.r[i] = max(r, cell * 0.1f); s.f[i] = f; s.d[i] = dist
    }
    add(points[0], 0f)
    var acc = 0f
    var next = step
    for (i in 1 until points.size) {
        val a = points[i - 1]; val b = points[i]
        val len = (b - a).getDistance()
        while (next <= acc + len && len > 0f) {
            add(a + (b - a) * ((next - acc) / len), next)
            next += step
        }
        acc += len
    }
    if (points.size > 1) add(points.last(), total)
    return s
}

/**
 * Serpente in stile 3D. [points] = linea centrale in pixel dalla testa alla coda.
 * Se [board] non è nullo, il serpente viene disegnato anche "dall'altra parte" del campo
 * quando attraversa un bordo (va usato dentro un clipRect delle stesse dimensioni).
 */
fun DrawScope.drawSnake(
    points: List<Offset>,
    cell: Float,
    time: Float,
    dead: Boolean = false,
    bulges: List<Float> = emptyList(),
    board: Size? = null,
) {
    if (points.isEmpty()) return
    val s = sample(points, cell, bulges)
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (i in 0 until s.count) {
        minX = min(minX, s.x[i] - cell); maxX = max(maxX, s.x[i] + cell)
        minY = min(minY, s.y[i] - cell); maxY = max(maxY, s.y[i] + cell)
    }
    val offsets = ArrayList<Offset>(4)
    if (board == null) offsets += Offset.Zero
    else for (ox in -1..1) for (oy in -1..1) {
        val dx = ox * board.width; val dy = oy * board.height
        if (maxX + dx >= 0f && minX + dx <= board.width && maxY + dy >= 0f && minY + dy <= board.height) offsets += Offset(dx, dy)
    }
    // Direzione della testa
    var hd = Offset(1f, 0f)
    for (i in 1 until s.count) {
        val v = Offset(s.x[0] - s.x[i], s.y[0] - s.y[i])
        val l = v.getDistance()
        if (l > cell * 0.3f) { hd = v / l; break }
    }
    for (o in offsets) translate(o.x, o.y) { drawSnakeOnce(s, cell, time, dead, hd) }
}

private fun DrawScope.drawSnakeOnce(s: Samples, cell: Float, time: Float, dead: Boolean, hd: Offset) {
    val head = if (dead) SnakeColors.DeadHead else SnakeColors.Head
    val tail = if (dead) SnakeColors.DeadTail else SnakeColors.Tail
    val n = s.count
    val shadowOff = cell * 0.16f
    // Ombra proiettata sul campo
    for (i in n - 1 downTo 0) drawCircle(Color.Black, s.r[i] * 1.02f, Offset(s.x[i], s.y[i] + shadowOff), alpha = 0.3f)
    // Contorno scuro
    for (i in n - 1 downTo 0) drawCircle(SnakeColors.Outline, s.r[i] + cell * 0.05f, Offset(s.x[i], s.y[i]))
    // Corpo con sfumatura testa → coda e ombreggiatura inferiore
    for (i in n - 1 downTo 0) {
        val c = lerp(head, tail, s.f[i])
        drawCircle(lerp(c, Color.Black, 0.3f), s.r[i], Offset(s.x[i], s.y[i]))
    }
    for (i in n - 1 downTo 0) {
        val c = lerp(head, tail, s.f[i])
        drawCircle(c, s.r[i] * 0.8f, Offset(s.x[i], s.y[i] - s.r[i] * 0.14f))
    }
    // Macchie sul dorso
    var nextSpot = cell * 0.9f
    var side = 1f
    for (i in 1 until n - 1) {
        if (s.d[i] < nextSpot) continue
        nextSpot += cell * 0.62f
        side = -side
        val tx = s.x[i + 1] - s.x[i - 1]; val ty = s.y[i + 1] - s.y[i - 1]
        val l = sqrt(tx * tx + ty * ty).coerceAtLeast(0.001f)
        val px = -ty / l; val py = tx / l
        val rr = s.r[i]
        drawCircle(
            if (dead) Color(0xFF4A1020) else SnakeColors.Spot, rr * 0.24f,
            Offset(s.x[i] + px * rr * 0.32f * side, s.y[i] + py * rr * 0.32f * side - rr * 0.05f), alpha = 0.55f,
        )
    }
    // Riflesso lucido lungo il dorso
    for (i in n - 1 downTo 0) {
        val rr = s.r[i]
        drawCircle(Color.White, rr * 0.36f, Offset(s.x[i] - rr * 0.22f, s.y[i] - rr * 0.36f), alpha = 0.22f)
    }
    drawSnakeHead(Offset(s.x[0], s.y[0]), s.r[0], cell, hd, time, dead, head)
}

private fun DrawScope.drawSnakeHead(c: Offset, bodyR: Float, cell: Float, dir: Offset, time: Float, dead: Boolean, color: Color) {
    val angle = atan2(dir.y, dir.x) * 180f / PI.toFloat()
    val len = max(bodyR, cell * 0.39f) * 2.75f
    val wid = max(bodyR, cell * 0.39f) * 2.35f
    val center = c + dir * (cell * 0.08f)
    // Lingua biforcuta che guizza ogni tanto
    val tp = (time % 2.6f) / 0.32f
    if (!dead && tp < 1f) {
        val ext = sin(tp * PI.toFloat()) * cell * 0.45f
        val base = center + dir * (len * 0.45f)
        val tip = base + dir * ext
        val perp = Offset(-dir.y, dir.x)
        drawLine(Color(0xFFFF3D6E), base, tip, cell * 0.06f, StrokeCap.Round)
        drawLine(Color(0xFFFF3D6E), tip, tip + dir * (cell * 0.1f) + perp * (cell * 0.08f), cell * 0.05f, StrokeCap.Round)
        drawLine(Color(0xFFFF3D6E), tip, tip + dir * (cell * 0.1f) - perp * (cell * 0.08f), cell * 0.05f, StrokeCap.Round)
    }
    rotate(angle, center) {
        val tl = Offset(center.x - len / 2, center.y - wid / 2)
        drawOval(Color.Black, tl + Offset(0f, cell * 0.16f), Size(len, wid), alpha = 0.3f)
        drawOval(SnakeColors.Outline, tl - Offset(cell * 0.05f, cell * 0.05f), Size(len + cell * 0.1f, wid + cell * 0.1f))
        drawOval(
            Brush.radialGradient(
                listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.3f)),
                Offset(center.x, center.y - wid * 0.25f), wid * 0.9f,
            ),
            tl, Size(len, wid),
        )
        // Narici
        drawCircle(SnakeColors.Outline, cell * 0.035f, Offset(center.x + len * 0.4f, center.y - wid * 0.12f))
        drawCircle(SnakeColors.Outline, cell * 0.035f, Offset(center.x + len * 0.4f, center.y + wid * 0.12f))
    }
    // Occhi (fuori dalla rotazione: le pupille guardano nella direzione di marcia)
    val perp = Offset(-dir.y, dir.x)
    val blink = !dead && (time % 3.4f) < 0.13f
    for (sgn in listOf(-1f, 1f)) {
        val e = center + dir * (len * 0.08f) + perp * (wid * 0.3f * sgn)
        val er = cell * 0.17f
        drawCircle(SnakeColors.Outline, er + cell * 0.035f, e)
        if (dead) {
            drawCircle(Color.White, er, e)
            val k = er * 0.55f
            drawLine(SnakeColors.Outline, e - Offset(k, k), e + Offset(k, k), cell * 0.05f, StrokeCap.Round)
            drawLine(SnakeColors.Outline, e + Offset(k, -k), e + Offset(-k, k), cell * 0.05f, StrokeCap.Round)
        } else if (blink) {
            drawLine(SnakeColors.Outline, e - perp * er, e + perp * er, cell * 0.06f, StrokeCap.Round)
        } else {
            drawCircle(Color.White, er, e)
            val pupil = e + dir * (er * 0.35f)
            drawCircle(Color(0xFF101030), er * 0.55f, pupil)
            drawCircle(Color.White, er * 0.2f, pupil + Offset(-er * 0.2f, -er * 0.25f))
        }
    }
}

/** Mela 3D che rimbalza leggermente. [scale] serve per le animazioni di comparsa/ingestione. */
fun DrawScope.drawApple(center: Offset, cell: Float, time: Float, scale: Float = 1f) {
    if (scale <= 0.01f) return
    val bob = sin(time * 4f) * cell * 0.05f
    val r = cell * 0.34f * scale
    val c = center + Offset(0f, -cell * 0.04f + bob)
    // Ombra che si allarga/stringe col rimbalzo
    val sh = 1f - bob / (cell * 0.2f)
    drawOval(
        Color.Black.copy(alpha = 0.35f),
        Offset(center.x - r * 0.85f * sh, center.y + r * 0.85f), Size(r * 1.7f * sh, r * 0.45f),
    )
    drawCircle(Neon.Pink.copy(alpha = 0.18f), r * 1.6f, c)
    drawCircle(Color(0xFF6E0A26), r * 1.05f, c + Offset(0f, r * 0.04f))
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFA3C2), Color(0xFFFF2E6A), Color(0xFFB3123F)),
            c + Offset(-r * 0.35f, -r * 0.4f), r * 1.5f,
        ),
        r, c,
    )
    drawOval(Color.White.copy(alpha = 0.7f), c + Offset(-r * 0.55f, -r * 0.6f), Size(r * 0.45f, r * 0.3f))
    drawLine(Color(0xFF6B3E1E), c + Offset(0f, -r * 0.85f), c + Offset(r * 0.12f, -r * 1.3f), cell * 0.07f * scale, StrokeCap.Round)
    rotate(-30f, c + Offset(r * 0.45f, -r * 1.1f)) {
        drawOval(
            Brush.linearGradient(listOf(Color(0xFF7DFF8C), Color(0xFF1E9E4A))),
            c + Offset(r * 0.15f, -r * 1.3f), Size(r * 0.7f, r * 0.38f),
        )
    }
}

/** Stella dorata 3D con anello del tempo rimasto. */
fun DrawScope.drawStar(center: Offset, cell: Float, time: Float, timeLeft: Float = 1f) {
    val r = cell * 0.46f * (1f + 0.06f * sin(time * 7f))
    val bob = sin(time * 3.5f) * cell * 0.05f
    val c = center + Offset(0f, bob - cell * 0.04f)
    drawCircle(Brush.radialGradient(listOf(Neon.Yellow.copy(alpha = 0.45f), Color.Transparent), c, r * 2.3f), r * 2.3f, c)
    fun star(rr: Float, at: Offset): Path {
        val p = Path()
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
            val k = if (i % 2 == 0) rr else rr * 0.48f
            val q = at + Offset(cos(a) * k, sin(a) * k)
            if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y)
        }
        p.close()
        return p
    }
    rotate(sin(time * 2f) * 14f, c) {
        drawPath(star(r, c + Offset(0f, cell * 0.12f)), Color.Black.copy(alpha = 0.3f))
        drawPath(star(r * 1.08f, c), Color(0xFF7A4A00))
        drawPath(star(r, c + Offset(0f, cell * 0.04f)), Color(0xFFC77800))
        drawPath(star(r * 0.92f, c), Brush.linearGradient(listOf(Color(0xFFFFF6B0), Color(0xFFFFC21A)), c - Offset(r, r), c + Offset(r, r)))
        drawPath(star(r * 0.4f, c - Offset(r * 0.15f, r * 0.2f)), Color.White.copy(alpha = 0.45f))
    }
    if (timeLeft < 1f) {
        drawArc(
            Neon.Yellow, -90f, 360f * timeLeft, false,
            center - Offset(cell * 0.66f, cell * 0.66f), Size(cell * 1.32f, cell * 1.32f),
            style = Stroke(cell * 0.07f, cap = StrokeCap.Round), alpha = 0.85f,
        )
    }
}

/**
 * Campo di gioco in rilievo: cornice 3D, piastrelle e bordi "portale" animati
 * per ricordare che si può attraversarli.
 */
fun DrawScope.drawSnakeBoard(w: Float, h: Float, cell: Float, time: Float, pulse: Float) {
    val frame = cell * 0.32f
    val r = cell * 0.5f
    // Cornice: ombra, fianco e faccia
    drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(-frame, -frame + cell * 0.3f), Size(w + 2 * frame, h + 2 * frame), CornerRadius(r + frame))
    drawRoundRect(Color(0xFF0E3B33), Offset(-frame, -frame + cell * 0.18f), Size(w + 2 * frame, h + 2 * frame), CornerRadius(r + frame))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF3B7E6E), Color(0xFF1B4C43)), -frame, h + frame),
        Offset(-frame, -frame), Size(w + 2 * frame, h + 2 * frame), CornerRadius(r + frame),
    )
    drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(-frame, -frame), Size(w + 2 * frame, h + 2 * frame), CornerRadius(r + frame), style = Stroke(1.5f * density))
    // Fondo incavato
    drawRoundRect(Color(0xFF0B1F24), Offset.Zero, Size(w, h), CornerRadius(r))
    // Piastrelle a scacchiera con leggero rilievo
    val g = cell * 0.04f
    for (yy in 0 until (h / cell).toInt()) for (xx in 0 until (w / cell).toInt()) {
        val tl = Offset(xx * cell + g, yy * cell + g)
        val col = if ((xx + yy) % 2 == 0) Color(0xFF13343A) else Color(0xFF102C32)
        drawRoundRect(col, tl, Size(cell - 2 * g, cell - 2 * g), CornerRadius(cell * 0.16f))
    }
    // Ombra interna in alto (profondità)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), 0f, cell * 1.2f),
        Offset.Zero, Size(w, cell * 1.2f), CornerRadius(r),
    )
    // Bordi portale tratteggiati in movimento
    val dash = PathEffect.dashPathEffect(floatArrayOf(cell * 0.45f, cell * 0.3f), -time * cell * 1.5f)
    drawRoundRect(
        Neon.Cyan.copy(alpha = 0.35f + 0.25f * pulse), Offset(1f, 1f), Size(w - 2f, h - 2f), CornerRadius(r),
        style = Stroke(cell * 0.08f, pathEffect = dash, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Animazione di comparsa per il cibo appena generato. */
fun popIn(age: Float): Float = if (age >= 0.35f) 1f else easeOutBack((age / 0.35f).coerceIn(0f, 1f))
