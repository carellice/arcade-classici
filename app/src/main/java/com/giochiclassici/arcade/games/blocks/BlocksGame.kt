package com.giochiclassici.arcade.games.blocks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Sfx
import com.giochiclassici.arcade.core.Haptics
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.ParticleSystem
import kotlin.math.max
import kotlin.math.pow

enum class BlPhase { READY, PLAYING, PAUSED, OVER }

/** I sette pezzi: celle nella rotazione iniziale, dentro un riquadro size×size. */
enum class Piece(val color: Color, val size: Int, val cells: List<Pair<Int, Int>>) {
    I(Neon.Cyan, 4, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)),
    O(Neon.Yellow, 2, listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)),
    T(Neon.Purple, 3, listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)),
    S(Neon.Green, 3, listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1)),
    Z(Neon.Red, 3, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1)),
    J(Neon.Teal, 3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1)),
    L(Neon.Orange, 3, listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1));

    /** Celle (x, y) per la rotazione r (0..3, in senso orario). */
    fun shape(r: Int): List<Pair<Int, Int>> {
        var c = cells
        repeat(((r % 4) + 4) % 4) { c = c.map { (x, y) -> (size - 1 - y) to x } }
        return c
    }
}

class BlocksGame(val cols: Int = 10, val rows: Int = 20) {
    /** Colore di ogni cella del campo (null = vuota). */
    val grid = Array(rows) { arrayOfNulls<Color>(cols) }

    var score by mutableIntStateOf(0)
        private set
    var lines by mutableIntStateOf(0)
        private set
    var level by mutableIntStateOf(1)
        private set
    var phase by mutableStateOf(BlPhase.READY)
    var frame by mutableLongStateOf(0L)
        private set
    var next by mutableStateOf(Piece.T)
        private set
    /** Diventa true poco dopo il game over, per mostrare il pannello finale. */
    var showResult by mutableStateOf(false)
        private set

    var piece = Piece.T
        private set
    var px = 0
        private set
    var py = 0
        private set
    var rot = 0
        private set

    var clock = 0f
        private set
    var shake = 0f
        private set
    /** Righe in via di cancellazione e tempo residuo dell'animazione. */
    var clearing: List<Int> = emptyList()
        private set
    var clearT = 0f
        private set
    /** Scritta temporanea dopo una cancellazione ("TETRIS!", "×3"…). */
    var banner by mutableStateOf<String?>(null)
        private set
    var bannerT = 0f
        private set
    /** Lampo dell'ultimo pezzo posato. */
    var lockFlash = 0f
        private set

    val particles = ParticleSystem(600)

    private val bag = ArrayList<Piece>()
    private var fallAcc = 0f
    private var lockT = 0f
    private var lockResets = 0
    private var overTime = 0f
    private var lockedCells: List<Pair<Int, Int>> = emptyList()

    init { restart() }

    val gravity get() = max(0.045f, 0.8f * 0.84f.pow(level - 1))

    fun restart() {
        for (r in grid) r.fill(null)
        score = 0; lines = 0; level = 1
        clearing = emptyList(); clearT = 0f
        banner = null; bannerT = 0f
        showResult = false
        particles.clear()
        bag.clear()
        next = draw()
        spawn()
        phase = BlPhase.READY
    }

    fun start() {
        if (phase == BlPhase.READY) {
            phase = BlPhase.PLAYING
            Audio.play(Sfx.Start)
        }
    }

    fun pause() { if (phase == BlPhase.PLAYING) phase = BlPhase.PAUSED }
    fun resume() { if (phase == BlPhase.PAUSED) phase = BlPhase.PLAYING }

    private val canAct get() = phase == BlPhase.PLAYING && clearing.isEmpty()

    /** Sacchetto da 7: ogni pezzo esce una volta per giro. */
    private fun draw(): Piece {
        if (bag.isEmpty()) bag += Piece.entries.shuffled()
        return bag.removeAt(bag.size - 1)
    }

    private fun spawn() {
        piece = next
        next = draw()
        rot = 0
        px = (cols - piece.size) / 2
        py = if (piece == Piece.I) -1 else 0
        fallAcc = 0f; lockT = 0f; lockResets = 0
        if (!fits(px, py, rot)) gameOver()
    }

    fun cells(x: Int = px, y: Int = py, r: Int = rot) = piece.shape(r).map { (cx, cy) -> (x + cx) to (y + cy) }

    private fun fits(x: Int, y: Int, r: Int): Boolean {
        for ((cx, cy) in piece.shape(r)) {
            val gx = x + cx; val gy = y + cy
            if (gx < 0 || gx >= cols || gy >= rows) return false
            if (gy >= 0 && grid[gy][gx] != null) return false
        }
        return true
    }

