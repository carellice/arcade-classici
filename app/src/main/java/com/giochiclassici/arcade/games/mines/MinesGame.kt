package com.giochiclassici.arcade.games.mines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Sfx
import com.giochiclassici.arcade.core.Haptics
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.ParticleSystem
import com.giochiclassici.arcade.ui.glowCircle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class MineDiff(val label: String, val cols: Int, val rows: Int, val mines: Int) {
    EASY("Facile", 8, 10, 10),
    MEDIUM("Medio", 10, 14, 22),
    HARD("Difficile", 12, 18, 42);

    val bestKey get() = "best_mines_${name.lowercase()}"
}

enum class MinesPhase { READY, PLAYING, WON, LOST }
enum class Face { HAPPY, WOW, DEAD, COOL }

class MinesGame(val diff: MineDiff) {
    val cols = diff.cols
    val rows = diff.rows
    val n = cols * rows
    val mine = BooleanArray(n)
    val adj = IntArray(n)
    val revealed = BooleanArray(n)
    val flagged = BooleanArray(n)
    val revealAt = FloatArray(n) { Float.MAX_VALUE }
    val boomAt = FloatArray(n) { Float.MAX_VALUE }
    private val boomDone = BooleanArray(n)

    var exploded = -1
        private set
    var phase by mutableStateOf(MinesPhase.READY)
        private set
    var flags by mutableIntStateOf(0)
        private set
    var seconds by mutableIntStateOf(0)
        private set
    var face by mutableStateOf(Face.HAPPY)
        private set
    var frame by mutableLongStateOf(0L)
        private set

    private var elapsed = 0f
    private var faceTimer = 0f
    private var revealedCount = 0
    var clock = 0f
        private set
    var shake = 0f
        private set
    var endTime = 0f
        private set
    val particles = ParticleSystem()
    /** Diventa true quando è il momento di mostrare il pannello finale. */
    var showResult by mutableStateOf(false)
        private set

    val minesLeft get() = diff.mines - flags
    val finished get() = phase == MinesPhase.WON || phase == MinesPhase.LOST

    fun x(i: Int) = i % cols
    fun y(i: Int) = i / cols

