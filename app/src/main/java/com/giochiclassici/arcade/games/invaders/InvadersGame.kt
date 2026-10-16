package com.giochiclassici.arcade.games.invaders

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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class IPhase { READY, PLAYING, PAUSED, WAVE_CLEAR, OVER }

enum class IPower(val label: String, val color: Color, val title: String) {
    TRIPLE("T", Neon.Cyan, "TRIPLO!"),
    RAPID("R", Neon.Orange, "RAFFICA!"),
    SHIELD("S", Neon.Green, "SCUDO!"),
}

class Shot(var x: Float, var y: Float, val vx: Float, val vy: Float, val enemy: Boolean) {
    var alive = true
    val seed = Random.nextFloat() * 6f
}

class Drop(var x: Float, var y: Float, val type: IPower)
class Popup(val x: Float, var y: Float, val text: String, val color: Color, var life: Float = 1.2f)
class Blast(val x: Float, val y: Float, val color: Color, val size: Float, var life: Float = 0.35f)

/** Invasori: logica in unità "mondo" (larghezza 1000). */
class InvadersGame(val worldH: Float) {
    val W = 1000f

    // --- Formazione ---
    val cols = 8
    val rows = 5
    val slotW = 78f
    val spacingX = 96f
    val spacingY = 70f
    val alive = BooleanArray(cols * rows)
    private val baseX = (W - ((cols - 1) * spacingX + slotW)) / 2f
    private var baseY = 150f
    var fx = 0f
        private set
    var fy = 0f
        private set
    private var fdir = 1f
    var animFrame = 0
        private set
    private var stepTimer = 0f
    private var marchNote = 0
    private val marchRates = floatArrayOf(1f, 0.94f, 0.89f, 0.84f)

    // --- Giocatore ---
    val shipY = worldH - 150f
    val shipW = SHIP.w * PIXEL
    val shipH = SHIP.h * PIXEL
    var shipX = W / 2
        private set
    var shipAlive = true
        private set
    private var respawnT = 0f
    var invulnT = 0f
        private set
    var bubble = false
        private set
    var bubbleT = 0f
        private set
    var tripleT = 0f
        private set
    var rapidT = 0f
        private set
    private var fireCd = 0.4f

    // --- Scudi ---
    val shieldCell = 6f
    val shieldCols = 18
    val shieldRows = 11
    val shieldY = shipY - 210f
    val shieldX = FloatArray(4)
    val shieldCells = BooleanArray(4 * shieldCols * shieldRows)
    var shieldVersion = 0
        private set

    // --- UFO ---
    var ufoActive = false
        private set
    var ufoX = 0f
        private set
    val ufoY = 78f
    private var ufoDir = 1f
    private var ufoTimer = 18f
    private var ufoSound = 0f

    // --- Oggetti ---
    val shots = ArrayList<Shot>()
    val drops = ArrayList<Drop>()
    val popups = ArrayList<Popup>()
    val blasts = ArrayList<Blast>()
    val particles = ParticleSystem(900)
    private var alienFireT = 1.5f

    // --- Stato ---
    var score by mutableIntStateOf(0)
        private set
    var lives by mutableIntStateOf(3)
        private set
    var wave by mutableIntStateOf(1)
        private set
    var phase by mutableStateOf(IPhase.READY)
    var frame by mutableLongStateOf(0L)
        private set
    var showResult by mutableStateOf(false)
        private set
    var invaded = false
        private set
    var clock = 0f
        private set
    var shake = 0f
        private set
    private var bannerT = 0f
    private var overTime = 0f

    init {
        val gap = (W - 4 * shieldCols * shieldCell) / 5f
        for (i in 0 until 4) shieldX[i] = gap + i * (shieldCols * shieldCell + gap)
        setupWave()
    }

    fun type(row: Int) = when (row) { 0 -> 0; 1, 2 -> 1; else -> 2 }
    fun points(row: Int) = when (type(row)) { 0 -> 30; 1 -> 20; else -> 10 }
    fun alienCenterX(col: Int) = baseX + col * spacingX + fx + slotW / 2
    fun alienTop(row: Int) = baseY + row * spacingY + fy
    fun aliveCount() = alive.count { it }

