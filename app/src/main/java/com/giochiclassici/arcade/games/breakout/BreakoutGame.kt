package com.giochiclassici.arcade.games.breakout

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Sfx
import com.giochiclassici.arcade.core.Haptics
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.ParticleSystem
import com.giochiclassici.arcade.ui.glowCircle
import com.giochiclassici.arcade.ui.glowRoundRect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class BPhase { PLAYING, PAUSED, CLEAR, OVER }

enum class PowerType(val label: String, val color: Color) {
    WIDE("W", Neon.Cyan),
    MULTI("M", Neon.Pink),
    SLOW("S", Neon.Green),
    FIRE("F", Neon.Orange),
    LIFE("♥", Neon.Yellow),
}

class Brick(
    val x: Float, val y: Float, val w: Float, val h: Float,
    var hp: Int, val maxHp: Int, val steel: Boolean, val color: Color, val appearAt: Float,
) {
    var alive = true
    var flash = 0f
    val cx get() = x + w / 2
    val cy get() = y + h / 2
}

class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    val trail = ArrayDeque<Offset>()
}

class PowerUp(var x: Float, var y: Float, val type: PowerType)

private val LEVELS = listOf(
    listOf(
        "1111111111",
        "1111111111",
        "1111111111",
        "1111111111",
        "1111111111",
    ),
    listOf(
        "....22....",
        "...1111...",
        "..111111..",
        ".11122111.",
        "..111111..",
        "...1111...",
        "....22....",
    ),
    listOf(
        "..1....1..",
        "...1..1...",
        "..222222..",
        ".22.22.22.",
        "2222222222",
        "2.222222.2",
        "2.2....2.2",
        "...22.22..",
    ),
    listOf(
        "3333333333",
        "1111111111",
        "#.##..##.#",
        "1111111111",
        "2222222222",
        "..#....#..",
    ),
    listOf(
        ".33....33.",
        "3223..3223",
        "2222222222",
        "2211111122",
        ".21111112.",
        "..211112..",
        "...2112...",
        "....33....",
    ),
    listOf(
        "3.3.3.3.3.",
        ".2.2.2.2.2",
        "1.1.1.1.1.",
        ".1.1.1.1.1",
        "2.2.2.2.2.",
        ".3.3.3.3.3",
        "##......##",
    ),
)

private val ROW_COLORS = listOf(Neon.Pink, Neon.Orange, Neon.Yellow, Neon.Green, Neon.Cyan, Neon.Purple, Neon.Pink, Neon.Orange)

class BreakoutGame(val worldH: Float) {
    val W = 1000f
    val ballR = 13f
    val paddleH = 24f
    val paddleY = worldH - 110f

    var score by mutableIntStateOf(0)
        private set
    var lives by mutableIntStateOf(3)
        private set
    var level by mutableIntStateOf(1)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var phase by mutableStateOf(BPhase.PLAYING)
    var attached by mutableStateOf(true)
        private set
    var frame by mutableLongStateOf(0L)
        private set

    var paddleX = W / 2
        private set
    var paddleW = 170f
        private set
    var paddleBounce = 0f
        private set

    val bricks = ArrayList<Brick>()
    val balls = ArrayList<Ball>()
    val powerUps = ArrayList<PowerUp>()
    val particles = ParticleSystem(900)

    var clock = 0f
        private set
    var shake = 0f
        private set
    var wideT = 0f
        private set
    var slowT = 0f
        private set
    var fireT = 0f
        private set
    var clearTimer = 0f
        private set
    var overTime = 0f
        private set
    /** Diventa true poco dopo il game over, per mostrare il pannello finale. */
    var showResult by mutableStateOf(false)
        private set
    private var speedMul = 1f
    private var baseSpeed = 640f

    init { loadLevel() }

    val speed get() = baseSpeed * speedMul * (if (slowT > 0f) 0.68f else 1f)

    fun restart() {
        score = 0; lives = 3; level = 1; combo = 0
        phase = BPhase.PLAYING
        showResult = false
        particles.clear()
        loadLevel()
    }

