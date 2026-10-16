package com.giochiclassici.arcade.games.breakout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.giochiclassici.arcade.ui.easeOutCubic
import kotlin.random.Random

private const val BEST_KEY = "best_breakout"

@Composable
fun BreakoutScreen(onExit: () -> Unit) {
    val prefs = Services.prefs
    val accent = Neon.Pink
    var showTutorial by remember { mutableStateOf(!prefs.tutorialSeen("breakout")) }
    var best by remember { mutableStateOf(prefs.best(BEST_KEY)) }
    var newRecord by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        GameBackground(accent)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val density = LocalDensity.current
            val canvasW = with(density) { maxWidth.toPx() }
            val canvasH = with(density) { (maxHeight - 64.dp).toPx() }
            val worldH = canvasH / canvasW * 1000f
            val game = remember { BreakoutGame(worldH) }

            val fg by AppState.foreground
            LaunchedEffect(fg) { if (!fg) game.pause() }
            GameLoop { dt -> game.update(dt, frozen = showTutorial) }
            LaunchedEffect(game.phase) {
                if (game.phase == BPhase.OVER) {
                    newRecord = prefs.submitHigh(BEST_KEY, game.score)
                    best = prefs.best(BEST_KEY)
                }
            }
            BackHandler { if (game.phase == BPhase.PLAYING) game.pause() else onExit() }

            Column(Modifier.fillMaxSize()) {
                GameTopBar(
                    accent = accent,
                    onBack = onExit,
                    onHelp = { game.pause(); showTutorial = true },
                    onPause = { game.pause() },
                ) {
                    StatBox("Punti", game.score.toString(), accent)
                    StatBox("Livello", game.level.toString(), Neon.Cyan)
                    LivesBox(game.lives)
                }
                Box(Modifier.fillMaxSize()) {
                    BreakoutBoard(game)
                    if (game.combo >= 3 && game.phase == BPhase.PLAYING) {
                        NeonText(
                            "COMBO ×${game.combo}", Neon.Yellow, (16 + minOf(game.combo, 12)).sp,
                            Modifier.align(Alignment.TopCenter).padding(top = 6.dp), glow = 18f,
                        )
                    }
                    if (game.attached && game.phase == BPhase.PLAYING && !showTutorial) {
                        LaunchHint(Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp))
                    }
                    ActivePowers(game, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
                    LevelClearBanner(game.phase == BPhase.CLEAR, game.level, Modifier.align(Alignment.Center))
                }
            }

            ResultPanel(
                visible = game.phase == BPhase.PAUSED && !showTutorial,
                accent = accent,
                title = "PAUSA",
                lines = listOf("Punti" to game.score.toString(), "Livello" to game.level.toString()),
                highlight = null,
                primary = "Riprendi" to { game.resume() },
                tertiary = "Ricomincia" to { newRecord = false; game.restart() },
                secondary = "Menu" to onExit,
            )
            ResultPanel(
                visible = game.showResult,
                accent = accent,
                title = "GAME OVER",
                lines = listOf("Punti" to game.score.toString(), "Livello" to game.level.toString(), "Record" to best.toString()),
                highlight = if (newRecord) "★ NUOVO RECORD! ★" else null,
                primary = "Riprova" to { newRecord = false; game.restart() },
                secondary = "Menu" to onExit,
            )
            TutorialOverlay(
                visible = showTutorial,
                accent = accent,
                gameTitle = "BREAKOUT",
                pages = breakoutTutorial(),
                onDone = { showTutorial = false; prefs.markTutorialSeen("breakout") },
            )
        }
    }
}

@Composable
private fun LevelClearBanner(visible: Boolean, level: Int, modifier: Modifier) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NeonText("LIVELLO $level", Neon.Cyan, 26.sp, textAlign = TextAlign.Center)
            NeonText("COMPLETATO!", Neon.Yellow, 34.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LivesBox(lives: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("VITE", color = Neon.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        if (lives <= 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(lives) { NeonText("♥", Neon.Red, 18.sp, glow = 12f, letterSpacing = 0.sp) }
            }
        } else {
            NeonText("♥×$lives", Neon.Red, 20.sp, glow = 12f, letterSpacing = 0.sp)
        }
    }
}