    private fun setupWave() {
        alive.fill(true)
        baseY = 150f + min(wave - 1, 5) * 26f
        fx = 0f; fy = 0f; fdir = 1f
        stepTimer = 0f; animFrame = 0
        shots.clear(); drops.clear()
        ufoActive = false
        ufoTimer = 14f + Random.nextFloat() * 10f
        alienFireT = 1.6f
        resetShields()
    }

    private fun resetShields() {
        for (i in 0 until 4) for (cy in 0 until shieldRows) for (cx in 0 until shieldCols) {
            var on = true
            if (cy < 3 && (cx < 3 - cy || cx > shieldCols - 1 - (3 - cy))) on = false
            if (cy >= 7 && cx in 6..11) on = false
            if (cy == 6 && cx in 7..10) on = false
            shieldCells[(i * shieldRows + cy) * shieldCols + cx] = on
        }
        shieldVersion++
    }

    fun restart() {
        score = 0; lives = 3; wave = 1
        shipX = W / 2; shipAlive = true; invulnT = 0f
        bubble = false; bubbleT = 0f; tripleT = 0f; rapidT = 0f
        invaded = false; showResult = false
        particles.clear(); popups.clear(); blasts.clear()
        setupWave()
        phase = IPhase.READY
    }

    fun start() {
        if (phase == IPhase.READY) {
            phase = IPhase.PLAYING
            Audio.play(Sfx.Start)
        }
    }

    fun pause() { if (phase == IPhase.PLAYING) phase = IPhase.PAUSED }
    fun resume() { if (phase == IPhase.PAUSED) phase = IPhase.PLAYING }

    fun moveShip(dx: Float) {
        if (phase != IPhase.PLAYING && phase != IPhase.WAVE_CLEAR) return
        shipX = (shipX + dx).coerceIn(shipW / 2 + 10f, W - shipW / 2 - 10f)
    }

    fun update(dt: Float, frozen: Boolean) {
        clock += dt
        frame++
        particles.update(dt)
        shake = max(0f, shake - dt * 3f)
        for (b in blasts) b.life -= dt
        blasts.removeAll { it.life <= 0f }
        for (p in popups) { p.life -= dt; p.y -= 60f * dt }
        popups.removeAll { it.life <= 0f }
        if (phase == IPhase.OVER) {
            if (!showResult && clock - overTime > 1.4f) showResult = true
            return
        }
        if (frozen || phase == IPhase.PAUSED || phase == IPhase.READY) return

        if (phase == IPhase.WAVE_CLEAR) {
            updateShots(dt)
            bannerT -= dt
            if (bannerT <= 0f) {
                wave++
                setupWave()
                phase = IPhase.PLAYING
            }
            return
        }

        tripleT = max(0f, tripleT - dt)
        rapidT = max(0f, rapidT - dt)
        if (bubble) { bubbleT -= dt; if (bubbleT <= 0f) bubble = false }
        invulnT = max(0f, invulnT - dt)
        if (!shipAlive && lives > 0) {
            respawnT -= dt
            if (respawnT <= 0f) { shipAlive = true; invulnT = 2f; shipX = W / 2 }
        }

        updateFormation(dt)
        if (phase != IPhase.PLAYING) return
        updateFiring(dt)
        updateShots(dt)
        updateUfo(dt)
        updateDrops(dt)

        if (aliveCount() == 0) waveClear()
    }

    private fun updateFormation(dt: Float) {
        val a = aliveCount()
        if (a == 0) return
        val frac = 1f - a / (cols * rows).toFloat()
        var speed = 28f + wave * 7f + 330f * frac * frac
        if (a == 1) speed *= 1.5f
        fx += fdir * speed * dt
        var minCol = cols; var maxCol = -1; var maxRow = -1
        for (i in alive.indices) if (alive[i]) {
            val c = i % cols; val r = i / cols
            minCol = min(minCol, c); maxCol = max(maxCol, c); maxRow = max(maxRow, r)
        }
        val left = baseX + minCol * spacingX + fx
        val right = baseX + maxCol * spacingX + fx + slotW
        if (fdir > 0 && right > W - 14f) { fx -= right - (W - 14f); fdir = -1f; fy += 26f }
        else if (fdir < 0 && left < 14f) { fx += 14f - left; fdir = 1f; fy += 26f }

        // Passo di marcia: animazione + suono che accelera
        stepTimer -= dt
        if (stepTimer <= 0f) {
            stepTimer = max(0.075f, 0.62f * a / (cols * rows))
            animFrame = 1 - animFrame
            Audio.play(Sfx.March, rate = marchRates[marchNote], volume = 0.75f)
            marchNote = (marchNote + 1) % 4
        }

        // Gli alieni sgretolano gli scudi e possono invadere
        val bottom = alienTop(maxRow) + ALIEN_H
        if (bottom > shieldY) {
            for (i in alive.indices) if (alive[i]) {
                val c = i % cols; val r = i / cols
                val hw = SPRITES[type(r)].w * PIXEL / 2
                eraseRect(alienCenterX(c) - hw, alienTop(r), alienCenterX(c) + hw, alienTop(r) + ALIEN_H)
            }
        }
        if (bottom >= shipY - shipH / 2) {
            invaded = true
            lives = 0
            gameOver()
        }
    }

