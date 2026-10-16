package com.giochiclassici.arcade.games.blocks

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.giochiclassici.arcade.ui.recessed
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

private const val BEST_KEY = "best_blocks"

@Composable
fun BlocksScreen(onExit: () -> Unit) {
    val prefs = Services.prefs
    val accent = Neon.Orange
    var showTutorial by remember { mutableStateOf(!prefs.tutorialSeen("blocks")) }
    var best by remember { mutableStateOf(prefs.best(BEST_KEY)) }
    // Record a inizio partita: il punteggio viene salvato anche a metà, così il confronto resta corretto
    var startBest by remember { mutableStateOf(best) }
    val game = remember { BlocksGame() }

    val fg by AppState.foreground
    LaunchedEffect(fg) { if (!fg) game.pause() }
    GameLoop { dt -> game.update(dt, frozen = showTutorial) }
    val saveScore = {
        prefs.submitHigh(BEST_KEY, game.score)
        best = prefs.best(BEST_KEY)
    }
    val restart = { saveScore(); startBest = best; game.restart() }
    val exit = { saveScore(); onExit() }
    LaunchedEffect(game.phase) {
        if (game.phase == BlPhase.OVER || game.phase == BlPhase.PAUSED) saveScore()
    }
    DisposableEffect(game) { onDispose { saveScore() } }
    BackHandler { if (game.phase == BlPhase.PLAYING) game.pause() else exit() }

    Box(Modifier.fillMaxSize()) {
        GameBackground(accent)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val density = LocalDensity.current
            val sideW = 78.dp
            val areaW = with(density) { (maxWidth - 24.dp - sideW - 10.dp).toPx() }
            val areaH = with(density) { (maxHeight - 64.dp - 28.dp).toPx() }
            val cell = floor(min(areaW / game.cols, areaH / game.rows))

            Column(Modifier.fillMaxSize()) {
                GameTopBar(
                    accent = accent,
                    onBack = exit,
                    onHelp = { game.pause(); showTutorial = true },
                    onPause = { game.pause() },
                ) {
                    StatBox("Punti", game.score.toString(), accent)
                    StatBox("Righe", game.lines.toString(), Neon.Cyan)
                    StatBox("Livello", game.level.toString(), Neon.Green)
                }
                Box(
                    Modifier.fillMaxSize().padding(bottom = 16.dp).blocksControls(game, cell),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        val wDp = with(density) { (cell * game.cols).toDp() }
                        val hDp = with(density) { (cell * game.rows).toDp() }
                        Box(Modifier.size(wDp, hDp)) {
                            BlocksBoard(game, cell)
                            BlocksBanner(game.banner, Modifier.align(Alignment.Center))
                            if (game.phase == BlPhase.READY && !showTutorial) {
                                ReadyHint(Modifier.align(Alignment.Center))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        SidePanel(game, best, sideW)
                    }
                }
            }

            ResultPanel(
                visible = game.phase == BlPhase.PAUSED && !showTutorial,
                accent = accent,
                title = "PAUSA",
                lines = listOf("Punti" to game.score.toString(), "Righe" to game.lines.toString(), "Record" to best.toString()),
                highlight = null,
                primary = "Riprendi" to { game.resume() },
                tertiary = "Ricomincia" to restart,
                secondary = "Menu" to exit,
            )
            ResultPanel(
                visible = game.showResult,
                accent = Neon.Pink,
                title = "GAME OVER",
                lines = listOf("Punti" to game.score.toString(), "Righe" to game.lines.toString(), "Record" to best.toString()),
                highlight = if (game.score > startBest) "★ NUOVO RECORD! ★" else null,
                primary = "Riprova" to restart,
                secondary = "Menu" to exit,
            )
            TutorialOverlay(
                visible = showTutorial,
                accent = accent,
                gameTitle = "BLOCCHI",
                pages = blocksTutorial(),
                onDone = { showTutorial = false; prefs.markTutorialSeen("blocks") },
            )
        }
    }
}

