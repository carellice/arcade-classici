package com.giochiclassici.arcade.games.tris

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Sfx
import com.giochiclassici.arcade.core.Haptics
import com.giochiclassici.arcade.core.Services
import com.giochiclassici.arcade.ui.GameBackground
import com.giochiclassici.arcade.ui.GameTopBar
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.NeonButton
import com.giochiclassici.arcade.ui.NeonText
import com.giochiclassici.arcade.ui.OverlayPanel
import com.giochiclassici.arcade.ui.Raised
import com.giochiclassici.arcade.ui.StatBox
import com.giochiclassici.arcade.ui.Surface3D
import com.giochiclassici.arcade.ui.TutorialOverlay
import com.giochiclassici.arcade.ui.TutorialPage
import com.giochiclassici.arcade.ui.rememberTime
import kotlin.math.min
import kotlin.math.sin

@Composable
fun TrisScreen(onExit: () -> Unit) {
    val prefs = Services.prefs
    val accent = Neon.Purple
    var showTutorial by remember { mutableStateOf(!prefs.tutorialSeen("tris")) }
    var mode by remember { mutableStateOf(TrisMode.CPU) }
    var difficulty by remember { mutableStateOf(TrisDifficulty.NORMAL) }
    var game by remember { mutableStateOf<TrisGame?>(null) }
    // Cresce a ogni mossa: fa ridisegnare la griglia e l'HUD.
    var version by remember { mutableIntStateOf(0) }

    fun afterMove(g: TrisGame) {
        version++
        when {
            !g.over -> { Audio.play(Sfx.Move); Haptics.tick() }
            g.winner == 0 -> Audio.play(Sfx.Wall)
            g.mode == TrisMode.CPU && g.winner == 2 -> { Audio.play(Sfx.Die); Haptics.heavy() }
            else -> { Audio.play(Sfx.Win); Haptics.heavy() }
        }
    }

    // La CPU ci pensa un attimo prima di muovere.
    LaunchedEffect(game, version, showTutorial) {
        val g = game ?: return@LaunchedEffect
        if (g.cpuTurn && !showTutorial) {
            kotlinx.coroutines.delay(550)
            if (g.play(g.cpuMove())) afterMove(g)
        }
    }
    BackHandler { if (game != null) game = null else onExit() }

    Box(Modifier.fillMaxSize()) {
        GameBackground(accent)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val g = game
            version
            val cpu = (g?.mode ?: mode) == TrisMode.CPU
            GameTopBar(accent = accent, onBack = onExit, onHelp = { showTutorial = true }, onPause = null) {
                StatBox(if (cpu) "Tu ✕" else "G1 ✕", (g?.xWins ?: 0).toString(), Neon.Cyan)
                StatBox("Pari", (g?.draws ?: 0).toString(), Neon.TextDim)
                StatBox(if (cpu) "CPU ◯" else "G2 ◯", (g?.oWins ?: 0).toString(), Neon.Pink)
            }
            Text(
                when {
                    g == null -> ""
                    g.over && g.winner == 0 -> "PAREGGIO!"
                    g.over -> winnerLabel(g)
                    g.cpuTurn -> "LA CPU CI PENSA…"
                    else -> "TOCCA A " + playerLabel(g, g.turn)
                },
                Modifier.fillMaxWidth().padding(8.dp),
                color = when {
                    g == null -> Neon.TextDim
                    g.over && g.winner == 0 -> Neon.Yellow
                    (if (g.over) g.winner else g.turn) == 1 -> Neon.Cyan
                    else -> Neon.Pink
                },
                fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, textAlign = TextAlign.Center,
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                val side = with(LocalDensity.current) { min(maxWidth.toPx(), maxHeight.toPx()) }
                if (g != null) TrisBoard(g, version, side) { i ->
                    if (!g.cpuTurn && g.play(i)) afterMove(g)
                }
            }
            if (g != null) {
                // Uno sopra l'altro a tutta larghezza: affiancati "Cambia modalità" andava a capo.
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (g.over) NeonButton("Ancora", accent, Modifier.fillMaxWidth(), filled = true) { g.nextRound(); version++; Audio.play(Sfx.Start) }
                    NeonButton("Cambia modalità", Neon.TextDim, Modifier.fillMaxWidth()) { game = null }
                }
            }
        }

        OverlayPanel(game == null && !showTutorial, accent) {
            NeonText("TRIS", accent, 42.sp, glow = 32f, letterSpacing = 6.sp)
            Text("TRE IN FILA PER VINCERE", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrisChoice("1vCPU", mode == TrisMode.CPU, Modifier.weight(1f)) { mode = TrisMode.CPU }
                TrisChoice("1v1", mode == TrisMode.LOCAL, Modifier.weight(1f)) { mode = TrisMode.LOCAL }
            }
            if (mode == TrisMode.CPU) {
                Text("DIFFICOLTÀ CPU", color = Neon.TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrisDifficulty.entries.forEach { d ->
                        TrisChoice(d.label, difficulty == d, Modifier.weight(1f)) { difficulty = d }
                    }
                }
            }
            Text(
                if (mode == TrisMode.CPU) "Tu sei ✕, la CPU è ◯.\nChi comincia cambia a ogni partita."
                else "Due giocatori sullo stesso telefono:\nG1 è ✕, G2 è ◯. Passatevelo a turno!",
                color = Neon.Text, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
            NeonButton("Inizia partita", accent, Modifier.fillMaxWidth(), filled = true) {
                game = TrisGame(mode, difficulty); version++; Audio.play(Sfx.Start)
            }
            NeonButton("Menu", Neon.TextDim, Modifier.fillMaxWidth(), onClick = onExit)
        }
        TutorialOverlay(showTutorial, accent, "TRIS", trisTutorial()) {
            showTutorial = false
            prefs.markTutorialSeen("tris")
        }
    }
}