    private inline fun forNeighbors(i: Int, f: (Int) -> Unit) {
        val cx = x(i); val cy = y(i)
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = cx + dx; val ny = cy + dy
            if (nx in 0 until cols && ny in 0 until rows) f(ny * cols + nx)
        }
    }

    private fun placeMines(safe: Int) {
        val forbidden = HashSet<Int>().apply { add(safe); forNeighbors(safe) { add(it) } }
        val candidates = (0 until n).filter { it !in forbidden }.shuffled()
        for (i in candidates.take(diff.mines)) mine[i] = true
        for (i in 0 until n) {
            var c = 0
            forNeighbors(i) { if (mine[it]) c++ }
            adj[i] = c
        }
    }

    fun tap(i: Int, flagMode: Boolean) = if (flagMode) toggleFlag(i) else open(i)
    fun longPress(i: Int, flagMode: Boolean) = if (flagMode) open(i) else toggleFlag(i)

    private fun open(i: Int) {
        if (finished || flagged[i]) return
        if (revealed[i]) { chord(i); return }
        if (phase == MinesPhase.READY) {
            placeMines(i)
            phase = MinesPhase.PLAYING
            Audio.play(Sfx.Start)
        }
        if (mine[i]) { lose(i); return }
        val count = flood(i)
        Audio.play(Sfx.Reveal, rate = if (count > 1) 1.3f else 1f)
        Haptics.tick()
        wow()
        checkWin()
    }

    private fun flood(start: Int): Int {
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(start to 0)
        var count = 0
        while (queue.isNotEmpty()) {
            val (i, d) = queue.removeFirst()
            if (revealed[i] || flagged[i] || mine[i]) continue
            revealed[i] = true
            revealAt[i] = clock + d * 0.028f
            revealedCount++
            count++
            if (adj[i] == 0) forNeighbors(i) { if (!revealed[it]) queue.add(it to d + 1) }
        }
        return count
    }

    private fun chord(i: Int) {
        if (adj[i] == 0) return
        var f = 0
        forNeighbors(i) { if (flagged[it]) f++ }
        if (f != adj[i]) return
        var opened = 0
        var hitMine = -1
        forNeighbors(i) {
            if (!revealed[it] && !flagged[it]) {
                if (mine[it]) hitMine = it else opened += flood(it)
            }
        }
        if (hitMine >= 0) { lose(hitMine); return }
        if (opened > 0) {
            Audio.play(Sfx.Reveal, rate = 1.15f)
            Haptics.tick()
            wow()
            checkWin()
        }
    }

    private fun toggleFlag(i: Int) {
        if (finished || revealed[i]) return
        flagged[i] = !flagged[i]
        if (flagged[i]) {
            flags++
            Audio.play(Sfx.Flag)
            particles.burst(x(i) + 0.5f, y(i) + 0.5f, Neon.Pink, 8, 3f, 0.08f, 0.4f)
        } else {
            flags--
            Audio.play(Sfx.Unflag)
        }
        Haptics.bump()
    }

    private fun wow() { face = Face.WOW; faceTimer = 0.25f }

    private fun lose(i: Int) {
        phase = MinesPhase.LOST
        face = Face.DEAD
        endTime = clock
        exploded = i
        revealed[i] = true
        revealAt[i] = clock
        boomAt[i] = clock
        val ex = x(i); val ey = y(i)
        val others = (0 until n).filter { mine[it] && it != i && !flagged[it] }
            .sortedBy { hypot((x(it) - ex).toFloat(), (y(it) - ey).toFloat()) }
        val step = min(0.08f, 2.2f / max(1, others.size))
        others.forEachIndexed { k, m -> boomAt[m] = clock + 0.45f + k * step }
        shake = 1f
        Audio.play(Sfx.Boom)
        Haptics.heavy()
    }

    private fun checkWin() {
        if (revealedCount != n - diff.mines) return
        phase = MinesPhase.WON
        face = Face.COOL
        endTime = clock
        for (i in 0 until n) if (mine[i] && !flagged[i]) { flagged[i] = true; flags++ }
        Audio.play(Sfx.Win)
        Haptics.bump()
        val colors = listOf(Neon.Cyan, Neon.Pink, Neon.Yellow, Neon.Green, Neon.Purple)
        repeat(6) { k ->
            particles.burst(
                Random.nextFloat() * cols, Random.nextFloat() * rows * 0.6f,
                colors[k % colors.size], 26, 9f, 0.16f, 1.4f, gravity = 6f, drag = 1.2f,
            )
        }
    }

    fun update(dt: Float, frozen: Boolean) {
        clock += dt
        if (phase == MinesPhase.PLAYING && !frozen) {
            elapsed += dt
            seconds = elapsed.toInt()
        }
        if (faceTimer > 0f) {
            faceTimer -= dt
            if (faceTimer <= 0f && !finished) face = Face.HAPPY
        }
        if (phase == MinesPhase.LOST) {
            var played = 0
            for (i in 0 until n) {
                if (!boomDone[i] && clock >= boomAt[i]) {
                    boomDone[i] = true
                    val cx = x(i) + 0.5f; val cy = y(i) + 0.5f
                    particles.burst(cx, cy, Neon.Orange, 16, 6f, 0.14f, 0.6f)
                    particles.burst(cx, cy, Neon.Red, 10, 4f, 0.2f, 0.5f)
                    if (i != exploded && played < 1) {
                        Audio.play(Sfx.Brick, rate = 0.55f + Random.nextFloat() * 0.2f, volume = 0.6f)
                        played++
                    }
                    if (i != exploded) shake = max(shake, 0.25f)
                }
            }
        }
        if (!showResult && finished) {
            val delay = if (phase == MinesPhase.WON) 1.2f else 3.2f
            if (clock - endTime > delay) showResult = true
        }
        shake = max(0f, shake - dt * 2.2f)
        particles.update(dt)
        frame++
    }
}

// ---------- Disegno condiviso ----------

val NumberColors = listOf(
    Color.Transparent, Neon.Cyan, Neon.Green, Neon.Pink, Neon.Purple,
    Neon.Orange, Neon.Teal, Neon.Yellow, Color.White,
)

private val TileTop = Color(0xFF5141B5)
private val TileBottom = Color(0xFF2B2274)
private val TileSide = Color(0xFF120C3A)