    private fun loadLevel() {
        bricks.clear(); balls.clear(); powerUps.clear()
        val pattern = LEVELS[(level - 1) % LEVELS.size]
        val loops = (level - 1) / LEVELS.size
        baseSpeed = min(950f, 640f + (level - 1) * 30f + loops * 40f)
        val margin = 30f; val gap = 8f; val cols = 10
        val bw = (W - 2 * margin - (cols - 1) * gap) / cols
        val bh = 36f
        val top = 70f
        for ((r, row) in pattern.withIndex()) {
            for ((c, ch) in row.withIndex()) {
                if (ch == '.') continue
                val steel = ch == '#'
                var hp = if (steel) 1 else ch.digitToInt()
                if (!steel && loops > 0) hp = min(3, hp + loops)
                bricks += Brick(
                    margin + c * (bw + gap), top + r * (bh + gap), bw, bh,
                    hp, hp, steel, ROW_COLORS[r % ROW_COLORS.size],
                    clock + 0.15f + r * 0.07f + c * 0.02f,
                )
            }
        }
        resetBall()
        wideT = 0f; slowT = 0f; fireT = 0f
    }

    private fun resetBall() {
        balls.clear()
        balls += Ball(paddleX, paddleY - paddleH / 2 - ballR, 0f, 0f)
        attached = true
        speedMul = 1f
        combo = 0
    }

    fun movePaddle(dx: Float) {
        if (phase != BPhase.PLAYING) return
        paddleX = (paddleX + dx).coerceIn(paddleW / 2, W - paddleW / 2)
    }

    fun launch() {
        if (phase != BPhase.PLAYING || !attached) return
        attached = false
        val a = Math.toRadians((Random.nextFloat() * 40f - 20f).toDouble()).toFloat()
        val b = balls.first()
        b.vx = speed * sin(a); b.vy = -speed * cos(a)
        Audio.play(Sfx.Paddle, rate = 1.3f)
    }

    fun pause() { if (phase == BPhase.PLAYING) phase = BPhase.PAUSED }
    fun resume() { if (phase == BPhase.PAUSED) phase = BPhase.PLAYING }

    fun update(dt: Float, frozen: Boolean) {
        clock += dt
        particles.update(dt)
        shake = max(0f, shake - dt * 3f)
        paddleBounce = max(0f, paddleBounce - dt * 6f)
        for (b in bricks) if (b.flash > 0f) b.flash = max(0f, b.flash - dt * 5f)
        frame++
        if (phase == BPhase.OVER && !showResult && clock - overTime > 1.2f) showResult = true
        if (frozen || phase == BPhase.PAUSED || phase == BPhase.OVER) return

        if (phase == BPhase.CLEAR) {
            clearTimer -= dt
            if (clearTimer <= 0f) {
                level++
                phase = BPhase.PLAYING
                loadLevel()
            }
            return
        }

        val targetW = if (wideT > 0f) 270f else 170f
        paddleW += (targetW - paddleW) * min(1f, dt * 10f)
        paddleX = paddleX.coerceIn(paddleW / 2, W - paddleW / 2)
        wideT = max(0f, wideT - dt)
        slowT = max(0f, slowT - dt)
        fireT = max(0f, fireT - dt)

        if (attached) {
            val b = balls.first()
            b.x = paddleX; b.y = paddleY - paddleH / 2 - ballR
        } else {
            updateBalls(dt)
        }
        updatePowerUps(dt)

        if (bricks.none { it.alive && !it.steel }) levelClear()
    }

