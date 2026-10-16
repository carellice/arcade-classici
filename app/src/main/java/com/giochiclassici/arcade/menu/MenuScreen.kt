package com.giochiclassici.arcade.menu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.core.Services
import com.giochiclassici.arcade.games.breakout.Brick
import com.giochiclassici.arcade.games.invaders.SHIP
import com.giochiclassici.arcade.games.invaders.SPRITES
import com.giochiclassici.arcade.games.invaders.drawSprite
import com.giochiclassici.arcade.games.breakout.drawBall
import com.giochiclassici.arcade.games.breakout.drawBrick
import com.giochiclassici.arcade.games.breakout.drawPaddle
import com.giochiclassici.arcade.games.mines.MineDiff
import com.giochiclassici.arcade.games.mines.NumberColors
import com.giochiclassici.arcade.games.mines.drawFlag
import com.giochiclassici.arcade.games.mines.drawHiddenTile
import com.giochiclassici.arcade.games.mines.drawRevealedTile
import com.giochiclassici.arcade.games.snake.drawApple
import com.giochiclassici.arcade.games.snake.drawSnake
import com.giochiclassici.arcade.ui.IconBtn
import com.giochiclassici.arcade.ui.Raised
import com.giochiclassici.arcade.ui.Surface3D
import com.giochiclassici.arcade.ui.drawRaised
import com.giochiclassici.arcade.ui.recessed
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.NeonIcons.gear
import com.giochiclassici.arcade.ui.NeonIcons.music
import com.giochiclassici.arcade.ui.NeonIcons.sound
import com.giochiclassici.arcade.ui.NeonIcons.vibrate
import com.giochiclassici.arcade.ui.NeonText
import com.giochiclassici.arcade.ui.SynthwaveBackground
import com.giochiclassici.arcade.ui.formatTime
import com.giochiclassici.arcade.update.Updater
import com.giochiclassici.arcade.ui.pressable
import com.giochiclassici.arcade.ui.rememberTime
import kotlin.math.abs
import kotlin.math.sin

enum class GameId(val title: String, val tagline: String, val color: Color) {
    SNAKE("SNAKE", "Scorri, mangia, cresci", Neon.Green),
    MINES("CAMPO MINATO", "Logica esplosiva", Neon.Cyan),
    BREAKOUT("BREAKOUT", "Distruggi ogni mattone", Neon.Pink),
    INVADERS("INVASORI", "Difendi la Terra dagli alieni", Neon.Purple),
}

