package com.giochiclassici.arcade.games.pong

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class PongMode { CPU, LOCAL }
enum class PongDifficulty(val label: String, val speed: Float, val reaction: Float, val error: Float) {
    EASY("Facile", 420f, 0.22f, 130f),
    NORMAL("Media", 620f, 0.14f, 75f),
    HARD("Difficile", 850f, 0.08f, 32f),
}
enum class PongPhase { READY, SERVING, PLAYING, PAUSED, OVER }
enum class PongEvent { PADDLE, WALL, POINT, WIN }
data class PongPoint(val x: Float, val y: Float)

/** Logica indipendente dal rendering: coordinate fisse, fisica a piccoli passi e CPU limitata. */
class PongGame(
    private val random: Random = Random.Default,
    private val onEvent: (PongEvent) -> Unit = {},
) {
    companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 1400f
        const val PADDLE_X = 72f
        const val PADDLE_WIDTH = 26f
        const val PADDLE_HEIGHT = 220f
        const val BALL_RADIUS = 17f
        const val WALL = 28f
        const val WIN_SCORE = 11
    }

    var mode = PongMode.CPU
        private set
    var difficulty = PongDifficulty.NORMAL
        private set
    var phase = PongPhase.READY
        private set
    var leftScore = 0
        private set
    var rightScore = 0
        private set
    var leftY = HEIGHT / 2
        private set
    var rightY = HEIGHT / 2
        private set
    var ballX = WIDTH / 2
        private set
    var ballY = HEIGHT / 2
        private set
    var velocityX = 0f
        private set
    var velocityY = 0f
        private set
    var serveTime = 0f
        private set
    var rally = 0
        private set
    var lastScorer: Int? = null
        private set
    var flash = 0f
        private set
    val trail = ArrayDeque<PongPoint>()
    private var previousPhase = PongPhase.SERVING
    private var serveDirection = 1f
    private var cpuTimer = 0f
    private var cpuTarget = HEIGHT / 2

    val countdown: Int get() = ceil(serveTime).toInt().coerceAtLeast(1)
    val leftWon: Boolean get() = leftScore >= WIN_SCORE

    fun start(mode: PongMode = this.mode, difficulty: PongDifficulty = this.difficulty) {
        this.mode = mode
        this.difficulty = difficulty
        leftScore = 0
        rightScore = 0
        leftY = HEIGHT / 2
        rightY = HEIGHT / 2
        lastScorer = null
        flash = 0f
        serveDirection = if (random.nextBoolean()) 1f else -1f
        prepareServe(3f)
    }

    fun setup() { phase = PongPhase.READY; trail.clear() }

    fun pause() {
        if (phase == PongPhase.PLAYING || phase == PongPhase.SERVING) {
            previousPhase = phase
            phase = PongPhase.PAUSED
        }
    }

    fun resume() {
        if (phase == PongPhase.PAUSED) phase = previousPhase
    }

    fun movePaddle(left: Boolean, y: Float) {
        if (phase != PongPhase.PLAYING && phase != PongPhase.SERVING) return
        if (!left && mode == PongMode.CPU) return
        val clamped = y.coerceIn(WALL + PADDLE_HEIGHT / 2, HEIGHT - WALL - PADDLE_HEIGHT / 2)
        if (left) leftY = clamped else rightY = clamped
    }

    fun update(dt: Float) {
        if (phase != PongPhase.PLAYING && phase != PongPhase.SERVING) return
        val elapsed = dt.coerceIn(0f, 0.05f)
        if (elapsed == 0f) return
        flash = (flash - elapsed * 2f).coerceAtLeast(0f)
        if (phase == PongPhase.SERVING) {
            serveTime -= elapsed
            if (serveTime <= 0f) {
                val angle = (random.nextFloat() - 0.5f) * 1.1f
                velocityX = cos(angle) * 700f * serveDirection
                velocityY = sin(angle) * 700f
                phase = PongPhase.PLAYING
            }
            return
        }

        if (mode == PongMode.CPU) updateCpu(elapsed)
        // A 240 Hz la pallina non può attraversare una racchetta fra due controlli.
        val steps = ceil(elapsed * 240f).toInt().coerceAtLeast(1)
        val step = elapsed / steps
        repeat(steps) {
            if (phase != PongPhase.PLAYING) return@repeat
            val oldX = ballX
            ballX += velocityX * step
            ballY += velocityY * step
            val minY = WALL + BALL_RADIUS
            val maxY = HEIGHT - WALL - BALL_RADIUS
            if (ballY < minY) {
                ballY = 2 * minY - ballY
                velocityY = abs(velocityY)
                onEvent(PongEvent.WALL)
            } else if (ballY > maxY) {
                ballY = 2 * maxY - ballY
                velocityY = -abs(velocityY)
                onEvent(PongEvent.WALL)
            }
            val leftFace = PADDLE_X + PADDLE_WIDTH / 2 + BALL_RADIUS
            val rightFace = WIDTH - leftFace
            if (velocityX < 0 && oldX >= leftFace && ballX <= leftFace &&
                abs(ballY - leftY) <= PADDLE_HEIGHT / 2 + BALL_RADIUS) {
                ballX = leftFace
                bounce(leftY, 1f)
            } else if (velocityX > 0 && oldX <= rightFace && ballX >= rightFace &&
                abs(ballY - rightY) <= PADDLE_HEIGHT / 2 + BALL_RADIUS) {
                ballX = rightFace
                bounce(rightY, -1f)
            }
            if (ballX < -BALL_RADIUS) point(left = false)
            else if (ballX > WIDTH + BALL_RADIUS) point(left = true)
        }
        if (phase == PongPhase.PLAYING) {
            trail.addLast(PongPoint(ballX, ballY))
            while (trail.size > 14) trail.removeFirst()
        }
    }

    private fun bounce(paddleY: Float, direction: Float) {
        val relative = ((ballY - paddleY) / (PADDLE_HEIGHT / 2)).coerceIn(-1f, 1f)
        val speed = (700f + (rally + 1) * 38f).coerceAtMost(1450f)
        val angle = relative * 1.02f
        velocityX = direction * cos(angle) * speed
        val vertical = sin(angle) * speed
        velocityY = if (abs(vertical) < 85f) {
            if (velocityY < 0f) -85f else 85f
        } else vertical
        rally++
        onEvent(PongEvent.PADDLE)
    }

    private fun point(left: Boolean) {
        if (left) leftScore++ else rightScore++
        lastScorer = if (left) 1 else 2
        flash = 1f
        if (leftScore >= WIN_SCORE || rightScore >= WIN_SCORE) {
            phase = PongPhase.OVER
            trail.clear()
            onEvent(PongEvent.WIN)
        } else {
            onEvent(PongEvent.POINT)
            // Il servizio successivo va verso chi ha appena perso il punto.
            serveDirection = if (left) 1f else -1f
            prepareServe(2f)
        }
    }

    private fun prepareServe(seconds: Float) {
        ballX = WIDTH / 2
        ballY = HEIGHT / 2
        velocityX = 0f
        velocityY = 0f
        rally = 0
        serveTime = seconds
        cpuTimer = 0f
        cpuTarget = HEIGHT / 2
        trail.clear()
        phase = PongPhase.SERVING
    }

    private fun updateCpu(dt: Float) {
        cpuTimer -= dt
        if (cpuTimer <= 0f) {
            cpuTimer = difficulty.reaction
            cpuTarget = if (velocityX > 0f) {
                val target = if (difficulty == PongDifficulty.EASY) ballY else {
                    val time = ((WIDTH - PADDLE_X - PADDLE_WIDTH / 2 - BALL_RADIUS - ballX) / velocityX)
                        .coerceAtLeast(0f)
                    val range = HEIGHT - 2 * (WALL + BALL_RADIUS)
                    val unfolded = ballY + velocityY * time - WALL - BALL_RADIUS
                    val wrapped = ((unfolded % (2 * range)) + 2 * range) % (2 * range)
                    WALL + BALL_RADIUS + if (wrapped <= range) wrapped else 2 * range - wrapped
                }
                target + (random.nextFloat() * 2 - 1) * difficulty.error
            } else HEIGHT / 2
            cpuTarget = cpuTarget.coerceIn(WALL + PADDLE_HEIGHT / 2, HEIGHT - WALL - PADDLE_HEIGHT / 2)
        }
        val maxMove = difficulty.speed * dt
        rightY += (cpuTarget - rightY).coerceIn(-maxMove, maxMove)
    }
}
