package com.giochiclassici.arcade.games.invaders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.core.AppState
import com.giochiclassici.arcade.core.Services
import com.giochiclassici.arcade.ui.GameLoop
import com.giochiclassici.arcade.ui.GameTopBar
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.NeonText
import com.giochiclassici.arcade.ui.ResultPanel
import com.giochiclassici.arcade.ui.StatBox
import com.giochiclassici.arcade.ui.TutorialOverlay
import kotlin.math.ceil
import kotlin.random.Random

private const val BEST_KEY = "best_invaders"

@Composable
fun InvadersScreen(onExit: () -> Unit) {
    val prefs = Services.prefs
    val accent = Neon.Purple
    var showTutorial by remember { mutableStateOf(!prefs.tutorialSeen("invaders")) }
    var best by remember { mutableStateOf(prefs.best(BEST_KEY)) }
    var newRecord by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val density = LocalDensity.current
            val canvasW = with(density) { maxWidth.toPx() }
            val canvasH = with(density) { (maxHeight - 68.dp).toPx() }
            val game = remember { InvadersGame(canvasH / canvasW * 1000f) }

            val fg by AppState.foreground
            LaunchedEffect(fg) { if (!fg) game.pause() }
            GameLoop { dt -> game.update(dt, frozen = showTutorial) }
            LaunchedEffect(game.phase) {
                if (game.phase == IPhase.OVER) {
                    newRecord = prefs.submitHigh(BEST_KEY, game.score)
                    best = prefs.best(BEST_KEY)
                }
            }
            BackHandler { if (game.phase == IPhase.PLAYING) game.pause() else onExit() }

            Column(Modifier.fillMaxSize()) {
                GameTopBar(
                    accent = accent,
                    onBack = onExit,
                    onHelp = { game.pause(); showTutorial = true },
                    onPause = { game.pause() },
                ) {
                    StatBox("Punti", game.score.toString(), accent)
                    StatBox("Ondata", game.wave.toString(), Neon.Cyan)
                    StatBox("Vite", "♥".repeat(game.lives.coerceIn(0, 5)).ifEmpty { "-" }, Neon.Red)
                }
                Box(Modifier.fillMaxSize()) {
                    InvadersBoard(game)
                    if (game.phase == IPhase.READY && !showTutorial) {
                        ReadyBanner(game.wave, Modifier.align(Alignment.Center))
                    }
                    WaveBanner(game.phase == IPhase.WAVE_CLEAR, game.wave, Modifier.align(Alignment.Center))
                    ActivePowers(game, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                }
            }

            ResultPanel(
                visible = game.phase == IPhase.PAUSED && !showTutorial,
                accent = accent,
                title = "PAUSA",
                lines = listOf("Punti" to game.score.toString(), "Ondata" to game.wave.toString()),
                highlight = null,
                primary = "Riprendi" to { game.resume() },
                tertiary = "Ricomincia" to { newRecord = false; game.restart() },
                secondary = "Menu" to onExit,
            )
            ResultPanel(
                visible = game.showResult,
                accent = Neon.Pink,
                title = if (game.invaded) "INVASIONE!" else "GAME OVER",
                lines = listOf("Punti" to game.score.toString(), "Ondata" to game.wave.toString(), "Record" to best.toString()),
                highlight = if (newRecord) "★ NUOVO RECORD! ★" else if (game.invaded) "Gli alieni sono atterrati" else null,
                primary = "Riprova" to { newRecord = false; game.restart() },
                secondary = "Menu" to onExit,
            )
            TutorialOverlay(
                visible = showTutorial,
                accent = accent,
                gameTitle = "INVASORI",
                pages = invadersTutorial(),
                onDone = { showTutorial = false; prefs.markTutorialSeen("invaders") },
            )
        }
    }
}

