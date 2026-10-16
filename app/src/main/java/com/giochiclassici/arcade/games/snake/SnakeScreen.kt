package com.giochiclassici.arcade.games.snake

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.core.AppState
import com.giochiclassici.arcade.core.Services
import com.giochiclassici.arcade.ui.GameBackground
import com.giochiclassici.arcade.ui.GameLoop
import com.giochiclassici.arcade.ui.GameTopBar
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.NeonText
import com.giochiclassici.arcade.ui.ResultPanel
import com.giochiclassici.arcade.ui.StatBox
import com.giochiclassici.arcade.ui.TutorialOverlay
import com.giochiclassici.arcade.ui.glowRoundRect
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val BEST_KEY = "best_snake"
private const val COLS = 15

@Composable
fun SnakeScreen(onExit: () -> Unit) {
    val prefs = Services.prefs
    val accent = Neon.Green
    var showTutorial by remember { mutableStateOf(!prefs.tutorialSeen("snake")) }
    var best by remember { mutableStateOf(prefs.best(BEST_KEY)) }
    var newRecord by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        GameBackground(accent)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val density = LocalDensity.current
            val areaW = with(density) { (maxWidth - 24.dp).toPx() }
            val areaH = with(density) { (maxHeight - 64.dp - 28.dp).toPx() }
            val cell = floor(areaW / COLS)
            val rows = min(30, (areaH / cell).toInt())
            val game = remember(rows) { SnakeGame(COLS, rows) }

            val fg by AppState.foreground
            LaunchedEffect(fg) { if (!fg) game.pause() }
            GameLoop { dt -> game.update(dt, frozen = showTutorial) }
            LaunchedEffect(game.phase) {
                if (game.phase == SnakePhase.OVER) {
                    newRecord = prefs.submitHigh(BEST_KEY, game.score)
                    best = prefs.best(BEST_KEY)
                }
            }
            BackHandler {
                if (game.phase == SnakePhase.PLAYING) game.pause() else onExit()
            }

            Column(Modifier.fillMaxSize()) {
                GameTopBar(
                    accent = accent,
                    onBack = onExit,
                    onHelp = { game.pause(); showTutorial = true },
                    onPause = { game.pause() },
                ) {
                    StatBox("Punti", game.score.toString(), accent)
                    StatBox("Livello", game.level.toString(), Neon.Cyan)
                    StatBox("Record", maxOf(best, game.score).toString(), Neon.Yellow)
                }
                Box(Modifier.fillMaxSize().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                    val wDp = with(density) { (cell * COLS).toDp() }
                    val hDp = with(density) { (cell * rows).toDp() }
                    Box(
                        Modifier
                            .size(wDp, hDp)
                            .pointerInput(game) {
                                // Un solo gestore per swipe (anche multipli nello stesso tocco) e tap
                                val threshold = 22.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var anchor = down.position
                                    var moved = false
                                    while (true) {
                                        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) {
                                            if (!moved) game.start()
                                            break
                                        }
                                        val d = ch.position - anchor
                                        if (abs(d.x) > threshold || abs(d.y) > threshold) {
                                            game.turn(
                                                if (abs(d.x) > abs(d.y)) Cell(if (d.x > 0) 1 else -1, 0)
                                                else Cell(0, if (d.y > 0) 1 else -1)
                                            )
                                            anchor = ch.position
                                            moved = true
                                        }
                                        ch.consume()
                                    }
                                }
                            },
                    ) {
                        SnakeBoard(game, cell)
                    }
                    if (game.phase == SnakePhase.READY && !showTutorial) ReadyHint()
                }
            }

            ResultPanel(
                visible = game.phase == SnakePhase.PAUSED && !showTutorial,
                accent = accent,
                title = "PAUSA",
                lines = listOf("Punti" to game.score.toString(), "Livello" to game.level.toString()),
                highlight = null,
                primary = "Riprendi" to { game.resume() },
                tertiary = "Ricomincia" to { game.reset() },
                secondary = "Menu" to onExit,
            )
            ResultPanel(
                visible = game.showResult,
                accent = Neon.Pink,
                title = "GAME OVER",
                lines = listOf("Punti" to game.score.toString(), "Record" to best.toString()),
                highlight = if (newRecord) "★ NUOVO RECORD! ★" else null,
                primary = "Riprova" to { newRecord = false; game.reset() },
                secondary = "Menu" to onExit,
            )
            TutorialOverlay(
                visible = showTutorial,
                accent = accent,
                gameTitle = "SNAKE",
                pages = snakeTutorial(),
                onDone = { showTutorial = false; prefs.markTutorialSeen("snake") },
            )
        }
    }
}

@Composable
private fun ReadyHint() {
    val a by rememberInfiniteTransition(label = "hint").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a",
    )
    NeonText(
        "SCORRI PER INIZIARE",
        Neon.Text,
        18.sp,
        Modifier.graphicsLayer { alpha = a }.padding(top = 140.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SnakeBoard(game: SnakeGame, cell: Float) {
    Canvas(Modifier.fillMaxSize()) {
        game.frame // lettura per ridisegnare ad ogni frame
        val sh = game.shake * cell * 0.4f
        val sx = if (sh > 0f) (Random.nextFloat() - 0.5f) * sh else 0f
        val sy = if (sh > 0f) (Random.nextFloat() - 0.5f) * sh else 0f
        val w = cell * game.cols
        val h = cell * game.rows
        val t = game.time
        translate(sx, sy) {
            drawSnakeBoard(w, h, cell, t, game.eatPulse)
            clipRect(0f, 0f, w, h) {
                fun center(c: Cell) = Offset((c.x + 0.5f) * cell, (c.y + 0.5f) * cell)
                // Frutto appena mangiato: si rimpicciolisce mentre la testa lo raggiunge
                game.eatenFood?.let { drawApple(center(it), cell, t, 1f - game.progress) }
                drawApple(center(game.food), cell, t, popIn(t - game.foodBorn))
                game.bonus?.let { b ->
                    val left = game.bonusLeft / game.bonusDuration
                    val blink = left > 0.3f || sin(t * 30f) > 0f
                    if (blink) drawStar(center(b), cell, t, left)
                }
                val dead = game.phase == SnakePhase.OVER
                val since = t - game.deathTime
                val visible = !dead || since > 0.8f || ((since * 8f).toInt() % 2 == 0)
                if (visible) {
                    val pts = game.polyline().map { it * cell }
                    drawSnake(pts, cell, t, dead, game.bulges, Size(w, h))
                }
                game.particles.draw(this, cell)
            }
        }
    }
}