@Composable
fun MenuScreen(onPlay: (GameId) -> Unit, onSettings: () -> Unit) {
    val prefs = Services.prefs
    val time by rememberTime()
    Box(Modifier.fillMaxSize()) {
        SynthwaveBackground()
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier.graphicsLayer { translationY = sin(time * 1.4f) * 6.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NeonText("ARCADE", Neon.Pink, 54.sp, letterSpacing = 6.sp, glow = 40f)
                NeonText("CLASSICI", Neon.Cyan, 24.sp, letterSpacing = 12.sp, glow = 24f)
            }
            Updater.availableVersion?.let { v -> UpdateBanner(v, onSettings) }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.weight(1f).widthIn(max = 500.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GameId.entries.forEachIndexed { i, g ->
                    val record = when (g) {
                        GameId.SNAKE -> prefs.best("best_snake").takeIf { it > 0 }?.let { "Record: $it" }
                        GameId.BREAKOUT -> prefs.best("best_breakout").takeIf { it > 0 }?.let { "Record: $it" }
                        GameId.INVADERS -> prefs.best("best_invaders").takeIf { it > 0 }?.let { "Record: $it" }
                        GameId.MINES -> MineDiff.entries.reversed().firstNotNullOfOrNull { d ->
                            prefs.best(d.bestKey).takeIf { it > 0 }?.let { "Record ${d.label}: ${formatTime(it)}" }
                        }
                    } ?: "Nuovo!"
                    GameCard(g, record, i) { onPlay(g) }
                }
            }
            Row(
                Modifier.padding(top = 6.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingToggle("Musica", prefs.music, { prefs.toggleMusic() }) { c -> music(c, prefs.music) }
                SettingToggle("Effetti", prefs.sfx, { prefs.toggleSfx() }) { c -> sound(c, prefs.sfx) }
                SettingToggle("Vibrazione", prefs.haptics, { prefs.toggleHaptics() }) { c -> vibrate(c, prefs.haptics) }
                Box {
                    SettingToggle("Impostazioni", true, onSettings) { c -> gear(c) }
                    if (Updater.availableVersion != null) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(end = 6.dp).size(14.dp)
                                .drawBehind {
                                    drawCircle(Neon.Bg0, size.minDimension / 2 + 2.dp.toPx())
                                    drawCircle(Neon.Yellow, size.minDimension / 2)
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onClick: () -> Unit) {
    Raised(Surface3D.solid(Neon.Yellow), Modifier.padding(top = 10.dp), corner = 18.dp, depth = 4.dp, onClick = onClick) {
        Text(
            "★ NUOVA VERSIONE $version DISPONIBILE",
            color = Neon.Bg0, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SettingToggle(label: String, on: Boolean, onClick: () -> Unit, icon: DrawScope.(Color) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconBtn(if (on) Neon.Cyan else Neon.TextDim, size = 50.dp, onClick = onClick, icon = icon)
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), color = Neon.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun GameCard(game: GameId, record: String, index: Int, onClick: () -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120L + index * 110L)
        enter.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    Raised(
        Surface3D.panel(game.color),
        Modifier
            .graphicsLayer {
                alpha = enter.value
                translationX = (1f - enter.value) * 60.dp.toPx() * (if (index % 2 == 0) -1 else 1)
            }
            .fillMaxWidth(),
        corner = 22.dp,
        depth = 8.dp,
        glow = 8.dp,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().height(106.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(86.dp).recessed(16.dp).padding(3.dp).clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { GamePreview(game) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                NeonText(game.title, game.color, 22.sp, glow = 18f, letterSpacing = 1.sp)
                Text(game.tagline, color = Neon.Text, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(record.uppercase(), color = Neon.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            PlayBadge(game.color)
        }
    }
}

/** Piccolo tasto "play" in rilievo (decorativo: il tocco è gestito dalla scheda). */
@Composable
private fun PlayBadge(color: Color) {
    Box(
        Modifier.size(42.dp, 46.dp).drawBehind {
            drawRaised(Surface3D.solid(color), 4.dp.toPx(), 0f, 21.dp.toPx())
        }.padding(bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.15f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.15f, size.height); close()
            }
            drawPath(p, Neon.Bg0)
        }
    }
}

@Composable
private fun GamePreview(game: GameId) {
    val time by rememberTime()
    val density = LocalDensity.current
    val m = rememberTextMeasurer()
    val nums = remember {
        (1..3).map {
            m.measure(it.toString(), TextStyle(fontSize = with(density) { 12.dp.toSp() }, fontWeight = FontWeight.Black, color = NumberColors[it]))
        }
    }
    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
        val t = time
        when (game) {
            GameId.SNAKE -> {
                val cell = size.width / 6f
                // Percorso ad anello su una griglia 6×6
                fun pos(p: Float): Offset {
                    val q = ((p % 16f) + 16f) % 16f
                    return when {
                        q < 4f -> Offset(1f + q, 1f)
                        q < 8f -> Offset(5f, 1f + (q - 4f))
                        q < 12f -> Offset(5f - (q - 8f), 5f)
                        else -> Offset(1f, 5f - (q - 12f))
                    }
                }
                val head = t * 3f
                val pts = (0..16).map { pos(head - it * 0.25f) * cell }
                drawApple(Offset(3f, 3f) * cell, cell * 1.3f, t)
                drawSnake(pts, cell, t)
            }
            GameId.MINES -> {
                val cell = size.width / 4f
                val order = listOf(5, 9, 6, 10, 1, 2, 4, 8, 13, 14, 0, 12)
                val shown = ((t * 3f) % 18f).toInt()
                val numbers = mapOf(1 to 1, 2 to 2, 4 to 1, 5 to 1, 6 to 3, 8 to 1, 9 to 2, 10 to 1, 13 to 1, 14 to 2)
                for (i in 0 until 16) {
                    val tl = Offset((i % 4) * cell, (i / 4) * cell)
                    val k = order.indexOf(i)
                    if (k in 0 until shown) {
                        drawRevealedTile(tl, cell, 1f)
                        numbers[i]?.let { n ->
                            val l = nums[n - 1]
                            drawText(l, topLeft = tl + Offset((cell - l.size.width) / 2, (cell - l.size.height) / 2), shadow = Shadow(NumberColors[n], Offset.Zero, 8f))
                        }
                    } else {
                        drawHiddenTile(tl, cell)
                        if (i == 7 || i == 11 && shown > 10) drawFlag(tl + Offset(cell / 2, cell / 2), cell, t + i)
                    }
                }
            }
            GameId.INVADERS -> {
                val p = size.width / 62f
                val frame = ((t * 2f).toInt()) % 2
                val fx = sin(t * 1.2f) * p * 6f
                for (r in 0 until 3) for (c in 0 until 4) {
                    val sp = SPRITES[if (r == 0) 0 else if (r == 1) 1 else 2]
                    val cx = size.width * (0.17f + c * 0.22f) + fx
                    drawSprite(sp, frame, Offset(cx - sp.w * p / 2, size.height * 0.06f + r * p * 11f), p, glow = false)
                }
                val shipX = size.width / 2 + sin(t * 0.9f) * size.width * 0.3f
                val by = size.height * (0.8f - ((t * 1.3f) % 1f) * 0.45f)
                drawLine(Neon.Cyan, Offset(shipX, by), Offset(shipX, by + p * 3f), p * 0.9f)
                drawSprite(SHIP, 0, Offset(shipX - SHIP.w * p / 2, size.height * 0.82f), p, glow = false)
            }
            GameId.BREAKOUT -> {
                val s = size.width / 100f
                scale(s, s, pivot = Offset.Zero) {
                    val colors = listOf(Neon.Pink, Neon.Yellow, Neon.Cyan)
                    for (r in 0 until 3) for (c in 0 until 5) {
                        if ((r + c + (t * 1.5f).toInt()) % 7 == 0) continue
                        drawBrick(Brick(4f + c * 19f, 6f + r * 10f, 16f, 7f, 1, 1, false, colors[r], 0f))
                    }
                    val bx = 10f + tri(t * 0.45f) * 80f
                    val by = 42f + tri(t * 0.8f) * 42f
                    drawBall(Offset(bx, by), 3.5f, emptyList(), false)
                    drawPaddle(bx.coerceIn(14f, 86f), 92f, 26f, 5f)
                }
            }
        }
    }
}

private fun tri(x: Float): Float {
    val f = x % 1f
    return if (f < 0.5f) f * 2f else 2f - f * 2f
}
