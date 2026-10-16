package com.giochiclassici.arcade.games.breakout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.TutorialPage
import com.giochiclassici.arcade.ui.rememberTime
import kotlin.math.abs
import kotlin.math.sin

fun breakoutTutorial(): List<TutorialPage> = listOf(
    TutorialPage(
        "Muovi la racchetta",
        "Trascina il dito in qualsiasi punto dello schermo per spostare la racchetta. Tocca per lanciare la pallina.",
    ) { MoveArt() },
    TutorialPage(
        "Controlla l'angolo",
        "Il punto in cui la pallina colpisce la racchetta decide la direzione: i bordi la mandano più di lato.",
    ) { AngleArt() },
    TutorialPage(
        "Mattoni e combo",
        "Alcuni mattoni servono più colpi, quelli d'acciaio sono indistruttibili. Più mattoni rompi di fila, più punti fai!",
    ) { BricksArt() },
    TutorialPage(
        "Potenziamenti",
        "W racchetta larga · M multi-palla · S rallenta · F palla di fuoco · ♥ vita extra. Prendili al volo!",
    ) { PowersArt() },
)

/** Canvas in unità "mondo" 300×300. */
@Composable
private fun WorldArt(draw: DrawScope.(Float) -> Unit) {
    val t by rememberTime()
    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
        val s = size.minDimension / 300f
        scale(s, s, pivot = Offset.Zero) { draw(t) }
    }
}

@Composable
private fun MoveArt() = WorldArt { t ->
    val x = 150f + sin(t * 1.6f) * 90f
    drawPaddle(x, 230f, 110f, 18f)
    drawBall(Offset(x, 211f), 10f, emptyList(), false)
    val fx = x + 10f
    drawCircle(Neon.Cyan, 26f, Offset(fx, 275f), alpha = 0.25f)
    drawCircle(Color.White, 12f, Offset(fx, 275f), alpha = 0.9f)
    drawLine(Neon.Cyan, Offset(fx - 50f, 275f), Offset(fx - 30f, 275f), 4f, StrokeCap.Round, alpha = 0.5f)
    drawLine(Neon.Cyan, Offset(fx + 30f, 275f), Offset(fx + 50f, 275f), 4f, StrokeCap.Round, alpha = 0.5f)
}

@Composable
private fun AngleArt() = WorldArt { t ->
    val px = 150f; val py = 240f; val w = 150f
    drawPaddle(px, py, w, 18f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), -t * 40f)
    val rels = listOf(-0.9f, -0.4f, 0f, 0.4f, 0.9f)
    val active = ((t * 0.8f).toInt()) % rels.size
    for ((i, rel) in rels.withIndex()) {
        val hit = Offset(px + rel * w / 2, py - 19f)
        val ang = rel * 1.08f
        val end = hit + Offset(sin(ang), -kotlin.math.cos(ang)) * 170f
        val col = if (i == active) Neon.Yellow else Neon.TextDim
        drawLine(col, hit, end, if (i == active) 5f else 3f, pathEffect = dash, alpha = if (i == active) 1f else 0.4f)
        if (i == active) drawBall(hit + (end - hit) * ((t * 0.8f) % 1f), 10f, emptyList(), false)
    }
}

@Composable
private fun BricksArt() = WorldArt { t ->
    val w = 120f; val h = 40f
    val items = listOf(
        Brick(20f, 40f, w, h, 1, 1, false, Neon.Green, 0f),
        Brick(160f, 40f, w, h, 2, 2, false, Neon.Orange, 0f),
        Brick(20f, 110f, w, h, 3, 3, false, Neon.Pink, 0f),
        Brick(160f, 110f, w, h, 1, 1, true, Color.Gray, 0f),
    )
    items[1].hp = if ((t % 2f) > 1f) 1 else 2
    for (b in items) drawBrick(b)
    val bounce = abs(sin(t * 2.2f))
    drawBall(Offset(150f, 250f - bounce * 70f), 10f, emptyList(), false)
}

@Composable
private fun PowersArt() {
    val m = rememberTextMeasurer()
    val labels = remember {
        PowerType.entries.associateWith { m.measure(it.label, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = Neon.Bg0)) }
    }
    val t by rememberTime()
    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
        val s = size.minDimension / 300f
        val types = PowerType.entries
        for ((i, type) in types.withIndex()) {
            val x = 40f + i * 55f
            val y = 40f + ((t * 70f + i * 47f) % 200f)
            val c = Offset(x * s, y * s)
            scale(s * 0.85f, s * 0.85f, pivot = Offset.Zero) {
                drawCapsule(c / (s * 0.85f), type, t)
            }
            val l = labels.getValue(type)
            drawText(l, topLeft = c - Offset(l.size.width / 2f, l.size.height / 2f))
        }
        scale(s, s, pivot = Offset.Zero) { drawPaddle(150f, 270f, 200f, 16f) }
    }
}
