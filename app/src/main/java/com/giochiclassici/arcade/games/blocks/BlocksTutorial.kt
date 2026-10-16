package com.giochiclassici.arcade.games.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.TutorialPage
import com.giochiclassici.arcade.ui.easeOutCubic
import com.giochiclassici.arcade.ui.rememberTime
import kotlin.math.sin

fun blocksTutorial(): List<TutorialPage> = listOf(
    TutorialPage(
        "Sposta e ruota",
        "Trascina il dito a destra o a sinistra per spostare il pezzo. Tocca lo schermo per ruotarlo.",
    ) { MoveArt() },
    TutorialPage(
        "Falli cadere",
        "Trascina verso il basso per farlo scendere più in fretta, oppure dai uno scatto rapido in giù per lasciarlo cadere di colpo.",
    ) { DropArt() },
    TutorialPage(
        "Completa le righe",
        "Una riga piena sparisce e ti dà punti. Più righe insieme valgono molto di più: quattro in un colpo fanno TETRIS!",
    ) { LinesArt() },
    TutorialPage(
        "Livelli",
        "Ogni 10 righe sali di livello e i pezzi cadono più veloci. Guarda il riquadro PROSSIMO per pianificare la mossa.",
    ) { LevelArt() },
)

/** Canvas in unità "mondo" 300×300, con celle da 30. */
@Composable
private fun WorldArt(draw: DrawScope.(Float) -> Unit) {
    val t by rememberTime()
    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
        val s = size.minDimension / 300f
        scale(s, s, pivot = Offset.Zero) { draw(t) }
    }
}

private const val C = 30f

private fun DrawScope.piece(p: Piece, r: Int, x: Float, y: Float, alpha: Float = 1f) {
    for ((cx, cy) in p.shape(r)) drawBlock(Offset(x + cx * C, y + cy * C), C, p.color, alpha)
}

private fun DrawScope.finger(at: Offset, pressed: Boolean = true) {
    drawCircle(Neon.Cyan, 26f, at, alpha = if (pressed) 0.3f else 0.12f)
    drawCircle(Color.White, 12f, at, alpha = 0.9f)
}

@Composable
private fun MoveArt() = WorldArt { t ->
    val x = 120f + (sin(t * 1.4f) * 3f).toInt() * C
    val rot = ((t * 0.9f).toInt()) % 4
    piece(Piece.T, rot, x - 15f, 60f)
    val fx = x + 30f
    finger(Offset(fx, 250f))
    drawLine(Neon.Cyan, Offset(fx - 60f, 250f), Offset(fx - 36f, 250f), 4f, StrokeCap.Round, alpha = 0.5f)
    drawLine(Neon.Cyan, Offset(fx + 36f, 250f), Offset(fx + 60f, 250f), 4f, StrokeCap.Round, alpha = 0.5f)
}

@Composable
private fun DropArt() = WorldArt { t ->
    for (c in 0 until 10) if (c != 4 && c != 5) drawBlock(Offset(c * C, 270f), C, Neon.Purple)
    val p = (t * 0.7f) % 1f
    val y = 20f + easeOutCubic((p * 1.6f).coerceAtMost(1f)) * 190f
    piece(Piece.O, 0, 120f, y)
    // Ombra di atterraggio
    piece(Piece.O, 0, 120f, 210f, alpha = 0.25f)
    val fy = 70f + (p * 2f).coerceAtMost(1f) * 120f
    finger(Offset(240f, fy), pressed = p < 0.5f)
    drawLine(Neon.Cyan, Offset(240f, 40f), Offset(240f, fy - 30f), 4f, StrokeCap.Round, alpha = 0.4f)
}

@Composable
private fun LinesArt() = WorldArt { t ->
    val p = (t * 0.5f) % 1f
    val colors = listOf(Neon.Cyan, Neon.Pink, Neon.Yellow, Neon.Green, Neon.Orange)
    for (r in 0 until 4) {
        val y = 150f + r * C
        val full = p > 0.4f
        for (c in 0 until 10) {
            val filled = c != 9 || full
            if (!filled) continue
            val k = if (full) (1f - (p - 0.4f) / 0.3f).coerceIn(0f, 1f) else 1f
            if (k > 0f) drawBlock(Offset(c * C, y), C, colors[(r + c) % colors.size], alpha = k, flash = if (full) 1f - k else 0f)
        }
    }
    if (p <= 0.4f) piece(Piece.I, 1, 9 * C - 60f, 20f + p / 0.4f * 70f)
}

@Composable
private fun LevelArt() = WorldArt { t ->
    val pieces = Piece.entries
    val i = ((t * 0.8f).toInt()) % pieces.size
    drawRoundRect(Neon.TextDim, Offset(170f, 40f), androidx.compose.ui.geometry.Size(110f, 110f), androidx.compose.ui.geometry.CornerRadius(14f), alpha = 0.3f)
    drawPiecePreview(pieces[(i + 1) % pieces.size], 110f, Offset(170f, 40f))
    val y = 20f + ((t * 0.8f) % 1f) * 200f
    piece(pieces[i], 0, 30f, y)
    for (c in 0 until 10) if (c % 3 != 1) drawBlock(Offset(c * C, 270f), C, Neon.Teal)
}
