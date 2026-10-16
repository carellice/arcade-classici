package com.giochiclassici.arcade.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class TutorialPage(val title: String, val text: String, val art: @Composable () -> Unit)

/** Guida a pagine con illustrazioni animate. */
@Composable
fun TutorialOverlay(
    visible: Boolean,
    accent: Color,
    gameTitle: String,
    pages: List<TutorialPage>,
    onDone: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible) { if (visible) page = 0 }
    OverlayPanel(visible, accent) {
        Text(
            "COME SI GIOCA · $gameTitle",
            color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(260)) { it * dir / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it * dir / 3 } + fadeOut(tween(200)))
            },
            label = "tutorial",
        ) { i ->
            val p = pages[i]
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(170.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Neon.Bg0.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) { p.art() }
                NeonText(p.title, accent, 22.sp, textAlign = TextAlign.Center, glow = 16f, letterSpacing = 1.sp)
                Text(
                    p.text,
                    color = Neon.Text, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.height(86.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            pages.indices.forEach { i ->
                val w by animateDpAsState(if (i == page) 22.dp else 8.dp, label = "dot")
                Box(
                    Modifier.height(8.dp).width(w).clip(CircleShape)
                        .background(if (i == page) accent else Neon.TextDim.copy(alpha = 0.4f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) {
                NeonButton("Indietro", Neon.TextDim, Modifier.weight(1f)) { page-- }
            } else {
                NeonButton("Salta", Neon.TextDim, Modifier.weight(1f)) { onDone() }
            }
            val last = page == pages.lastIndex
            NeonButton(if (last) "Gioca!" else "Avanti", accent, Modifier.weight(1f), filled = true) {
                if (last) onDone() else page++
            }
        }
    }
}