    private fun updateBalls(dt: Float) {
        val sp = speed
        val iter = balls.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            // Normalizza la velocità
            val len = sqrt(b.vx * b.vx + b.vy * b.vy).coerceAtLeast(1f)
            b.vx = b.vx / len * sp; b.vy = b.vy / len * sp
            val steps = max(1, ceil(sp * dt / 6f).toInt())
            val h = dt / steps
            repeat(steps) { stepBall(b, h) }
            b.trail.addFirst(Offset(b.x, b.y))
            while (b.trail.size > 10) b.trail.removeLast()
            if (fireT > 0f) {
                particles.emit(
                    b.x + (Random.nextFloat() - 0.5f) * 10f, b.y + (Random.nextFloat() - 0.5f) * 10f,
                    (Random.nextFloat() - 0.5f) * 60f, -40f - Random.nextFloat() * 60f,
                    if (Random.nextBoolean()) Neon.Orange else Neon.Yellow, 7f, 0.35f,
                )
            }
            if (b.y - ballR > worldH) iter.remove()
        }
        if (balls.isEmpty()) loseLife()
    }

    private fun stepBall(b: Ball, h: Float) {
        b.x += b.vx * h; b.y += b.vy * h
        if (b.x < ballR) { b.x = ballR; b.vx = abs(b.vx); wallHit() }
        if (b.x > W - ballR) { b.x = W - ballR; b.vx = -abs(b.vx); wallHit() }
        if (b.y < ballR) { b.y = ballR; b.vy = abs(b.vy); wallHit() }

        // Racchetta
        val top = paddleY - paddleH / 2
        if (b.vy > 0 && b.y + ballR >= top && b.y - ballR <= paddleY + paddleH / 2 &&
            b.x >= paddleX - paddleW / 2 - ballR && b.x <= paddleX + paddleW / 2 + ballR
        ) {
            val rel = ((b.x - paddleX) / (paddleW / 2)).coerceIn(-1f, 1f)
            val angle = rel * 1.08f // ~62°
            val sp = speed
            b.vx = sp * sin(angle); b.vy = -sp * cos(angle)
            b.y = top - ballR
            combo = 0
            paddleBounce = 1f
            Audio.play(Sfx.Paddle, rate = 1f + rel * 0.12f)
            Haptics.tick()
            particles.burst(b.x, top, Neon.Cyan, 6, 160f, 5f, 0.3f)
        }

        // Mattoni
        for (br in bricks) {
            if (!br.alive) continue
            val nx = b.x.coerceIn(br.x, br.x + br.w)
            val ny = b.y.coerceIn(br.y, br.y + br.h)
            val dx = b.x - nx; val dy = b.y - ny
            if (dx * dx + dy * dy >= ballR * ballR) continue
            val pierce = fireT > 0f && !br.steel
            hitBrick(br)
            if (!pierce) {
                val ox = min(b.x + ballR - br.x, br.x + br.w - (b.x - ballR))
                val oy = min(b.y + ballR - br.y, br.y + br.h - (b.y - ballR))
                if (ox < oy) {
                    b.vx = if (b.x < br.cx) -abs(b.vx) else abs(b.vx)
                    b.x += if (b.x < br.cx) -ox else ox
                } else {
                    b.vy = if (b.y < br.cy) -abs(b.vy) else abs(b.vy)
                    b.y += if (b.y < br.cy) -oy else oy
                }
            }
            break
        }
        // Evita traiettorie quasi orizzontali
        val sp = speed
        if (abs(b.vy) < sp * 0.28f) {
            val s = if (b.vy == 0f) -1f else sign(b.vy)
            b.vy = s * sp * 0.28f
            b.vx = sign(b.vx) * sqrt(max(0f, sp * sp - b.vy * b.vy))
        }
    }

    private fun wallHit() = Audio.play(Sfx.Wall, volume = 0.6f)

    private fun hitBrick(br: Brick) {
        br.flash = 1f
        if (br.steel) {
            Audio.play(Sfx.BrickHard)
            particles.burst(br.cx, br.cy, Color(0xFFB8C0D0), 5, 150f, 4f, 0.3f)
            return
        }
        val dmg = if (fireT > 0f) br.hp else 1
        br.hp -= dmg
        speedMul = min(1.3f, speedMul + 0.006f)
        if (br.hp > 0) {
            Audio.play(Sfx.BrickHard, rate = 1.25f)
            particles.burst(br.cx, br.cy, br.color, 6, 160f, 5f, 0.35f)
            return
        }
        br.alive = false
        combo++
        score += 10 * br.maxHp + (combo - 1) * 5
        Audio.play(Sfx.Brick, rate = 1f + min(combo, 14) * 0.055f)
        particles.burst(br.cx, br.cy, br.color, 22, 420f, 7f, 0.6f, gravity = 500f, drag = 2f)
        particles.burst(br.cx, br.cy, Color.White, 6, 200f, 4f, 0.25f)
        shake = max(shake, 0.15f)
        if (Random.nextFloat() < 0.14f) {
            val t = if (Random.nextFloat() < 0.1f) PowerType.LIFE
            else listOf(PowerType.WIDE, PowerType.MULTI, PowerType.SLOW, PowerType.FIRE).random()
            powerUps += PowerUp(br.cx, br.cy, t)
        }
    }

    private fun updatePowerUps(dt: Float) {
        val it = powerUps.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.y += 230f * dt
            val caught = p.y + 18f >= paddleY - paddleH / 2 && p.y - 18f <= paddleY + paddleH / 2 &&
                abs(p.x - paddleX) <= paddleW / 2 + 30f
            if (caught) {
                apply(p.type)
                particles.burst(p.x, paddleY, p.type.color, 24, 300f, 6f, 0.6f)
                it.remove()
            } else if (p.y > worldH + 40f) it.remove()
        }
    }

    private fun apply(t: PowerType) {
        Audio.play(Sfx.PowerUp)
        Haptics.bump()
        score += 25
        when (t) {
            PowerType.WIDE -> wideT = 15f
            PowerType.SLOW -> slowT = 10f
            PowerType.FIRE -> fireT = 8f
            PowerType.LIFE -> lives = min(9, lives + 1)
            PowerType.MULTI -> {
                if (attached) launch()
                val extra = ArrayList<Ball>()
                for (b in balls) {
                    for (ang in listOf(-0.45f, 0.45f)) {
                        if (balls.size + extra.size >= 12) break
                        val c = cos(ang); val s = sin(ang)
                        extra += Ball(b.x, b.y, b.vx * c - b.vy * s, b.vx * s + b.vy * c)
                    }
                }
                balls += extra
            }
        }
    }

    private fun loseLife() {
        lives--
        shake = 1f
        Haptics.heavy()
        powerUps.clear()
        if (lives <= 0) {
            phase = BPhase.OVER
            overTime = clock
            Audio.play(Sfx.Die)
            for (br in bricks) if (br.alive) particles.burst(br.cx, br.cy, br.color, 4, 200f, 6f, 1.2f, gravity = 700f)
        } else {
            Audio.play(Sfx.LifeLost)
            wideT = 0f; slowT = 0f; fireT = 0f
            resetBall()
        }
    }

    private fun levelClear() {
        phase = BPhase.CLEAR
        clearTimer = 2.4f
        score += 100 * level
        balls.clear(); powerUps.clear()
        Audio.play(Sfx.LevelClear)
        Haptics.bump()
        val colors = listOf(Neon.Cyan, Neon.Pink, Neon.Yellow, Neon.Green, Neon.Purple, Neon.Orange)
        repeat(8) { k ->
            particles.burst(
                100f + Random.nextFloat() * 800f, 150f + Random.nextFloat() * worldH * 0.4f,
                colors[k % colors.size], 30, 520f, 7f, 1.3f, gravity = 380f, drag = 1.4f,
            )
        }
    }
}