    private fun updateFiring(dt: Float) {
        // Giocatore: fuoco automatico
        if (shipAlive) {
            fireCd -= dt
            val maxShots = if (rapidT > 0f) 7 else 3
            val mine = shots.count { !it.enemy }
            if (fireCd <= 0f && mine < maxShots) {
                val y = shipY - shipH / 2
                val sp = 1350f
                shots += Shot(shipX, y, 0f, -sp, false)
                if (tripleT > 0f) {
                    shots += Shot(shipX - 20f, y + 10f, -sin(0.2f) * sp, -cos(0.2f) * sp, false)
                    shots += Shot(shipX + 20f, y + 10f, sin(0.2f) * sp, -cos(0.2f) * sp, false)
                }
                fireCd = if (rapidT > 0f) 0.15f else 0.42f
                Audio.play(Sfx.Laser, rate = 0.95f + Random.nextFloat() * 0.1f, volume = 0.45f)
                particles.burst(shipX, y, Neon.Cyan, 3, 120f, 4f, 0.15f)
            }
        }
        // Alieni
        alienFireT -= dt
        val maxEnemy = min(7, 3 + wave / 2)
        if (alienFireT <= 0f && shots.count { it.enemy } < maxEnemy) {
            val colsAlive = (0 until cols).filter { c -> (0 until rows).any { r -> alive[r * cols + c] } }
            if (colsAlive.isNotEmpty()) {
                // Spesso mira alla colonna più vicina alla navicella
                val c = if (Random.nextFloat() < 0.4f) colsAlive.minBy { abs(alienCenterX(it) - shipX) } else colsAlive.random()
                val r = (rows - 1 downTo 0).first { alive[it * cols + c] }
                shots += Shot(alienCenterX(c), alienTop(r) + ALIEN_H, 0f, min(660f, 380f + wave * 28f), true)
            }
            val frac = 1f - aliveCount() / (cols * rows).toFloat()
            alienFireT = (0.5f + Random.nextFloat() * 0.8f) * max(0.4f, 1f - (wave - 1) * 0.08f) / (1f + frac * 0.6f)
        }
    }

    private fun updateShots(dt: Float) {
        for (s in shots) {
            if (!s.alive) continue
            val steps = max(1, ceil(abs(s.vy) * dt / 5f).toInt())
            val h = dt / steps
            for (k in 0 until steps) {
                s.x += s.vx * h; s.y += s.vy * h
                if (s.y < -30f || s.y > worldH + 30f || s.x < -30f || s.x > W + 30f) { s.alive = false; break }
                if (hitShield(s.x, s.y, s.enemy)) { s.alive = false; break }
                if (s.enemy) {
                    if (hitShip(s)) { s.alive = false; break }
                } else if (phase == IPhase.PLAYING && hitByPlayer(s)) { s.alive = false; break }
            }
        }
        // Colpi che si scontrano a mezz'aria
        for (p in shots) {
            if (!p.alive || p.enemy) continue
            for (e in shots) {
                if (!e.alive || !e.enemy) continue
                if (abs(p.x - e.x) < 12f && abs(p.y - e.y) < 18f) {
                    p.alive = false; e.alive = false
                    particles.burst(e.x, e.y, Neon.Yellow, 10, 260f, 5f, 0.3f)
                    Audio.play(Sfx.ShieldHit, rate = 1.5f)
                    break
                }
            }
        }
        shots.removeAll { !it.alive }
    }

