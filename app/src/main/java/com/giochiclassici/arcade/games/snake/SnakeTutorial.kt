package com.giochiclassici.arcade.games.snake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.TutorialPage
import com.giochiclassici.arcade.ui.rememberTime
import kotlin.math.min

fun snakeTutorial(): List<TutorialPage> = listOf(
    TutorialPage(
        "Scorri per girare",
        "Fai uno swipe su, giù, sinistra o destra in qualsiasi punto del campo per cambiare direzione.",
    ) { SwipeArt() },
    TutorialPage(
        "Mangia e cresci",
        "Ogni mela vale punti e allunga il serpente. Ogni 5 mele sali di livello: più punti, ma più velocità!",
    ) { FruitArt() },
    TutorialPage(
        "Stella dorata",
        "Ogni tanto appare una stella: raccoglila prima che il cerchio si esaurisca per un bonus di 50 punti × livello.",
    ) { StarArt() },
    TutorialPage(
        "Nessun muro",
        "Esci da un lato e rientri dal lato opposto! L'unico pericolo è mordersi la coda: lì la partita finisce.",
    ) { WrapArt() },
)

@Composable
private fun ArtCanvas(draw: DrawScope.(Float) -> Unit) {
    val t by rememberTime()
    Canvas(Modifier.fillMaxSize().padding(14.dp)) { draw(t) }
}

/** Polilinea di un serpente lungo [len] celle che segue la funzione di percorso [path]. */
private fun trail(head: Float, len: Int, path: (Float) -> Offset): List<Offset> =
    (0..len * 4).map { path(head - it * 0.25f) }

@Composable
private fun SwipeArt() = ArtCanvas { t ->
    val cell = size.width / 7f
    val phase = (t % 3f) / 3f
    val head = phase * 6f
    val pts = trail(head, 3) { p ->
        if (p < 3f) Offset(1f + p, 4.5f) else Offset(4f, 4.5f - (p - 3f))
    }.map { it * cell }
    drawSnake(pts, cell, t)
    val a = ((phase - 0.4f) * 4f).coerceIn(0f, 1f)
    if (phase in 0.35f..0.75f) {
        val base = Offset(size.width * 0.82f, size.height * 0.8f)
        val tip = base + Offset(0f, -size.height * 0.35f * a)
        drawLine(Neon.Cyan, base, tip, cell * 0.18f, StrokeCap.Round, alpha = 0.9f)
        drawCircle(Neon.Cyan, cell * 0.35f, tip, alpha = 0.5f)
        drawCircle(Color.White, cell * 0.2f, tip)
    }
}

@Composable
private fun FruitArt() = ArtCanvas { t ->
    val cell = size.width / 3f
    drawApple(Offset(size.width / 2, size.height * 0.5f), cell * 1.4f, t)
}

@Composable
private fun StarArt() = ArtCanvas { t ->
    val cell = size.width / 3.2f
    val left = 1f - (t % 4f) / 4f
    drawStar(Offset(size.width / 2, size.height / 2), cell, t, left)
}

@Composable
private fun WrapArt() = ArtCanvas { t ->
    val cols = 6f
    val cell = size.width / cols
    val w = size.width
    val h = size.height
    drawSnakeBoard(w, h, cell, t, 0.5f)
    val head = (t * 2.2f) % cols
    val pts = trail(head + 4f, 3) { p -> Offset(p, 3f) }.map { it * cell }
    clipRect(0f, 0f, w, h) {
        drawSnake(pts, cell, t, board = Size(w, h))
    }
    // Frecce che indicano l'uscita e il rientro
    val y = cell * 1.2f
    val a = 0.5f + 0.5f * kotlin.math.sin(t * 5f)
    drawLine(Neon.Cyan, Offset(w - cell * 1.1f, y), Offset(w - cell * 0.3f, y), cell * 0.1f, StrokeCap.Round, alpha = a)
    drawLine(Neon.Cyan, Offset(cell * 0.3f, y), Offset(cell * 1.1f, y), cell * 0.1f, StrokeCap.Round, alpha = a)
    drawLine(Neon.Cyan, Offset(cell * 1.1f, y), Offset(cell * 0.85f, y - cell * 0.22f), cell * 0.1f, StrokeCap.Round, alpha = a)
    drawLine(Neon.Cyan, Offset(cell * 1.1f, y), Offset(cell * 0.85f, y + cell * 0.22f), cell * 0.1f, StrokeCap.Round, alpha = a)
    drawLine(Neon.Cyan, Offset(w - cell * 0.3f, y), Offset(w - cell * 0.55f, y - cell * 0.22f), cell * 0.1f, StrokeCap.Round, alpha = a)
    drawLine(Neon.Cyan, Offset(w - cell * 0.3f, y), Offset(w - cell * 0.55f, y + cell * 0.22f), cell * 0.1f, StrokeCap.Round, alpha = min(1f, a))
}