/**
 * Comandi a gesti: tocco = ruota, trascina di lato = sposta,
 * trascina in giù = discesa veloce, scatto verso il basso = caduta immediata.
 */
private fun Modifier.blocksControls(game: BlocksGame, cell: Float): Modifier = pointerInput(game, cell) {
    val slop = 10.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val t0 = down.uptimeMillis
        var anchorX = down.position.x
        var anchorY = down.position.y
        var moved = false
        var dropping = false
        while (true) {
            val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            val total = ch.position - down.position
            if (!ch.pressed) {
                game.setSoftDrop(false)
                val dt = ch.uptimeMillis - t0
                when {
                    game.phase == BlPhase.READY -> game.start()
                    !moved && dt < 350 -> game.rotate()
                    total.y > cell * 2.5f && total.y > abs(total.x) * 1.6f && dt < 280 -> game.hardDrop()
                }
                break
            }
            if (abs(total.x) > slop || abs(total.y) > slop) moved = true
            // Spostamento orizzontale: un passo per ogni cella percorsa dal dito
            if (!dropping) {
                while (ch.position.x - anchorX > cell * 0.9f) { game.move(1); anchorX += cell * 0.9f }
                while (anchorX - ch.position.x > cell * 0.9f) { game.move(-1); anchorX -= cell * 0.9f }
            }
            // Discesa veloce se il dito scende con decisione
            val dy = ch.position.y - anchorY
            if (dy > cell * 1.2f && dy > abs(ch.position.x - anchorX) * 1.4f) dropping = true
            if (dy < -cell) { dropping = false; anchorY = ch.position.y }
            game.setSoftDrop(dropping && ch.uptimeMillis - t0 > 200)
            ch.consume()
        }
    }
}

@Composable
private fun SidePanel(game: BlocksGame, best: Int, width: androidx.compose.ui.unit.Dp) {
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PROSSIMO", color = Neon.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(width).recessed(14.dp).padding(10.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                game.frame
                drawPiecePreview(game.next, size.width)
            }
        }
        Spacer(Modifier.height(14.dp))
        StatBox("Record", maxOf(best, game.score).toString(), Neon.Yellow)
    }
}

/** Disegna un pezzo centrato in un riquadro quadrato di lato [box]. */
fun DrawScope.drawPiecePreview(p: Piece, box: Float, offset: Offset = Offset.Zero) {
    val cells = p.shape(0)
    val minX = cells.minOf { it.first }; val maxX = cells.maxOf { it.first }
    val minY = cells.minOf { it.second }; val maxY = cells.maxOf { it.second }
    val c = box / 4.2f
    val ox = offset.x + (box - (maxX - minX + 1) * c) / 2f
    val oy = offset.y + (box - (maxY - minY + 1) * c) / 2f
    for ((x, y) in cells) drawBlock(Offset(ox + (x - minX) * c, oy + (y - minY) * c), c, p.color)
}

/** Blocco in rilievo: faccia illuminata, bordo scuro e riflesso. */
fun DrawScope.drawBlock(tl: Offset, s: Float, color: Color, alpha: Float = 1f, flash: Float = 0f) {
    val g = s * 0.06f
    val o = tl + Offset(g, g)
    val sz = Size(s - 2 * g, s - 2 * g)
    val r = CornerRadius(s * 0.16f)
    drawRoundRect(color, o - Offset(g, g), Size(sz.width + 2 * g, sz.height + 2 * g), CornerRadius(s * 0.22f), alpha = 0.18f * alpha)
    drawRoundRect(lerp(color, Color.Black, 0.5f), o, sz, r, alpha = alpha)
    val face = Size(sz.width, sz.height * 0.86f)
    drawRoundRect(
        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.2f)), o.y, o.y + face.height),
        o, face, r, alpha = alpha,
    )
    drawRoundRect(Color.White, o + Offset(sz.width * 0.14f, sz.height * 0.1f), Size(sz.width * 0.5f, sz.height * 0.12f), r, alpha = 0.4f * alpha)
    if (flash > 0f) drawRoundRect(Color.White, o, sz, r, alpha = flash * 0.7f * alpha)
}