@Composable
private fun LaunchHint(modifier: Modifier) {
    val a by rememberInfiniteTransition(label = "hint").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a",
    )
    NeonText("TOCCA PER LANCIARE", Neon.Text, 16.sp, modifier.graphicsLayer { alpha = a })
}

@Composable
private fun ActivePowers(game: BreakoutGame, modifier: Modifier) {
    game.frame
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val list = listOf(
            Triple("LARGA", game.wideT, Neon.Cyan),
            Triple("LENTA", game.slowT, Neon.Green),
            Triple("FUOCO", game.fireT, Neon.Orange),
        )
        for ((label, t, c) in list) {
            if (t > 0f) Text("$label ${t.toInt() + 1}s", color = c, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun BreakoutBoard(game: BreakoutGame) {
    val measurer = rememberTextMeasurer()
    val labels = remember {
        PowerType.entries.associateWith {
            measurer.measure(it.label, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Neon.Bg0))
        }
    }
    val scaleRef = remember { floatArrayOf(1f) }
    Canvas(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(game) {
                // Trascina per muovere la racchetta, rilascia (o tocca) per lanciare
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastX = down.position.x
                    while (true) {
                        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            game.launch()
                            break
                        }
                        game.movePaddle((ch.position.x - lastX) / scaleRef[0])
                        lastX = ch.position.x
                        ch.consume()
                    }
                }
            },
    ) {
        game.frame
        val s = size.width / game.W
        scaleRef[0] = s
        val shake = game.shake * 14f
        val sx = if (shake > 0f) (Random.nextFloat() - 0.5f) * shake else 0f
        val sy = if (shake > 0f) (Random.nextFloat() - 0.5f) * shake else 0f
        translate(sx * s, sy * s) {
            scale(s, s, pivot = Offset.Zero) {
                // Bordi del campo
                drawRect(
                    Brush.horizontalGradient(listOf(Neon.Pink.copy(alpha = 0.35f), Color.Transparent), 0f, 18f),
                    Offset.Zero, Size(18f, game.worldH),
                )
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Neon.Pink.copy(alpha = 0.35f)), game.W - 18f, game.W),
                    Offset(game.W - 18f, 0f), Size(18f, game.worldH),
                )
                drawLine(Neon.Pink, Offset(2f, 0f), Offset(2f, game.worldH), 3f, alpha = 0.8f)
                drawLine(Neon.Pink, Offset(game.W - 2f, 0f), Offset(game.W - 2f, game.worldH), 3f, alpha = 0.8f)
                drawLine(Neon.Pink, Offset(0f, 2f), Offset(game.W, 2f), 3f, alpha = 0.8f)
                // Zona pericolo
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Neon.Red.copy(alpha = 0.18f)), game.worldH - 60f, game.worldH),
                    Offset(0f, game.worldH - 60f), Size(game.W, 60f),
                )

                for (br in game.bricks) {
                    if (!br.alive) continue
                    val p = ((game.clock - br.appearAt) / 0.45f).coerceIn(0f, 1f)
                    if (p <= 0f) continue
                    drawBrick(br, yOffset = -(1f - easeOutCubic(p)) * 300f, alpha = p)
                }
                for (pu in game.powerUps) drawCapsule(Offset(pu.x, pu.y), pu.type, game.clock)
                if (game.phase != BPhase.OVER) {
                    drawPaddle(
                        game.paddleX, game.paddleY, game.paddleW, game.paddleH, game.paddleBounce,
                        if (game.fireT > 0f) Neon.Orange else Neon.Cyan,
                    )
                }
                for (b in game.balls) drawBall(Offset(b.x, b.y), game.ballR, b.trail.toList(), game.fireT > 0f)
                game.particles.draw(this)
            }
            for (pu in game.powerUps) {
                val l = labels.getValue(pu.type)
                drawText(l, topLeft = Offset(pu.x * s - l.size.width / 2f, pu.y * s - l.size.height / 2f))
            }
        }
    }
}