    /** Riga su cui atterrerebbe il pezzo (per l'ombra). */
    fun ghostY(): Int {
        var y = py
        while (fits(px, y + 1, rot)) y++
        return y
    }

    private val grounded get() = !fits(px, py + 1, rot)

    private fun onMoved() {
        if (grounded && lockResets < 15) { lockT = 0f; lockResets++ }
    }

    fun move(dx: Int): Boolean {
        if (!canAct || !fits(px + dx, py, rot)) return false
        px += dx
        Audio.play(Sfx.Move, rate = 1f + (px % 3) * 0.05f)
        Haptics.tick()
        onMoved()
        return true
    }

    fun rotate() {
        if (!canAct || piece == Piece.O) return
        val nr = (rot + 1) % 4
        // Piccoli spostamenti ("wall kick") per ruotare vicino ai bordi
        val kicks = if (piece == Piece.I) listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1)
        else listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, -1 to -1, 1 to -1)
        for ((kx, ky) in kicks) {
            if (fits(px + kx, py + ky, nr)) {
                px += kx; py += ky; rot = nr
                Audio.play(Sfx.Rotate)
                Haptics.tick()
                onMoved()
                return
            }
        }
    }

    fun hardDrop() {
        if (!canAct) return
        val gy = ghostY()
        val dist = gy - py
        score += dist * 2
        for ((cx, cy) in cells(px, gy)) {
            for (k in 0 until 3) particles.emit(cx + 0.5f, cy + 0.2f - k * 0.8f, 0f, -3f - k * 2f, piece.color, 0.18f, 0.3f)
        }
        py = gy
        shake = 0.5f
        lock()
    }

    fun update(dt: Float, frozen: Boolean) {
        clock += dt
        frame++
        particles.update(dt)
        shake = max(0f, shake - dt * 3f)
        lockFlash = max(0f, lockFlash - dt * 4f)
        if (bannerT > 0f) { bannerT -= dt; if (bannerT <= 0f) banner = null }
        if (phase == BlPhase.OVER && !showResult && clock - overTime > 1.2f) showResult = true
        if (frozen || phase != BlPhase.PLAYING) return

        if (clearing.isNotEmpty()) {
            clearT -= dt
            if (clearT <= 0f) collapse()
            return
        }

        val interval = gravity
        if (grounded) {
            lockT += dt
            if (lockT >= 0.5f) lock()
            return
        }
        fallAcc += dt
        while (fallAcc >= interval && !grounded) {
            fallAcc -= interval
            py++
        }
    }

    private fun lock() {
        val placed = cells()
        for ((x, y) in placed) {
            if (y < 0) { gameOver(); return }
            grid[y][x] = piece.color
        }
        lockedCells = placed
        lockFlash = 1f
        Audio.play(Sfx.Lock)
        Haptics.bump()
        val full = (0 until rows).filter { r -> grid[r].all { it != null } }
        if (full.isNotEmpty()) {
            clearing = full
            clearT = 0.38f
            val n = full.size
            score += intArrayOf(0, 100, 300, 500, 800)[n] * level
            banner = when (n) { 4 -> "TETRIS!"; 3 -> "TRIPLA!"; 2 -> "DOPPIA!"; else -> null }
            bannerT = 1.3f
            Audio.play(if (n == 4) Sfx.Tetris else Sfx.Line, rate = 1f + (n - 1) * 0.08f)
            if (n == 4) { shake = 1f; Haptics.heavy() }
            for (r in full) for (c in 0 until cols) {
                particles.burst(c + 0.5f, r + 0.5f, grid[r][c] ?: Neon.Text, 5, 9f, 0.16f, 0.7f, gravity = 14f)
            }
        } else {
            spawn()
        }
    }

    private fun collapse() {
        val keep = (0 until rows).filter { it !in clearing }.map { grid[it].copyOf() }
        val empty = rows - keep.size
        for (r in 0 until rows) {
            if (r < empty) grid[r].fill(null) else keep[r - empty].copyInto(grid[r])
        }
        val before = level
        lines += clearing.size
        level = lines / 10 + 1
        clearing = emptyList()
        if (level > before) {
            banner = "LIVELLO $level"
            bannerT = 1.6f
            Audio.play(Sfx.LevelClear)
        }
        spawn()
    }

    private fun gameOver() {
        if (phase == BlPhase.OVER) return
        phase = BlPhase.OVER
        overTime = clock
        shake = 1f
        Audio.play(Sfx.Die)
        Haptics.heavy()
        for (r in 0 until rows) for (c in 0 until cols) {
            val col = grid[r][c] ?: continue
            if ((r + c) % 2 == 0) particles.burst(c + 0.5f, r + 0.5f, col, 2, 6f, 0.15f, 1.1f, gravity = 18f)
        }
    }

    fun isLocked(x: Int, y: Int) = lockFlash > 0f && (x to y) in lockedCells
}
