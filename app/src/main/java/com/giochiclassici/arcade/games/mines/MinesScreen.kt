package com.giochiclassici.arcade.games.mines

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.core.AppState
import com.giochiclassici.arcade.core.Services
import com.giochiclassici.arcade.ui.GameBackground
import com.giochiclassici.arcade.ui.GameLoop
import com.giochiclassici.arcade.ui.GameTopBar
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.NeonIcons.dig
import com.giochiclassici.arcade.ui.NeonIcons.flag
import com.giochiclassici.arcade.ui.Raised
import com.giochiclassici.arcade.ui.ResultPanel
import com.giochiclassici.arcade.ui.Surface3D
import com.giochiclassici.arcade.ui.recessed
import com.giochiclassici.arcade.ui.StatBox
import com.giochiclassici.arcade.ui.TutorialOverlay
import com.giochiclassici.arcade.ui.formatTime
import com.giochiclassici.arcade.ui.pressable
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

@Composable
fun MinesScreen(onExit: () -> Unit) {
    val prefs = Services.prefs
    val accent = Neon.Cyan
    var diff by remember { mutableStateOf(MineDiff.EASY) }
    var game by remember { mutableStateOf(MinesGame(diff)) }
    var flagMode by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(!prefs.tutorialSeen("mines")) }
    var newRecord by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    fun newGame(d: MineDiff = diff) {
        diff = d
        game = MinesGame(d)
        newRecord = false
        dismissed = false
    }

    val fg by AppState.foreground
    GameLoop { dt -> game.update(dt, frozen = showTutorial || !fg) }
    LaunchedEffect(game, game.phase) {
        if (game.phase == MinesPhase.WON) newRecord = prefs.submitLow(diff.bestKey, maxOf(1, game.seconds))
    }
    BackHandler { onExit() }

    Box(Modifier.fillMaxSize()) {
        GameBackground(accent)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            GameTopBar(accent = accent, onBack = onExit, onHelp = { showTutorial = true }, onPause = null) {
                StatBox("Mine", game.minesLeft.toString(), Neon.Pink)
                FaceButton(game.face) { newGame() }
                StatBox("Tempo", formatTime(game.seconds), accent)
            }
            DifficultyBar(diff) { newGame(it) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val cell = floor(min(
                    with(density) { maxWidth.toPx() } / game.cols,
                    with(density) { maxHeight.toPx() } / game.rows,
                ))
                MinesBoard(game, cell, flagMode)
            }
            ModeToggle(flagMode) { flagMode = it }
            Spacer(Modifier.height(12.dp))
        }

        val best = prefs.best(diff.bestKey)
        ResultPanel(
            visible = game.phase == MinesPhase.WON && game.showResult && !dismissed,
            accent = Neon.Green,
            title = "VITTORIA!",
            lines = listOf("Tempo" to formatTime(game.seconds), "Record" to (if (best > 0) formatTime(best) else "-")),
            highlight = if (newRecord) "★ NUOVO RECORD (${diff.label}) ★" else "Campo bonificato!",
            primary = "Nuova partita" to { newGame() },
            secondary = "Menu" to onExit,
            tertiary = "Guarda il campo" to { dismissed = true },
        )
        ResultPanel(
            visible = game.phase == MinesPhase.LOST && game.showResult && !dismissed,
            accent = Neon.Pink,
            title = "BOOM!",
            lines = listOf("Tempo" to formatTime(game.seconds), "Livello" to diff.label),
            highlight = null,
            primary = "Riprova" to { newGame() },
            secondary = "Menu" to onExit,
            tertiary = "Guarda il campo" to { dismissed = true },
        )
        TutorialOverlay(
            visible = showTutorial,
            accent = accent,
            gameTitle = "CAMPO MINATO",
            pages = minesTutorial(),
            onDone = { showTutorial = false; prefs.markTutorialSeen("mines") },
        )
    }
}

@Composable
private fun DifficultyBar(current: MineDiff, onSelect: (MineDiff) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
            .recessed(20.dp).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (d in MineDiff.entries) {
            Segment(d.label, d == current, Neon.Cyan, Modifier.weight(1f), null) { if (d != current) onSelect(d) }
        }
    }
}

@Composable
private fun ModeToggle(flagMode: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 36.dp).height(62.dp)
            .recessed(26.dp).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Segment("Scava", !flagMode, Neon.Cyan, Modifier.weight(1f), { dig(it) }) { onChange(false) }
        Segment("Bandiera", flagMode, Neon.Pink, Modifier.weight(1f), { flag(it) }) { onChange(true) }
    }
}

/** Segmento di un selettore: in rilievo se selezionato, piatto nel binario altrimenti. */
@Composable
private fun Segment(
    label: String, selected: Boolean, color: Color, modifier: Modifier,
    icon: (DrawScope.(Color) -> Unit)?, onClick: () -> Unit,
) {
    val content: @Composable (Color) -> Unit = { fg ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Canvas(Modifier.size(17.dp)) { icon(fg) }
                Spacer(Modifier.width(8.dp))
            }
            Text(label.uppercase(), color = fg, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
        }
    }
    if (selected) {
        Raised(Surface3D.solid(color), modifier.fillMaxHeight(), corner = 18.dp, depth = 4.dp, onClick = onClick) {
            content(Neon.Bg0)
        }
    } else {
        Box(
            modifier.fillMaxHeight().padding(bottom = 4.dp).pressable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content(color.copy(alpha = 0.75f)) }
    }
}

