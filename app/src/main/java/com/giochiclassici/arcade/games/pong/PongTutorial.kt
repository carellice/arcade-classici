package com.giochiclassici.arcade.games.pong

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.TutorialPage
import com.giochiclassici.arcade.ui.rememberTime
import kotlin.math.sin

fun pongTutorial(): List<TutorialPage> = listOf(
    TutorialPage("Il duello originale", "Rimanda la pallina oltre la racchetta avversaria. Ogni errore vale un punto: il primo a 11 vince!") {
        PongTutorialArt(false)
    },
    TutorialPage("Solo o in compagnia", "1vCPU: tre difficoltà. 1v1: giocate insieme sullo stesso schermo, G1 a sinistra e G2 a destra.") {
        PongTutorialArt(true)
    },
    TutorialPage("Trova l'angolo", "Trascina su e giù. Colpisci con i bordi per cambiare angolo: ogni scambio accelera la pallina!") {
        PongTutorialArt(true)
    },
)

@Composable
private fun PongTutorialArt(controls: Boolean) {
    val time by rememberTime()
    Canvas(Modifier.fillMaxSize().padding(18.dp)) {
        drawPongPreview(time)
        if (controls) {
            for ((x, color) in listOf(size.width * 0.22f to Neon.Teal, size.width * 0.78f to Neon.Pink)) {
                val y = size.height * (0.5f + sin(time * 2.2f) * 0.3f)
                drawLine(color.copy(alpha = 0.25f), Offset(x, size.height * 0.15f), Offset(x, size.height * 0.85f), 4f)
                drawCircle(color.copy(alpha = 0.25f), 16.dp.toPx(), Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.8f), 7.dp.toPx(), Offset(x, y))
            }
        }
    }
}
