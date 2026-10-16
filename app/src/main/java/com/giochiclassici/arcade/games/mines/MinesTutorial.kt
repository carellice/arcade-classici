package com.giochiclassici.arcade.games.mines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.TutorialPage
import com.giochiclassici.arcade.ui.rememberTime

fun minesTutorial(): List<TutorialPage> = listOf(
    TutorialPage(
        "L'obiettivo",
        "Scopri tutte le caselle che NON nascondono una mina. Tranquillo: il primo tocco è sempre sicuro.",
    ) { GoalArt() },
    TutorialPage(
        "Leggi i numeri",
        "Un numero indica quante mine toccano quella casella, contando anche le diagonali.",
    ) { NumberArt() },
    TutorialPage(
        "Pianta le bandierine",
        "Tieni premuto su una casella (o attiva la modalità Bandiera) per segnare una mina sospetta.",
    ) { FlagArt() },
    TutorialPage(
        "Apertura rapida",
        "Tocca un numero che ha già tutte le sue bandierine: le altre caselle intorno si aprono in un colpo!",
    ) { ChordArt() },
)

private class Grid3(val cell: Float, val origin: Offset) {
    fun tl(x: Int, y: Int) = origin + Offset(x * cell, y * cell)
    fun c(x: Int, y: Int) = tl(x, y) + Offset(cell / 2, cell / 2)
}

@Composable
private fun TileArt(draw: DrawScope.(t: Float, g: Grid3, nums: List<TextLayoutResult>) -> Unit) {
    val time by rememberTime()
    val density = LocalDensity.current
    val m = rememberTextMeasurer()
    val nums = remember {
        (0..8).map {
            m.measure(
                it.toString(),
                TextStyle(fontSize = with(density) { 26.dp.toSp() }, fontWeight = FontWeight.Black, color = NumberColors[it]),
            )
        }
    }
    Canvas(Modifier.fillMaxSize().padding(16.dp)) {
        val cell = size.minDimension / 3f
        draw(time, Grid3(cell, Offset((size.width - cell * 3) / 2, (size.height - cell * 3) / 2)), nums)
    }
}

private fun DrawScope.num(g: Grid3, x: Int, y: Int, n: Int, nums: List<TextLayoutResult>) {
    drawRevealedTile(g.tl(x, y), g.cell, 1f)
    if (n > 0) {
        val l = nums[n]
        drawText(l, topLeft = g.c(x, y) - Offset(l.size.width / 2f, l.size.height / 2f), shadow = Shadow(NumberColors[n], Offset.Zero, 12f))
    }
}

@Composable
private fun GoalArt() = TileArt { t, g, nums ->
    val layout = listOf(0, 1, 1, 0, 1, -1, 0, 1, 1)
    val shown = ((t % 4f) / 0.35f).toInt()
    for (i in 0 until 9) {
        val x = i % 3; val y = i / 3
        if (layout[i] >= 0 && i < shown) num(g, x, y, layout[i], nums)
        else {
            drawHiddenTile(g.tl(x, y), g.cell)
            if (layout[i] < 0 && shown > 9) drawFlag(g.c(x, y), g.cell, t)
        }
    }
}

@Composable
private fun NumberArt() = TileArt { t, g, nums ->
    val mines = setOf(0, 2, 7)
    for (i in 0 until 9) {
        val x = i % 3; val y = i / 3
        when {
            i == 4 -> num(g, x, y, 3, nums)
            i in mines -> { drawRevealedTile(g.tl(x, y), g.cell, 1f, danger = (t % 1.2f) < 0.6f); drawMine(g.c(x, y), g.cell, t) }
            else -> drawHiddenTile(g.tl(x, y), g.cell)
        }
    }
    drawCircle(Neon.Cyan, g.cell * 0.55f, g.c(1, 1), alpha = 0.6f, style = Stroke(3f))
}

@Composable
private fun FlagArt() = TileArt { t, g, _ ->
    val cycle = t % 2.4f
    for (i in 0 until 9) drawHiddenTile(g.tl(i % 3, i / 3), g.cell)
    val c = g.c(1, 1)
    if (cycle < 1f) {
        val p = cycle
        drawCircle(Neon.Pink, g.cell * 0.5f * p, c, alpha = 0.3f)
        drawArc(
            Neon.Pink, -90f, 360f * p, false, c - Offset(g.cell * 0.45f, g.cell * 0.45f),
            androidx.compose.ui.geometry.Size(g.cell * 0.9f, g.cell * 0.9f), style = Stroke(5f),
        )
        drawCircle(androidx.compose.ui.graphics.Color.White, g.cell * 0.14f, c, alpha = 0.9f)
    } else {
        drawFlag(c, g.cell, t)
    }
}

@Composable
private fun ChordArt() = TileArt { t, g, nums ->
    val cycle = t % 3f
    for (i in 0 until 9) {
        val x = i % 3; val y = i / 3
        when {
            i == 4 -> num(g, x, y, 1, nums)
            i == 0 -> { drawHiddenTile(g.tl(x, y), g.cell); drawFlag(g.c(x, y), g.cell, t) }
            cycle > 1.2f && cycle - 1.2f > (i % 3 + i / 3) * 0.12f -> num(g, x, y, listOf(0, 1, 1, 1, 0, 1, 1, 1, 0)[i], nums)
            else -> drawHiddenTile(g.tl(x, y), g.cell)
        }
    }
    if (cycle in 0.6f..1.3f) {
        drawCircle(androidx.compose.ui.graphics.Color.White, g.cell * 0.18f, g.c(1, 1) + Offset(g.cell * 0.2f, g.cell * 0.25f), alpha = 0.8f)
        drawCircle(Neon.Cyan, g.cell * 0.4f * (cycle - 0.6f) / 0.7f + g.cell * 0.2f, g.c(1, 1), alpha = 0.4f, style = Stroke(4f))
    }
}