@Composable
private fun FaceButton(face: Face, onClick: () -> Unit) {
    Raised(Surface3D.solid(Neon.Yellow), Modifier.size(46.dp, 50.dp), corner = 23.dp, depth = 4.dp, onClick = onClick) {
        Canvas(Modifier.size(30.dp)) { drawFace(face) }
    }
}

fun DrawScope.drawFace(face: Face) {
    val w = size.width
    val c = Offset(w / 2, w / 2)
    val col = Color(0xFF3A2A00)
    val eyeY = w * 0.4f
    when (face) {
        Face.DEAD -> for (ex in listOf(0.34f, 0.66f)) {
            val e = Offset(w * ex, eyeY); val r = w * 0.07f
            drawLine(col, e - Offset(r, r), e + Offset(r, r), w * 0.06f, StrokeCap.Round)
            drawLine(col, e + Offset(r, -r), e + Offset(-r, r), w * 0.06f, StrokeCap.Round)
        }
        Face.COOL -> {
            drawRoundRect(col, Offset(w * 0.2f, eyeY - w * 0.08f), Size(w * 0.26f, w * 0.14f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
            drawRoundRect(col, Offset(w * 0.54f, eyeY - w * 0.08f), Size(w * 0.26f, w * 0.14f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
            drawLine(col, Offset(w * 0.46f, eyeY - w * 0.03f), Offset(w * 0.54f, eyeY - w * 0.03f), w * 0.05f)
        }
        else -> for (ex in listOf(0.34f, 0.66f)) drawCircle(col, w * 0.06f, Offset(w * ex, eyeY))
    }
    when (face) {
        Face.HAPPY, Face.COOL -> drawArc(
            col, 20f, 140f, false, Offset(w * 0.27f, w * 0.38f), Size(w * 0.46f, w * 0.3f),
            style = Stroke(w * 0.07f, cap = StrokeCap.Round),
        )
        Face.WOW -> drawCircle(col, w * 0.09f, Offset(w / 2, w * 0.68f), style = Stroke(w * 0.06f))
        Face.DEAD -> drawArc(
            col, 200f, 140f, false, Offset(w * 0.3f, w * 0.62f), Size(w * 0.4f, w * 0.24f),
            style = Stroke(w * 0.07f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun MinesBoard(game: MinesGame, cell: Float, flagMode: Boolean) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val numbers: List<TextLayoutResult> = remember(cell) {
        (0..8).map {
            measurer.measure(
                it.toString(),
                TextStyle(
                    fontSize = with(density) { (cell * 0.58f).toSp() },
                    fontWeight = FontWeight.Black,
                    color = NumberColors[it],
                ),
            )
        }
    }
    val mode by rememberUpdatedState(flagMode)
    val wDp = with(density) { (cell * game.cols).toDp() }
    val hDp = with(density) { (cell * game.rows).toDp() }
    Canvas(
        Modifier
            .size(wDp, hDp)
            .pointerInput(game, cell) {
                fun index(o: Offset): Int? {
                    val x = (o.x / cell).toInt(); val y = (o.y / cell).toInt()
                    return if (x in 0 until game.cols && y in 0 until game.rows) y * game.cols + x else null
                }
                detectTapGestures(
                    onTap = { o -> index(o)?.let { game.tap(it, mode) } },
                    onLongPress = { o -> index(o)?.let { game.longPress(it, mode) } },
                )
            },
    ) {
        game.frame
        val t = game.clock
        val sh = game.shake * cell * 0.25f
        translate((Random.nextFloat() - 0.5f) * sh, (Random.nextFloat() - 0.5f) * sh) {
            for (i in 0 until game.n) {
                val tl = Offset(game.x(i) * cell, game.y(i) * cell)
                val center = tl + Offset(cell / 2, cell / 2)
                val isMine = game.mine[i]
                val lost = game.phase == MinesPhase.LOST
                val showMine = lost && isMine && t >= game.boomAt[i]
                when {
                    showMine -> {
                        drawRevealedTile(tl, cell, 1f, danger = i == game.exploded)
                        drawMine(center, cell, t)
                    }
                    game.revealed[i] && t >= game.revealAt[i] -> {
                        val a = ((t - game.revealAt[i]) / 0.2f).coerceIn(0f, 1f)
                        drawRevealedTile(tl, cell, a)
                        val num = game.adj[i]
                        if (num > 0) {
                            val l = numbers[num]
                            drawText(
                                l,
                                topLeft = center - Offset(l.size.width / 2f, l.size.height / 2f),
                                alpha = a,
                                shadow = Shadow(NumberColors[num], Offset.Zero, cell * 0.3f),
                            )
                        }
                    }
                    else -> {
                        drawHiddenTile(tl, cell, if (mode) 0.15f else 0f)
                        if (game.flagged[i]) {
                            drawFlag(center, cell, t + i)
                            if (lost && !isMine) drawCross(center, cell)
                        }
                    }
                }
            }
            if (game.phase == MinesPhase.WON) {
                val p = ((t - game.endTime) / 1.2f).coerceIn(0f, 1f)
                drawRect(
                    Neon.Green, Offset.Zero, Size(size.width, size.height),
                    alpha = (1f - p) * 0.25f,
                )
            }
            game.particles.draw(this, cell)
            if (game.phase == MinesPhase.READY) {
                drawRect(Neon.Cyan, Offset.Zero, size, alpha = 0.03f + 0.03f * kotlin.math.sin(t * 3f), style = Stroke(cell * 0.05f))
            }
        }
    }
}