private fun playerLabel(g: TrisGame, p: Int) = when {
    g.mode == TrisMode.CPU -> if (p == 1) "TE (✕)" else "CPU (◯)"
    else -> if (p == 1) "G1 (✕)" else "G2 (◯)"
}

private fun winnerLabel(g: TrisGame) = when {
    g.mode == TrisMode.CPU -> if (g.winner == 1) "HAI VINTO!" else "VINCE LA CPU"
    else -> "VINCE G${g.winner}!"
}

@Composable
private fun TrisChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Raised(if (selected) Surface3D.solid(Neon.Purple) else Surface3D.dark(Neon.Purple), modifier,
        corner = 12.dp, depth = 4.dp, onClick = onClick) {
        Text(label.uppercase(), Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            color = if (selected) Neon.Bg0 else Neon.Purple, fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TrisBoard(game: TrisGame, version: Int, side: Float, onTap: (Int) -> Unit) {
    val time by rememberTime()
    val cell = side / 3f
    Canvas(
        Modifier.size(with(LocalDensity.current) { side.toDp() }).pointerInput(game, cell) {
            detectTapGestures { o ->
                val x = (o.x / cell).toInt(); val y = (o.y / cell).toInt()
                if (x in 0 until 3 && y in 0 until 3) onTap(y * 3 + x)
            }
        },
    ) {
        version
        drawGrid(cell, Neon.Purple)
        val line = game.winLine
        val pulse = 0.6f + 0.4f * sin(time * 6f)
        for (i in 0 until 9) {
            val c = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
            val dim = game.over && (line == null || i !in line)
            val a = if (dim) 0.35f else if (line != null) pulse else 1f
            when (game.board[i]) {
                1 -> drawX(c, cell * 0.28f, a)
                2 -> drawO(c, cell * 0.28f, a)
            }
        }
        if (line != null) {
            val from = Offset((line[0] % 3 + 0.5f) * cell, (line[0] / 3 + 0.5f) * cell)
            val to = Offset((line[2] % 3 + 0.5f) * cell, (line[2] / 3 + 0.5f) * cell)
            drawLine(Neon.Yellow.copy(alpha = 0.35f), from, to, cell * 0.16f, StrokeCap.Round)
            drawLine(Neon.Yellow, from, to, cell * 0.05f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawGrid(cell: Float, color: Color) {
    for (k in 1..2) {
        for ((w, a) in listOf(cell * 0.09f to 0.18f, cell * 0.03f to 0.9f)) {
            drawLine(color.copy(alpha = a), Offset(k * cell, cell * 0.08f), Offset(k * cell, cell * 2.92f), w, StrokeCap.Round)
            drawLine(color.copy(alpha = a), Offset(cell * 0.08f, k * cell), Offset(cell * 2.92f, k * cell), w, StrokeCap.Round)
        }
    }
}

/** Segni al neon: un alone largo e trasparente sotto un tratto pieno. */
private fun DrawScope.drawX(c: Offset, r: Float, alpha: Float) {
    for ((w, a) in listOf(r * 0.5f to 0.25f, r * 0.2f to 1f)) {
        drawLine(Neon.Cyan.copy(alpha = a * alpha), c + Offset(-r, -r), c + Offset(r, r), w, StrokeCap.Round)
        drawLine(Neon.Cyan.copy(alpha = a * alpha), c + Offset(r, -r), c + Offset(-r, r), w, StrokeCap.Round)
    }
}

private fun DrawScope.drawO(c: Offset, r: Float, alpha: Float) {
    drawCircle(Neon.Pink.copy(alpha = 0.25f * alpha), r, c, style = Stroke(r * 0.5f))
    drawCircle(Neon.Pink.copy(alpha = alpha), r, c, style = Stroke(r * 0.2f))
}

/** Anteprima animata per il menu: i segni compaiono uno alla volta. */
fun DrawScope.drawTrisPreview(t: Float) {
    val cell = size.width / 3f
    drawGrid(cell, Neon.Purple)
    val moves = intArrayOf(4, 0, 2, 6, 3, 5, 1, 7, 8)
    val shown = (t * 1.4f).toInt() % (moves.size + 3)
    for (k in 0 until min(shown, moves.size)) {
        val i = moves[k]
        val c = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
        if (k % 2 == 0) drawX(c, cell * 0.26f, 1f) else drawO(c, cell * 0.26f, 1f)
    }
}

fun trisTutorial(): List<TutorialPage> = listOf(
    TutorialPage("Tre in fila", "A turno, ognuno mette il proprio segno in una casella libera. Vince chi allinea per primo tre segni: in riga, in colonna o in diagonale.") {
        TutorialArt(moves = intArrayOf(0, 4, 1, 8, 2))
    },
    TutorialPage("Solo o in due", "1vCPU: tu sei ✕ contro la CPU, con tre difficoltà (Imbattibile non sbaglia mai). 1v1: giocate in due sullo stesso telefono.") {
        TutorialArt(moves = intArrayOf(4, 0, 2, 6, 3))
    },
    TutorialPage("Pareggio", "Se la griglia si riempie senza tris è pari. Il trucco: blocca sempre la fila che l'avversario sta per chiudere!") {
        TutorialArt(moves = intArrayOf(4, 0, 2, 6, 3, 5, 1, 7, 8))
    },
)

@Composable
private fun TutorialArt(moves: IntArray) {
    val time by rememberTime()
    Canvas(Modifier.fillMaxSize().padding(24.dp)) {
        val s = min(size.width, size.height)
        val cell = s / 3f
        val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        val shown = (time * 1.6f).toInt() % (moves.size + 3)
        val board = IntArray(9)
        for (k in 0 until min(shown, moves.size)) board[moves[k]] = if (k % 2 == 0) 1 else 2
        translate(o.x, o.y) {
            drawGrid(cell, Neon.Purple)
            for (i in 0 until 9) {
                val c = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
                if (board[i] == 1) drawX(c, cell * 0.26f, 1f) else if (board[i] == 2) drawO(c, cell * 0.26f, 1f)
            }
            Tris.winLine(board)?.let { l ->
                drawLine(Neon.Yellow, Offset((l[0] % 3 + 0.5f) * cell, (l[0] / 3 + 0.5f) * cell),
                    Offset((l[2] % 3 + 0.5f) * cell, (l[2] / 3 + 0.5f) * cell), cell * 0.05f, StrokeCap.Round)
            }
        }
    }
}