@Composable
private fun BlocksBoard(game: BlocksGame, cell: Float) {
    Canvas(Modifier.fillMaxSize()) {
        game.frame // lettura per ridisegnare ad ogni frame
        val sh = game.shake * cell * 0.35f
        val sx = if (sh > 0f) (Random.nextFloat() - 0.5f) * sh else 0f
        val sy = if (sh > 0f) (Random.nextFloat() - 0.5f) * sh else 0f
        val w = cell * game.cols
        val h = cell * game.rows
        translate(sx, sy) {
            // Pozzo
            drawRoundRect(Color(0xCC07061A), Offset.Zero, Size(w, h), CornerRadius(cell * 0.3f))
            for (c in 1 until game.cols) drawLine(Neon.Orange, Offset(c * cell, 0f), Offset(c * cell, h), 1f, alpha = 0.08f)
            for (r in 1 until game.rows) drawLine(Neon.Orange, Offset(0f, r * cell), Offset(w, r * cell), 1f, alpha = 0.08f)
            drawRoundRect(Neon.Orange, Offset.Zero, Size(w, h), CornerRadius(cell * 0.3f), alpha = 0.7f, style = Stroke(3f))

            // Blocchi fermi
            val clearP = if (game.clearing.isNotEmpty()) 1f - game.clearT / 0.38f else 0f
            for (r in 0 until game.rows) {
                val clearingRow = r in game.clearing
                for (c in 0 until game.cols) {
                    val col = game.grid[r][c] ?: continue
                    if (clearingRow) {
                        // La riga si accende e si restringe verso il centro
                        val k = 1f - clearP
                        val cx = (c + 0.5f) * cell
                        val mid = w / 2f
                        drawBlock(Offset(mid + (cx - mid) * (0.4f + 0.6f * k) - cell / 2f, r * cell), cell, col, alpha = k, flash = 1f)
                    } else {
                        drawBlock(Offset(c * cell, r * cell), cell, col, flash = if (game.isLocked(c, r)) game.lockFlash * 0.6f else 0f)
                    }
                }
            }

            if (game.phase != BlPhase.OVER && game.clearing.isEmpty()) {
                // Ombra di atterraggio
                val gy = game.ghostY()
                for ((x, y) in game.cells(y = gy)) {
                    if (y < 0) continue
                    val i = cell * 0.1f
                    drawRoundRect(
                        game.piece.color, Offset(x * cell + i, y * cell + i), Size(cell - 2 * i, cell - 2 * i),
                        CornerRadius(cell * 0.14f), alpha = 0.45f, style = Stroke(2.5f),
                    )
                }
                // Pezzo in caduta
                for ((x, y) in game.cells()) if (y >= 0) drawBlock(Offset(x * cell, y * cell), cell, game.piece.color)
            }
            game.particles.draw(this, cell)
        }
    }
}

@Composable
private fun BlocksBanner(text: String?, modifier: Modifier) {
    AnimatedVisibility(
        text != null,
        modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.5f),
        exit = fadeOut(),
    ) {
        // Mantiene l'ultimo testo durante l'animazione di uscita
        val last = remember { mutableStateOf("") }
        if (text != null) last.value = text
        NeonText(last.value, Neon.Yellow, 30.sp, textAlign = TextAlign.Center, glow = 24f)
    }
}

@Composable
private fun ReadyHint(modifier: Modifier) {
    val a by rememberInfiniteTransition(label = "hint").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a",
    )
    Column(modifier.graphicsLayer { alpha = a }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        NeonText("TOCCA PER", Neon.Text, 18.sp, textAlign = TextAlign.Center)
        NeonText("INIZIARE", Neon.Text, 18.sp, textAlign = TextAlign.Center)
    }
}
