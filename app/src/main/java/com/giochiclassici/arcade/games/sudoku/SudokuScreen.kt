package com.giochiclassici.arcade.games.sudoku

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
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
import com.giochiclassici.arcade.ui.Raised
import com.giochiclassici.arcade.ui.ResultPanel
import com.giochiclassici.arcade.ui.StatBox
import com.giochiclassici.arcade.ui.Surface3D
import com.giochiclassici.arcade.ui.TutorialOverlay
import com.giochiclassici.arcade.ui.TutorialPage
import com.giochiclassici.arcade.ui.formatTime
import com.giochiclassici.arcade.ui.pressable
import com.giochiclassici.arcade.ui.recessed
import com.giochiclassici.arcade.ui.rememberTime
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

@Composable
fun SudokuScreen(onExit: () -> Unit) {
    val prefs = Services.prefs
    val accent = Neon.Yellow
    var diff by remember { mutableStateOf(SudokuDiff.EASY) }
    var game by remember { mutableStateOf(SudokuGame(diff)) }
    var showTutorial by remember { mutableStateOf(!prefs.tutorialSeen("sudoku")) }
    var newRecord by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    fun newGame(d: SudokuDiff = diff) {
        diff = d
        game = SudokuGame(d)
        newRecord = false
        dismissed = false
    }

    val fg by AppState.foreground
    GameLoop { dt -> game.update(dt, frozen = showTutorial || !fg) }
    LaunchedEffect(game, game.phase) {
        if (game.phase == SudokuPhase.WON) newRecord = prefs.submitLow(diff.bestKey, maxOf(1, game.seconds))
    }
    BackHandler { onExit() }

    Box(Modifier.fillMaxSize()) {
        GameBackground(accent)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            GameTopBar(accent = accent, onBack = onExit, onHelp = { showTutorial = true }, onPause = null) {
                StatBox("Tempo", formatTime(game.seconds), accent)
                StatBox("Errori", game.mistakes.toString(), Neon.Pink)
                // Il tempo da battere per questa difficoltà, se c'è.
                val record = remember(diff, newRecord) { prefs.best(diff.bestKey) }
                if (record > 0) StatBox("Record", formatTime(record), Neon.Green)
            }
            DifficultyBar(diff) { newGame(it) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                val side = with(LocalDensity.current) { floor(min(maxWidth.toPx(), maxHeight.toPx()) / 9f) * 9f }
                SudokuBoard(game, side)
            }
            NumberPad(game)
            Spacer(Modifier.height(12.dp))
        }

        val best = prefs.best(diff.bestKey)
        ResultPanel(
            visible = game.phase == SudokuPhase.WON && !dismissed && game.clock - game.endTime > 0.9f,
            accent = Neon.Green,
            title = "RISOLTO!",
            lines = listOf("Tempo" to formatTime(game.seconds), "Errori" to game.mistakes.toString(), "Record" to (if (best > 0) formatTime(best) else "-")),
            highlight = if (newRecord) "★ NUOVO RECORD (${diff.label}) ★" else "Schema completato!",
            primary = "Nuova partita" to { newGame() },
            secondary = "Menu" to onExit,
            tertiary = "Guarda lo schema" to { dismissed = true },
        )
        TutorialOverlay(
            visible = showTutorial,
            accent = accent,
            gameTitle = "SUDOKU",
            pages = sudokuTutorial(),
            onDone = { showTutorial = false; prefs.markTutorialSeen("sudoku") },
        )
    }
}

@Composable
private fun DifficultyBar(current: SudokuDiff, onSelect: (SudokuDiff) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp).recessed(20.dp).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (d in SudokuDiff.entries) {
            val label: @Composable (Color) -> Unit = { fg ->
                Text(d.label.uppercase(), color = fg, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
            }
            if (d == current) {
                Raised(Surface3D.solid(Neon.Yellow), Modifier.weight(1f).fillMaxHeight(), corner = 18.dp, depth = 4.dp) { label(Neon.Bg0) }
            } else {
                Box(
                    Modifier.weight(1f).fillMaxHeight().padding(bottom = 4.dp).pressable { onSelect(d) },
                    contentAlignment = Alignment.Center,
                ) { label(Neon.Yellow.copy(alpha = 0.75f)) }
            }
        }
    }
}