    private fun hitByPlayer(s: Shot): Boolean {
        for (i in alive.indices) {
            if (!alive[i]) continue
            val c = i % cols; val r = i / cols
            val hw = SPRITES[type(r)].w * PIXEL / 2
            val cx = alienCenterX(c); val top = alienTop(r)
            if (abs(s.x - cx) < hw && s.y > top && s.y < top + ALIEN_H) {
                killAlien(i)
                return true
            }
        }
        if (ufoActive && abs(s.x - ufoX) < UFO.w * PIXEL / 2 && abs(s.y - (ufoY + UFO.h * PIXEL / 2)) < UFO.h * PIXEL / 2 + 6f) {
            killUfo()
            return true
        }
        return false
    }

    private fun killAlien(i: Int) {
        alive[i] = false
        val c = i % cols; val r = i / cols
        val cx = alienCenterX(c); val cy = alienTop(r) + ALIEN_H / 2
        val col = SPRITES[type(r)].main
        score += points(r)
        blasts += Blast(cx, cy, col, 60f)
        particles.burst(cx, cy, col, 20, 420f, 7f, 0.55f, gravity = 300f, drag = 2f)
        particles.burst(cx, cy, Color.White, 6, 220f, 4f, 0.25f)
        Audio.play(Sfx.Invader, rate = 0.9f + Random.nextFloat() * 0.25f, volume = 0.8f)
        Haptics.tick()
        shake = max(shake, 0.12f)
        if (Random.nextFloat() < 0.07f && drops.size < 2) drops += Drop(cx, cy, IPower.entries.random())
    }

    private fun killUfo() {
        ufoActive = false
        val pts = listOf(50, 100, 150, 300).random()
        score += pts
        val cy = ufoY + UFO.h * PIXEL / 2
        popups += Popup(ufoX, cy, "+$pts", Neon.Yellow)
        blasts += Blast(ufoX, cy, Neon.Red, 110f, 0.5f)
        particles.burst(ufoX, cy, Neon.Red, 30, 520f, 8f, 0.8f, gravity = 250f)
        particles.burst(ufoX, cy, Neon.Yellow, 20, 380f, 6f, 0.6f)
        Audio.play(Sfx.Bonus)
        Audio.play(Sfx.Invader, rate = 0.7f)
        Haptics.bump()
        ufoTimer = 18f + Random.nextFloat() * 12f
    }

    private fun hitShip(s: Shot): Boolean {
        if (!shipAlive || invulnT > 0f) return false
        if (abs(s.x - shipX) > shipW * 0.45f || s.y < shipY - shipH / 2 || s.y > shipY + shipH / 2) return false
        if (bubble) {
            bubble = false
            invulnT = 0.6f
            particles.burst(shipX, shipY, Neon.Green, 26, 380f, 6f, 0.5f)
            Audio.play(Sfx.BrickHard)
            Haptics.bump()
            return true
        }
        lives--
        shipAlive = false
        respawnT = 1.6f
        shake = 1f
        tripleT = 0f; rapidT = 0f
        blasts += Blast(shipX, shipY, Neon.Cyan, 140f, 0.6f)
        particles.burst(shipX, shipY, Neon.Cyan, 40, 560f, 8f, 1f, gravity = 300f)
        particles.burst(shipX, shipY, Neon.Orange, 30, 420f, 7f, 0.8f)
        // Siamo dentro il ciclo sui proiettili: li marchiamo, verranno rimossi a fine ciclo
        for (o in shots) if (o.enemy) o.alive = false
        Audio.play(Sfx.Boom)
        Haptics.heavy()
        if (lives <= 0) gameOver()
        return true
    }

    private fun gameOver() {
        if (phase == IPhase.OVER) return
        phase = IPhase.OVER
        overTime = clock
        shipAlive = false
        if (invaded) {
            blasts += Blast(shipX, shipY, Neon.Red, 200f, 0.8f)
            particles.burst(shipX, shipY, Neon.Red, 50, 600f, 9f, 1.2f, gravity = 300f)
            Audio.play(Sfx.Boom)
            Haptics.heavy()
            shake = 1f
        }
        Audio.play(Sfx.Die)
    }

    private fun shieldIndexAt(x: Float, y: Float): Int {
        if (y < shieldY || y >= shieldY + shieldRows * shieldCell) return -1
        for (i in 0 until 4) {
            val lx = x - shieldX[i]
            if (lx >= 0f && lx < shieldCols * shieldCell) {
                val cx = (lx / shieldCell).toInt()
                val cy = ((y - shieldY) / shieldCell).toInt()
                return (i * shieldRows + cy) * shieldCols + cx
            }
        }
        return -1
    }

