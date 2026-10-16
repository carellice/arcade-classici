package com.giochiclassici.arcade.games.snake

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.lerp
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Sfx
import com.giochiclassici.arcade.core.Haptics
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.ParticleSystem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.random.Random

enum class SnakePhase { READY, PLAYING, PAUSED, OVER }

data class Cell(val x: Int, val y: Int)

/**
 * Snake classico: il campo non ha muri (si rientra dal lato opposto),
 * si perde solo mordendosi la coda.
 */
class SnakeGame(val cols: Int, val rows: Int) {
    val body = ArrayList<Cell>()
    private var prev: List<Cell> = emptyList()
    var dir = Cell(1, 0)
        private set
    private var lastDir = dir
    private val queue = ArrayDeque<Cell>()

    var food = Cell(0, 0)
        private set
    var foodBorn = 0f
        private set
    /** Frutto appena mangiato: resta visibile mentre la testa lo raggiunge. */
    var eatenFood: Cell? = null
        private set
    var bonus: Cell? = null
        private set
    var bonusLeft = 0f
        private set
    val bonusDuration = 6f

    /** Rigonfiamenti del pasto che scorrono lungo il corpo (distanza dalla testa in celle). */
    val bulges = ArrayList<Float>()

    private var eaten = 0
    private var interval = START_INTERVAL
    private var acc = 0f

    val particles = ParticleSystem()
    var score by mutableIntStateOf(0)
        private set
    var level by mutableIntStateOf(1)
        private set
    var phase by mutableStateOf(SnakePhase.READY)
    var frame by mutableLongStateOf(0L)
        private set
    /** Diventa true poco dopo la morte, per mostrare il pannello finale. */
    var showResult by mutableStateOf(false)
        private set

    var time = 0f
        private set
    var shake = 0f
        private set
    var deathTime = 0f
        private set
    var eatPulse = 0f
        private set

    /** Avanzamento tra un passo e il successivo (0..1), per il movimento continuo. */
    val progress get() = if (phase == SnakePhase.PLAYING || phase == SnakePhase.PAUSED) (acc / interval).coerceIn(0f, 1f) else 1f

    init { reset() }

    fun reset() {
        body.clear()
        val cy = rows / 2
        val cx = cols / 2
        for (i in 0 until 4) body += Cell(cx - i, cy)
        prev = body.toList()
        dir = Cell(1, 0); lastDir = dir
        queue.clear()
        eaten = 0; interval = START_INTERVAL; acc = 0f
        score = 0; level = 1
        bonus = null; eatenFood = null
        bulges.clear()
        particles.clear()
        spawnFood()
        phase = SnakePhase.READY
        showResult = false
    }

    fun turn(d: Cell) {
        if (phase == SnakePhase.READY) {
            if (d.x == -dir.x && d.y == -dir.y) return
            start()
        }
        if (phase != SnakePhase.PLAYING) return
        val last = queue.lastOrNull() ?: lastDir
        if (d == last || (d.x == -last.x && d.y == -last.y)) return
        if (queue.size < 3) queue.addLast(d)
    }

    fun start() {
        if (phase == SnakePhase.READY) {
            phase = SnakePhase.PLAYING
            Audio.play(Sfx.Start)
        }
    }

    fun pause() { if (phase == SnakePhase.PLAYING) phase = SnakePhase.PAUSED }
    fun resume() { if (phase == SnakePhase.PAUSED) phase = SnakePhase.PLAYING }

    private fun wrap(x: Int, y: Int) = Cell((x + cols) % cols, (y + rows) % rows)

    private fun spawnFood() {
        val free = ArrayList<Cell>()
        for (y in 0 until rows) for (x in 0 until cols) {
            val c = Cell(x, y)
            if (c !in body && c != bonus) free += c
        }
        if (free.isNotEmpty()) food = free.random()
        foodBorn = time
    }

    private fun spawnBonus() {
        repeat(200) {
            val c = Cell(Random.nextInt(cols), Random.nextInt(rows))
            if (c !in body && c != food) { bonus = c; bonusLeft = bonusDuration; return }
        }
    }