/** I tasti da 1 a 9 e la gomma: un numero già messo nove volte si spegne. */
@Composable
private fun NumberPad(game: SudokuGame) {
    game.frame
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf(1..5, 6..9)) {
            Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in row) {
                    val done = game.count(n) >= 9
                    Raised(
                        if (done) Surface3D.solid(Neon.TextDim) else Surface3D.solid(Neon.Cyan),
                        Modifier.weight(1f).fillMaxHeight(), corner = 14.dp, depth = 4.dp,
                        onClick = { game.input(n) },
                    ) {
                        Text(n.toString(), color = Neon.Bg0, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                }
                if (row.last == 9) {
                    Raised(Surface3D.solid(Neon.Pink), Modifier.weight(1f).fillMaxHeight(), corner = 14.dp, depth = 4.dp, onClick = { game.input(0) }) {
                        Text("✕", color = Neon.Bg0, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun SudokuBoard(game: SudokuGame, side: Float) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val cell = side / 9f
    val sizeDp = with(density) { side.toDp() }
    val styles = remember(cell) { NumberStyles(measurer, with(density) { (cell * 0.6f).toSp() }) }
    Canvas(
        Modifier.size(sizeDp).pointerInput(game, cell) {
            detectTapGestures { o ->
                val x = (o.x / cell).toInt(); val y = (o.y / cell).toInt()
                if (x in 0 until 9 && y in 0 until 9) game.select(y * 9 + x)
            }
        },
    ) {
        game.frame
        drawBoard(game, cell, styles)
    }
}

/** I numeri già misurati: dati in bianco, scritti in azzurro, sbagliati in rosa. */
private class NumberStyles(private val m: TextMeasurer, private val fontSize: androidx.compose.ui.unit.TextUnit) {
    private fun row(color: Color) = (0..9).map { m.measure(if (it == 0) "" else it.toString(), TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, color = color, fontFamily = FontFamily.Monospace)) }
    val given = row(Neon.Text)
    val typed = row(Neon.Cyan)
    val wrong = row(Neon.Pink)
}

private fun DrawScope.drawBoard(game: SudokuGame, cell: Float, styles: NumberStyles) {
    val sel = game.selected
    val selValue = if (sel >= 0) game.grid[sel] else 0
    val won = game.phase == SudokuPhase.WON
    drawRoundRect(Color(0xFF0B0920), cornerRadius = CornerRadius(cell * 0.25f))
    for (i in 0 until 81) {
        val x = i % 9; val y = i / 9
        val tl = Offset(x * cell, y * cell)
        // La casella scelta, la sua riga, colonna e riquadro, e gli stessi numeri altrove.
        val related = sel >= 0 && (i == sel || i in Sudoku.peers(sel))
        val same = selValue != 0 && game.grid[i] == selValue
        val glow = when {
            i == sel -> Neon.Yellow.copy(alpha = 0.35f)
            same -> Neon.Cyan.copy(alpha = 0.22f)
            related -> Color.White.copy(alpha = 0.06f)
            else -> null
        }
        if (glow != null) drawRect(glow, tl, Size(cell, cell))
        if (won) {
            // Onda verde che attraversa lo schema risolto.
            val wave = ((game.clock - game.endTime) * 2.2f - (x + y) * 0.12f).coerceIn(0f, 1f)
            drawRect(Neon.Green, tl, Size(cell, cell), alpha = 0.25f * sin(wave * Math.PI).toFloat())
        }
        val n = game.grid[i]
        if (n != 0) {
            val l: TextLayoutResult = when {
                game.given[i] -> styles.given[n]
                game.conflict(i) || n != game.solution[i] -> styles.wrong[n]
                else -> styles.typed[n]
            }
            val c = tl + Offset(cell / 2, cell / 2)
            drawText(l, topLeft = c - Offset(l.size.width / 2f, l.size.height / 2f), shadow = if (game.given[i]) null else Shadow(Neon.Cyan, Offset.Zero, cell * 0.25f))
        }
    }
    for (k in 0..9) {
        val thick = k % 3 == 0
        val color = if (thick) Neon.Yellow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.14f)
        val w = if (thick) cell * 0.06f else cell * 0.02f
        drawLine(color, Offset(k * cell, 0f), Offset(k * cell, size.height), w)
        drawLine(color, Offset(0f, k * cell), Offset(size.width, k * cell), w)
    }
}

fun sudokuTutorial(): List<TutorialPage> = listOf(
    TutorialPage(
        "L'obiettivo",
        "Riempi lo schema: ogni riga, ogni colonna e ogni riquadro 3×3 contiene i numeri da 1 a 9, una volta sola.",
    ) { MiniArt(highlightRow = true) },
    TutorialPage(
        "Scegli e scrivi",
        "Tocca una casella vuota, poi un numero in basso. La ✕ cancella. I numeri uguali si illuminano in tutto lo schema.",
    ) { MiniArt(highlightRow = false) },
    TutorialPage(
        "Occhio agli errori",
        "Un numero che si scontra con un altro diventa rosa. Ogni sbaglio conta: più veloce e preciso sei, migliore è il record.",
    ) { MiniArt(highlightRow = false, wrong = true) },
)

@Composable
private fun MiniArt(highlightRow: Boolean, wrong: Boolean = false) {
    val time by rememberTime()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(fontSize = with(density) { 20.dp.toSp() }, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    val nums = remember { (1..9).map { measurer.measure(it.toString(), style.copy(color = Neon.Text)) } }
    val bad = remember { measurer.measure("5", style.copy(color = Neon.Pink)) }
    Canvas(Modifier.fillMaxSize().padding(24.dp)) {
        val cell = min(size.width, size.height) / 3f
        val origin = Offset((size.width - cell * 3) / 2, (size.height - cell * 3) / 2)
        val values = intArrayOf(5, 3, 0, 6, 0, 0, 0, 9, 8)
        val pulse = 0.5f + 0.5f * sin(time * 3f)
        for (i in 0 until 9) {
            val tl = origin + Offset(i % 3 * cell, i / 3 * cell)
            if (highlightRow && i / 3 == (time.toInt() % 3)) drawRect(Neon.Yellow.copy(alpha = 0.18f), tl, Size(cell, cell))
            if (!highlightRow && i == 2) drawRect(Neon.Yellow.copy(alpha = 0.2f + 0.2f * pulse), tl, Size(cell, cell))
            val l = when {
                wrong && i == 2 -> bad
                values[i] != 0 -> nums[values[i] - 1]
                else -> null
            } ?: continue
            drawText(l, topLeft = tl + Offset((cell - l.size.width) / 2f, (cell - l.size.height) / 2f))
        }
        for (k in 0..3) {
            drawLine(Neon.Yellow.copy(alpha = 0.7f), origin + Offset(k * cell, 0f), origin + Offset(k * cell, cell * 3), 3f)
            drawLine(Neon.Yellow.copy(alpha = 0.7f), origin + Offset(0f, k * cell), origin + Offset(cell * 3, k * cell), 3f)
        }
        drawRect(Neon.Yellow, origin, Size(cell * 3, cell * 3), style = Stroke(4f), alpha = 0.4f)
    }
}