// ---------- Disegno condiviso ----------

fun DrawScope.drawBrick(br: Brick, yOffset: Float = 0f, alpha: Float = 1f) {
    val depth = br.h * 0.2f
    val tl = Offset(br.x, br.y + yOffset)
    val s = Size(br.w, br.h - depth)
    val r = br.h * 0.26f
    // Ombra e fianco: danno spessore al mattone
    drawRoundRect(Color.Black, tl + Offset(0f, depth + 3f), s, CornerRadius(r), alpha = 0.35f * alpha)
    val sideColor = if (br.steel) Color(0xFF353B4A) else lerp(br.color, Color.Black, 0.55f)
    drawRoundRect(sideColor, tl + Offset(0f, depth), s, CornerRadius(r), alpha = alpha)
    if (br.steel) {
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFC9D1E0), Color(0xFF5B6478)), tl.y, tl.y + s.height),
            tl, s, CornerRadius(r), alpha = alpha,
        )
        drawRoundRect(Color.White, tl, s, CornerRadius(r), alpha = 0.35f * alpha, style = Stroke(2f))
        for (dx in listOf(0.15f, 0.85f)) drawCircle(Color(0xFF3A4152), 3.5f, tl + Offset(s.width * dx, s.height / 2), alpha = alpha)
    } else {
        val dmg = 1f - br.hp / br.maxHp.toFloat()
        val base = lerp(br.color, Color(0xFF2A2050), dmg * 0.45f)
        glowRoundRect(
            base, tl, s, r, glow = 9f, intensity = 0.22f * alpha,
            fill = Brush.verticalGradient(listOf(lerp(base, Color.White, 0.3f), base, lerp(base, Color.Black, 0.25f)), tl.y, tl.y + s.height),
        )
        drawRoundRect(Color.White, tl + Offset(s.width * 0.08f, s.height * 0.12f), Size(s.width * 0.84f, s.height * 0.16f), CornerRadius(r), alpha = 0.3f * alpha)
        // Indicatori di resistenza
        for (k in 1 until br.hp) {
            val inset = 5f + (k - 1) * 5f
            drawRoundRect(
                Color.White, tl + Offset(inset, inset), Size(s.width - 2 * inset, s.height - 2 * inset),
                CornerRadius(r * 0.6f), alpha = 0.45f * alpha, style = Stroke(2f),
            )
        }
        if (dmg > 0f) {
            val p = Path().apply {
                moveTo(tl.x + s.width * 0.3f, tl.y)
                lineTo(tl.x + s.width * 0.42f, tl.y + s.height * 0.45f)
                lineTo(tl.x + s.width * 0.34f, tl.y + s.height * 0.6f)
                lineTo(tl.x + s.width * 0.48f, tl.y + s.height)
                moveTo(tl.x + s.width * 0.42f, tl.y + s.height * 0.45f)
                lineTo(tl.x + s.width * 0.62f, tl.y + s.height * 0.35f)
            }
            drawPath(p, Color.Black, alpha = 0.5f * alpha, style = Stroke(2.5f))
        }
    }
    if (br.flash > 0f) drawRoundRect(Color.White, tl, s, CornerRadius(r), alpha = br.flash * 0.7f * alpha)
}