    fun update(dt: Float, frozen: Boolean) {
        time += dt
        shake = max(0f, shake - dt * 2.5f)
        eatPulse = max(0f, eatPulse - dt * 3f)
        particles.update(dt)
        if (phase == SnakePhase.OVER && !showResult && time - deathTime > 1.0f) showResult = true
        if (phase == SnakePhase.PLAYING && !frozen) {
            bonus?.let {
                bonusLeft -= dt
                if (bonusLeft <= 0f) {
                    particles.burst(it.x + 0.5f, it.y + 0.5f, Neon.Yellow.copy(alpha = 0.6f), 10, 3f)
                    bonus = null
                }
            }
            val adv = dt / interval
            for (i in bulges.indices) bulges[i] += adv
            bulges.removeAll { it > body.size + 1 }
            acc += dt
            while (acc >= interval && phase == SnakePhase.PLAYING) {
                acc -= interval
                step()
            }
        }
        frame++
    }

    private fun step() {
        eatenFood = null
        queue.removeFirstOrNull()?.let { dir = it }
        lastDir = dir
        val head = body[0]
        val nh = wrap(head.x + dir.x, head.y + dir.y)
        val grows = nh == food
        val hitsSelf = body.subList(0, if (grows) body.size else body.size - 1).any { it == nh }
        if (hitsSelf) {
            die()
            return
        }
        prev = body.toList()
        body.add(0, nh)
        if (grows) {
            eaten++
            score += 10 * level
            eatPulse = 1f
            eatenFood = nh
            bulges.add(-1f)
            particles.burst(nh.x + 0.5f, nh.y + 0.5f, Neon.Pink, 16, 6f, 0.12f, 0.5f)
            Audio.play(Sfx.Eat, rate = 1f + (level - 1) * 0.04f)
            Haptics.tick()
            interval = max(MIN_INTERVAL, interval * 0.965f)
            level = 1 + eaten / 5
            if (eaten % 5 == 0 && bonus == null) spawnBonus()
            spawnFood()
        } else {
            body.removeAt(body.size - 1)
        }
        if (nh == bonus) {
            score += 50 * level
            bonus = null
            particles.burst(nh.x + 0.5f, nh.y + 0.5f, Neon.Yellow, 30, 9f, 0.16f, 0.8f)
            Audio.play(Sfx.Bonus)
            Haptics.bump()
        }
    }

    private fun die() {
        phase = SnakePhase.OVER
        deathTime = time
        shake = 1f
        prev = body.toList()
        acc = 0f
        for ((i, c) in body.withIndex()) {
            val col = lerp(SnakeColors.Head, SnakeColors.Tail, i / max(1f, body.size - 1f))
            particles.burst(c.x + 0.5f, c.y + 0.5f, col, 5, 5f, 0.13f, 0.9f)
        }
        Audio.play(Sfx.Die)
        Haptics.heavy()
    }

    /** Porta [p] vicino a [ref] tenendo conto dell'attraversamento dei bordi. */
    private fun near(p: Offset, ref: Offset): Offset {
        val dx = round((ref.x - p.x) / cols) * cols
        val dy = round((ref.y - p.y) / rows) * rows
        return Offset(p.x + dx, p.y + dy)
    }

    private fun c(cell: Cell) = Offset(cell.x + 0.5f, cell.y + 0.5f)

    /**
     * Linea centrale del serpente in coordinate di cella, dalla testa alla coda, "srotolata"
     * attraverso i bordi. La testa avanza e la coda si ritira in modo continuo: il corpo non scatta.
     */
    fun polyline(): List<Offset> {
        val t = progress
        val pts = ArrayList<Offset>(body.size + 2)
        val from = c(prev.firstOrNull() ?: body[0])
        val to = near(c(body[0]), from)
        pts += lerp(from, to, t)
        var last = pts[0]
        for (i in 1 until body.size) {
            val p = near(c(body[i]), last)
            if (abs(p.x - last.x) + abs(p.y - last.y) > 0.001f) pts += p
            last = p
        }
        val tailFrom = near(c(prev.lastOrNull() ?: body.last()), last)
        val tailEnd = lerp(tailFrom, last, t)
        if (abs(tailEnd.x - last.x) + abs(tailEnd.y - last.y) > 0.001f) pts += tailEnd
        return pts
    }

    companion object {
        const val START_INTERVAL = 0.16f
        const val MIN_INTERVAL = 0.065f
    }
}
