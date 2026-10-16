package com.giochiclassici.arcade.games.invaders

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.TutorialPage
import com.giochiclassici.arcade.ui.rememberTime
import kotlin.math.sin

fun invadersTutorial(): List<TutorialPage> = listOf(
    TutorialPage(
        "Muovi la navicella",
        "Trascina il dito in qualsiasi punto per spostarti a destra e sinistra. La navicella spara da sola!",
    ) { MoveArt() },
    TutorialPage(
        "Gli invasori",
        "Ogni alieno vale punti diversi. Più ne abbatti, più gli altri accelerano: non lasciarli arrivare a terra!",
    ) { AliensArt() },
    TutorialPage(
        "Scudi e UFO",
        "Ripàrati dietro gli scudi verdi: si sgretolano a ogni colpo. Abbatti l'UFO rosso per un bonus misterioso.",
    ) { ShieldArt() },
    TutorialPage(
        "Potenziamenti",
        "Alcuni alieni lasciano cadere capsule: T colpo triplo · R raffica · S scudo che para un colpo.",
    ) { PowersArt() },
)

@Composable
private fun MoveArt() {
    val t by rememberTime()
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val p = size.width / 40f
        val x = size.width / 2 + sin(t * 1.5f) * size.width * 0.28f
        val shipTop = size.height * 0.68f
        // Colpi verso l'alto
        for (k in 0 until 3) {
            val f = ((t * 1.6f + k / 3f) % 1f)
            val bx = size.width / 2 + sin((t - f * 0.6f) * 1.5f) * size.width * 0.28f
            val by = shipTop - f * size.height * 0.62f
            drawLine(Neon.Cyan.copy(alpha = 0.35f), Offset(bx, by), Offset(bx, by + p * 4), p * 2.2f, StrokeCap.Round)
            drawLine(Neon.Cyan, Offset(bx, by), Offset(bx, by + p * 4), p, StrokeCap.Round)
        }
        drawSprite(SHIP, 0, Offset(x - SHIP.w * p / 2, shipTop), p)
        drawFlame(x, shipTop + SHIP.h * p, p / 6.5f, t)
        val fy = size.height * 0.94f
        drawCircle(Neon.Purple, p * 3.2f, Offset(x, fy), alpha = 0.3f)
        drawCircle(Color.White, p * 1.6f, Offset(x, fy))
    }
}

@Composable
private fun AliensArt() {
    val t by rememberTime()
    val m = rememberTextMeasurer()
    val labels = remember {
        listOf("30" to Neon.Pink, "20" to Neon.Cyan, "10" to Neon.Green).map { (txt, c) ->
            m.measure("= $txt", TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = c, shadow = Shadow(c, Offset.Zero, 12f)))
        }
    }
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val p = size.width / 26f
        val frame = ((t * 2f).toInt()) % 2
        for ((i, sp) in SPRITES.withIndex()) {
            val y = size.height * (0.06f + i * 0.33f)
            drawSprite(sp, frame, Offset(size.width * 0.32f - sp.w * p / 2, y), p)
            val l = labels[i]
            drawText(l, topLeft = Offset(size.width * 0.58f, y + sp.h * p / 2 - l.size.height / 2f))
        }
    }
}

@Composable
private fun ShieldArt() {
    val t by rememberTime()
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val p = size.width / 34f
        // UFO che attraversa
        val ux = ((t * 0.35f) % 1.4f - 0.2f) * size.width
        drawSprite(UFO, ((t * 6f).toInt()) % 2, Offset(ux - UFO.w * p / 2, size.height * 0.04f), p)
        // Scudo con un "morso" che si allarga e si ricompone
        val c = p * 1.4f
        val cols = 18; val rows = 11
        val left = (size.width - cols * c) / 2
        val top = size.height * 0.52f
        val bite = ((t % 3f) / 3f) * 4f
        for (pass in 0..1) for (cy in 0 until rows) for (cx in 0 until cols) {
            if (cy < 3 && (cx < 3 - cy || cx > cols - 1 - (3 - cy))) continue
            if (cy >= 7 && cx in 6..11) continue
            if (cy == 6 && cx in 7..10) continue
            val dx = cx - 5f; val dy = cy.toFloat()
            if (dx * dx + dy * dy < bite * bite) continue
            val x = left + cx * c; val y = top + cy * c
            if (pass == 0) drawRect(Color(0xFF0B5A33), Offset(x, y + c * 0.3f), Size(c, c))
            else drawRect(lerp(Color(0xFF8BFFB0), Color(0xFF22C766), cy / rows.toFloat()), Offset(x, y), Size(c, c * 0.92f))
        }
        // Saetta aliena in arrivo
        val sy = top - c * 6 + ((t % 3f) / 3f) * c * 6
        drawLine(Neon.Yellow, Offset(left + 5.5f * c, sy - c * 3), Offset(left + 5.5f * c, sy), p * 0.8f, StrokeCap.Round)
    }
}

@Composable
private fun PowersArt() {
    val t by rememberTime()
    val m = rememberTextMeasurer()
    val labels = remember {
        IPower.entries.associateWith { m.measure(it.label, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Neon.Bg0)) }
    }
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val s = size.width / 300f
        for ((i, type) in IPower.entries.withIndex()) {
            val x = size.width * (0.22f + i * 0.28f)
            val y = size.height * 0.1f + ((t * 0.45f + i * 0.31f) % 1f) * size.height * 0.55f
            drawPowerPill(Offset(x, y), type.color, s * 1.3f, t)
            val l = labels.getValue(type)
            drawText(l, topLeft = Offset(x - l.size.width / 2f, y - l.size.height / 2f))
        }
        val p = size.width / 40f
        drawSprite(SHIP, 0, Offset(size.width / 2 - SHIP.w * p / 2, size.height * 0.76f), p)
        drawCircle(Neon.Green.copy(alpha = 0.7f), SHIP.w * p * 0.75f, Offset(size.width / 2, size.height * 0.76f + SHIP.h * p / 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
    }
}