fun DrawScope.drawPaddle(cx: Float, cy: Float, w: Float, h: Float, bounce: Float = 0f, color: Color = Neon.Cyan) {
    val sq = 1f - bounce * 0.15f
    val hh = h * sq
    val ww = w * (1f + bounce * 0.05f)
    val tl = Offset(cx - ww / 2, cy - hh / 2 + (h - hh))
    drawRoundRect(Color.Black, tl + Offset(0f, h * 0.55f + 4f), Size(ww, hh), CornerRadius(hh / 2), alpha = 0.4f)
    drawRoundRect(lerp(color, Color.Black, 0.6f), tl + Offset(0f, h * 0.4f), Size(ww, hh), CornerRadius(hh / 2))
    glowRoundRect(
        color, tl, Size(ww, hh), hh / 2, glow = 16f, intensity = 0.35f,
        fill = Brush.verticalGradient(listOf(Color.White, color, lerp(color, Neon.Bg0, 0.4f)), tl.y, tl.y + hh),
    )
    drawRoundRect(Neon.Pink, Offset(cx - ww / 2 + 6f, tl.y + hh * 0.35f), Size(14f, hh * 0.3f), CornerRadius(4f))
    drawRoundRect(Neon.Pink, Offset(cx + ww / 2 - 20f, tl.y + hh * 0.35f), Size(14f, hh * 0.3f), CornerRadius(4f))
}

fun DrawScope.drawBall(b: Offset, r: Float, trail: List<Offset>, fire: Boolean) {
    val c = if (fire) Neon.Orange else Color.White
    val glow = if (fire) Neon.Yellow else Neon.Cyan
    for ((i, p) in trail.withIndex()) {
        val a = 1f - i / trail.size.toFloat()
        drawCircle(glow, r * (0.3f + 0.7f * a), p, alpha = a * 0.25f)
    }
    glowCircle(glow, b, r * 1.05f, r * 1.8f, 0.5f)
    drawCircle(c, r * 0.85f, b)
}

fun DrawScope.drawCapsule(center: Offset, type: PowerType, time: Float) {
    val w = 64f; val h = 30f
    val tl = center - Offset(w / 2, h / 2)
    val pulse = 0.5f + 0.5f * sin(time * 8f)
    glowRoundRect(
        type.color, tl, Size(w, h), h / 2, glow = 10f + 6f * pulse, intensity = 0.35f,
        fill = Brush.verticalGradient(listOf(Color.White, type.color, lerp(type.color, Neon.Bg0, 0.5f)), tl.y, tl.y + h),
    )
}