    private fun hitShield(x: Float, y: Float, enemy: Boolean): Boolean {
        val idx = shieldIndexAt(x, y)
        if (idx < 0 || !shieldCells[idx]) return false
        val i = idx / (shieldRows * shieldCols)
        val rem = idx % (shieldRows * shieldCols)
        val cy = rem / shieldCols; val cx = rem % shieldCols
        val r = if (enemy) 2.4f else 1.8f
        for (dy in -3..3) for (dx in -3..3) {
            val nx = cx + dx; val ny = cy + (if (enemy) dy + 1 else dy - 1)
            if (nx !in 0 until shieldCols || ny !in 0 until shieldRows) continue
            if (dx * dx + dy * dy <= r * r * (0.5f + Random.nextFloat() * 0.7f)) {
                shieldCells[(i * shieldRows + ny) * shieldCols + nx] = false
            }
        }
        shieldCells[idx] = false
        shieldVersion++
        particles.burst(x, y, Neon.Green, 8, 200f, 4f, 0.35f)
        Audio.play(Sfx.ShieldHit, volume = 0.6f)
        return true
    }

    private fun eraseRect(x0: Float, y0: Float, x1: Float, y1: Float) {
        var changed = false
        for (i in 0 until 4) for (cy in 0 until shieldRows) for (cx in 0 until shieldCols) {
            val idx = (i * shieldRows + cy) * shieldCols + cx
            if (!shieldCells[idx]) continue
            val x = shieldX[i] + (cx + 0.5f) * shieldCell
            val y = shieldY + (cy + 0.5f) * shieldCell
            if (x in x0..x1 && y in y0..y1) { shieldCells[idx] = false; changed = true }
        }
        if (changed) shieldVersion++
    }

    private fun updateUfo(dt: Float) {
        if (ufoActive) {
            ufoX += ufoDir * 230f * dt
            ufoSound -= dt
            if (ufoSound <= 0f) { Audio.play(Sfx.Ufo, volume = 0.7f); ufoSound = 0.2f }
            if (ufoX < -100f || ufoX > W + 100f) {
                ufoActive = false
                ufoTimer = 18f + Random.nextFloat() * 12f
            }
        } else {
            ufoTimer -= dt
            if (ufoTimer <= 0f && aliveCount() >= 8) {
                ufoActive = true
                ufoDir = if (Random.nextBoolean()) 1f else -1f
                ufoX = if (ufoDir > 0) -80f else W + 80f
            }
        }
    }

    private fun updateDrops(dt: Float) {
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.y += 260f * dt
            if (shipAlive && abs(d.x - shipX) < shipW / 2 + 22f && abs(d.y - shipY) < shipH / 2 + 18f) {
                when (d.type) {
                    IPower.TRIPLE -> tripleT = 10f
                    IPower.RAPID -> rapidT = 10f
                    IPower.SHIELD -> { bubble = true; bubbleT = 12f }
                }
                popups += Popup(shipX, shipY - 70f, d.type.title, d.type.color)
                particles.burst(d.x, d.y, d.type.color, 24, 320f, 6f, 0.6f)
                Audio.play(Sfx.PowerUp)
                Haptics.bump()
                score += 25
                it.remove()
            } else if (d.y > worldH + 40f) it.remove()
        }
    }

    private fun waveClear() {
        phase = IPhase.WAVE_CLEAR
        bannerT = 2.4f
        score += 100 * wave
        drops.clear()
        ufoActive = false
        shots.removeAll { it.enemy }
        Audio.play(Sfx.LevelClear)
        Haptics.bump()
        val colors = listOf(Neon.Cyan, Neon.Pink, Neon.Yellow, Neon.Green, Neon.Purple)
        repeat(7) { k ->
            particles.burst(
                100f + Random.nextFloat() * 800f, 150f + Random.nextFloat() * worldH * 0.35f,
                colors[k % colors.size], 28, 520f, 7f, 1.3f, gravity = 380f, drag = 1.4f,
            )
        }
    }

    companion object {
        const val PIXEL = 6.5f
        val ALIEN_H = 8 * PIXEL
    }
}