/** Casella coperta: un piccolo tasto in rilievo. */
fun DrawScope.drawHiddenTile(topLeft: Offset, cell: Float, highlight: Float = 0f) {
    val g = cell * 0.05f
    val w = cell - 2 * g
    val d = cell * 0.09f
    val fh = w - d
    val tl = topLeft + Offset(g, g)
    val r = CornerRadius(cell * 0.18f)
    drawRoundRect(Color.Black, tl + Offset(0f, d + cell * 0.025f), Size(w, fh), r, alpha = 0.35f)
    drawRoundRect(TileSide, tl + Offset(0f, d), Size(w, fh), r)
    drawRoundRect(Brush.verticalGradient(listOf(TileTop, TileBottom), tl.y, tl.y + fh), tl, Size(w, fh), r)
    val i = cell * 0.04f
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0f)), tl.y + i, tl.y + fh * 0.5f),
        tl + Offset(i, i), Size(w - 2 * i, fh * 0.46f), CornerRadius(cell * 0.14f),
    )
    drawRoundRect(Neon.Cyan, tl, Size(w, fh), r, alpha = 0.22f + highlight * 0.5f, style = Stroke(cell * 0.025f))
}

/** Casella scoperta: incavata, con un piccolo "pop" quando appare. */
fun DrawScope.drawRevealedTile(topLeft: Offset, cell: Float, anim: Float, danger: Boolean = false) {
    val g = cell * 0.05f
    val scale = 0.7f + 0.3f * com.giochiclassici.arcade.ui.easeOutBack(anim)
    val s = (cell - 2 * g) * scale
    val c = topLeft + Offset(cell / 2, cell / 2)
    val tl = c - Offset(s / 2, s / 2)
    val r = CornerRadius(cell * 0.16f)
    drawRoundRect(if (danger) Color(0xFF7A0F2A) else Color(0xFF0D0A27), tl, Size(s, s), r)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), tl.y, tl.y + s * 0.35f),
        tl, Size(s, s * 0.35f), r,
    )
    drawRoundRect(Color.White, tl, Size(s, s), r, alpha = 0.07f, style = Stroke(cell * 0.02f))
    if (anim < 1f) drawRoundRect(Neon.Cyan, tl, Size(s, s), r, alpha = (1f - anim) * 0.45f)
}

fun DrawScope.drawFlag(center: Offset, cell: Float, time: Float) {
    val h = cell * 0.56f
    val base = center + Offset(-cell * 0.1f, h / 2)
    val top = base - Offset(0f, h)
    drawLine(Neon.Text, base, top, cell * 0.06f, StrokeCap.Round)
    drawLine(Neon.Text, base + Offset(-cell * 0.16f, 0f), base + Offset(cell * 0.2f, 0f), cell * 0.06f, StrokeCap.Round)
    val wave = sin(time * 6f) * cell * 0.04f
    val p = Path().apply {
        moveTo(top.x, top.y)
        quadraticTo(top.x + cell * 0.2f, top.y + cell * 0.06f + wave, top.x + cell * 0.38f, top.y + cell * 0.14f + wave)
        quadraticTo(top.x + cell * 0.2f, top.y + cell * 0.22f - wave, top.x, top.y + cell * 0.3f)
        close()
    }
    drawCircle(
        Brush.radialGradient(listOf(Neon.Pink.copy(alpha = 0.45f), Color.Transparent), top + Offset(cell * 0.15f, cell * 0.15f), cell * 0.45f),
        cell * 0.45f, top + Offset(cell * 0.15f, cell * 0.15f),
    )
    drawPath(p, Neon.Pink)
}

fun DrawScope.drawMine(center: Offset, cell: Float, time: Float) {
    val r = cell * 0.22f
    for (k in 0 until 8) {
        val a = k * PI.toFloat() / 4f
        drawLine(Color(0xFF2A2440), center, center + Offset(cos(a), sin(a)) * (r * 1.55f), cell * 0.07f, StrokeCap.Round)
    }
    glowCircle(Color(0xFF2A2440), center, r, cell * 0.35f, 0f)
    drawCircle(Neon.Red, r * 0.35f, center, alpha = 0.6f + 0.4f * sin(time * 10f))
    drawCircle(Color.White, r * 0.22f, center + Offset(-r * 0.4f, -r * 0.4f), alpha = 0.6f)
}

fun DrawScope.drawCross(center: Offset, cell: Float) {
    val r = cell * 0.3f
    drawLine(Neon.Red, center - Offset(r, r), center + Offset(r, r), cell * 0.08f, StrokeCap.Round)
    drawLine(Neon.Red, center + Offset(r, -r), center + Offset(-r, r), cell * 0.08f, StrokeCap.Round)
}