@Composable
private fun ReadyBanner(wave: Int, modifier: Modifier) {
    val a by rememberInfiniteTransition(label = "ready").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a",
    )
    Column(modifier.padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NeonText("ONDATA $wave", Neon.Cyan, 30.sp, textAlign = TextAlign.Center)
        NeonText("TOCCA PER INIZIARE", Neon.Text, 16.sp, Modifier.graphicsLayer { alpha = a }, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WaveBanner(visible: Boolean, wave: Int, modifier: Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NeonText("ONDATA $wave", Neon.Cyan, 26.sp, textAlign = TextAlign.Center)
            NeonText("SUPERATA!", Neon.Yellow, 34.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActivePowers(game: InvadersGame, modifier: Modifier) {
    game.frame
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (game.tripleT > 0f) PowerLabel("TRIPLO ${game.tripleT.toInt() + 1}s", Neon.Cyan)
        if (game.rapidT > 0f) PowerLabel("RAFFICA ${game.rapidT.toInt() + 1}s", Neon.Orange)
        if (game.bubble) PowerLabel("SCUDO ${game.bubbleT.toInt() + 1}s", Neon.Green)
    }
}

@Composable
private fun PowerLabel(text: String, color: Color) =
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)

/** Cache delle bitmap degli sprite, rigenerata quando cambia la scala. */
private class InvadersCache {
    var scale = -1f
    var aliens: List<List<SpriteImage>> = emptyList()
    var ship: SpriteImage? = null
    var ufo: List<SpriteImage> = emptyList()
    var shields: ImageBitmap? = null
    var shieldVersion = -1

    fun ensure(s: Float, density: Float) {
        if (s == scale) return
        scale = s
        val p = InvadersGame.PIXEL * s
        aliens = SPRITES.map { sp -> (0 until 2).map { renderSprite(sp, it, p, density) } }
        ship = renderSprite(SHIP, 0, p, density)
        ufo = (0 until 2).map { renderSprite(UFO, it, p, density) }
        shieldVersion = -1
    }
}

private class Star(val x: Float, val y: Float, val speed: Float, val size: Float, val tw: Float)

@Composable
private fun InvadersBoard(game: InvadersGame) {
    val measurer = rememberTextMeasurer()
    val labels = remember {
        IPower.entries.associateWith { measurer.measure(it.label, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Neon.Bg0)) }
    }
    val stars = remember {
        val rnd = Random(3)
        List(140) { i ->
            val layer = i % 3
            Star(rnd.nextFloat(), rnd.nextFloat(), 0.006f + layer * 0.012f, 0.8f + layer * 0.7f, rnd.nextFloat() * 6f)
        }
    }
    val cache = remember { InvadersCache() }
    val scaleRef = remember { floatArrayOf(1f) }
    Canvas(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(game) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    game.start()
                    var lastX = down.position.x
                    while (true) {
                        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        game.moveShip((ch.position.x - lastX) / scaleRef[0])
                        lastX = ch.position.x
                        ch.consume()
                    }
                }
            },
    ) {
        game.frame
        val s = size.width / game.W
        scaleRef[0] = s
        cache.ensure(s, density)
        val t = game.clock

        // Spazio profondo con stelle in parallasse
        drawRect(Brush.verticalGradient(listOf(Color(0xFF05031A), Color(0xFF120836), Color(0xFF07041A))))
        drawCircle(
            Brush.radialGradient(listOf(Neon.Purple.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.15f), size.width * 0.7f),
            size.width * 0.7f, Offset(size.width * 0.85f, size.height * 0.15f),
        )
        for (st in stars) {
            val y = ((st.y + t * st.speed) % 1f) * size.height
            val a = 0.45f + 0.55f * (0.5f + 0.5f * kotlin.math.sin(t * 2f + st.tw))
            drawCircle(Color.White, st.size * density * 0.7f, Offset(st.x * size.width, y), alpha = a * (0.4f + st.speed * 25f).coerceAtMost(1f))
        }
        // Terra
        val groundY = (game.shipY + game.shipH / 2 + 34f) * s
        drawRect(
            Brush.verticalGradient(listOf(Neon.Green.copy(alpha = 0.18f), Color.Transparent), groundY, groundY + 60f * s),
            Offset(0f, groundY), Size(size.width, 60f * s),
        )
        drawLine(Neon.Green, Offset(0f, groundY), Offset(size.width, groundY), 3f * s)

        val sh = game.shake * 14f * s
        val sx = if (sh > 0f) (Random.nextFloat() - 0.5f) * sh else 0f
        val sy = if (sh > 0f) (Random.nextFloat() - 0.5f) * sh else 0f
        translate(sx, sy) {
            // Scudi (bitmap rigenerata solo quando cambiano)
            if (cache.shieldVersion != game.shieldVersion || cache.shields == null) {
                val h = ceil((game.shieldRows * game.shieldCell + 4f) * s).toInt()
                val img = cache.shields?.takeIf { it.width == size.width.toInt() && it.height == h }
                    ?: ImageBitmap(size.width.toInt(), h)
                val cnv = Canvas(img)
                CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, cnv, Size(img.width.toFloat(), img.height.toFloat())) {
                    drawRect(Color.Transparent, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                    translate(0f, -game.shieldY * s) { drawShieldCells(game, s) }
                }
                cache.shields = img
                cache.shieldVersion = game.shieldVersion
            }
            cache.shields?.let { drawImage(it, Offset(0f, game.shieldY * s)) }

            // Alieni
            for (i in game.alive.indices) {
                if (!game.alive[i]) continue
                val c = i % game.cols; val r = i / game.cols
                val type = game.type(r)
                val img = cache.aliens[type][game.animFrame]
                val sp = SPRITES[type]
                val x = (game.alienCenterX(c) - sp.w * InvadersGame.PIXEL / 2) * s
                val y = game.alienTop(r) * s
                drawImage(img.image, Offset(x - img.pad, y - img.pad))
            }
            // UFO
            if (game.ufoActive) {
                val img = cache.ufo[((t * 6f).toInt()) % 2]
                val x = (game.ufoX - UFO.w * InvadersGame.PIXEL / 2) * s
                drawImage(img.image, Offset(x - img.pad, game.ufoY * s - img.pad))
            }
            // Potenziamenti
            for (d in game.drops) {
                val cpos = Offset(d.x * s, d.y * s)
                drawPowerPill(cpos, d.type.color, s, t)
                val l = labels.getValue(d.type)
                drawText(l, topLeft = cpos - Offset(l.size.width / 2f, l.size.height / 2f))
            }
            for (shot in game.shots) drawShot(shot, s, t)

            // Navicella
            if (game.shipAlive) {
                val blink = game.invulnT > 0f && ((t * 12f).toInt() % 2 == 0)
                val ship = cache.ship!!
                val x = (game.shipX - game.shipW / 2) * s
                val y = (game.shipY - game.shipH / 2) * s
                drawFlame(game.shipX * s, y + game.shipH * s, s, t)
                drawImage(ship.image, Offset(x - ship.pad, y - ship.pad), alpha = if (blink) 0.35f else 1f)
                if (game.bubble) {
                    val r = game.shipW * 0.85f * s
                    val pulse = 0.5f + 0.5f * kotlin.math.sin(t * 6f)
                    drawCircle(Neon.Green.copy(alpha = 0.10f + 0.06f * pulse), r, Offset(game.shipX * s, game.shipY * s))
                    drawCircle(Neon.Green.copy(alpha = 0.7f), r, Offset(game.shipX * s, game.shipY * s), style = Stroke(3f * s))
                }
            }
            // Esplosioni
            for (b in game.blasts) {
                val p = 1f - b.life / 0.35f
                val c = Offset(b.x * s, b.y * s)
                drawCircle(b.color.copy(alpha = (1f - p).coerceIn(0f, 1f) * 0.8f), b.size * s * (0.3f + p), c, style = Stroke(6f * s * (1f - p).coerceAtLeast(0.1f)))
                if (p < 0.4f) drawCircle(Color.White.copy(alpha = (0.4f - p) * 2f), b.size * s * 0.35f, c)
            }
            game.particles.draw(this, s)
            for (pp in game.popups) {
                val style = TextStyle(
                    color = pp.color.copy(alpha = pp.life.coerceIn(0f, 1f)), fontSize = 20.sp, fontWeight = FontWeight.Black,
                    shadow = Shadow(pp.color, Offset.Zero, 14f),
                )
                val l = measurer.measure(pp.text, style)
                drawText(l, topLeft = Offset(pp.x * s - l.size.width / 2f, pp.y * s - l.size.height / 2f))
            }
        }
    }
}
