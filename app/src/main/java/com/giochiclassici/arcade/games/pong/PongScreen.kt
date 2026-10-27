package com.giochiclassici.arcade.games.pong

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.focusable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Sfx
import com.giochiclassici.arcade.core.AppState
import com.giochiclassici.arcade.core.Haptics
import com.giochiclassici.arcade.core.Services
import com.giochiclassici.arcade.ui.GameBackground
import com.giochiclassici.arcade.ui.GameLoop
import com.giochiclassici.arcade.ui.GameTopBar
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.NeonButton
import com.giochiclassici.arcade.ui.NeonText
import com.giochiclassici.arcade.ui.OverlayPanel
import com.giochiclassici.arcade.ui.Raised
import com.giochiclassici.arcade.ui.ResultPanel
import com.giochiclassici.arcade.ui.StatBox
import com.giochiclassici.arcade.ui.Surface3D
import com.giochiclassici.arcade.ui.TutorialOverlay
import com.giochiclassici.arcade.ui.rememberTime

private data class PongHud(
    val phase: PongPhase, val left: Int, val right: Int, val countdown: Int, val rally: Int,
)
private fun PongGame.hud() = PongHud(phase, leftScore, rightScore, countdown, rally)

@Composable
fun PongScreen(onExit: () -> Unit) {
    val prefs = Services.prefs
    var showTutorial by remember { mutableStateOf(!prefs.tutorialSeen("pong")) }
    var mode by remember { mutableStateOf(PongMode.CPU) }
    var difficulty by remember { mutableStateOf(PongDifficulty.NORMAL) }
    val game = remember {
        PongGame { event ->
            when (event) {
                PongEvent.PADDLE -> { Audio.play(Sfx.Paddle); Haptics.tick() }
                PongEvent.WALL -> Audio.play(Sfx.Wall, volume = 0.6f)
                PongEvent.POINT -> { Audio.play(Sfx.Bonus); Haptics.bump() }
                PongEvent.WIN -> { Audio.play(Sfx.Win); Haptics.heavy() }
            }
        }
    }
    var hud by remember { mutableStateOf(game.hud()) }
    val frame = remember { mutableLongStateOf(0L) }
    val keys = remember { mutableSetOf<Key>() }
    val focus = remember { FocusRequester() }
    val foreground by AppState.foreground
    fun sync() { hud = game.hud() }
    val pause = { game.pause(); keys.clear(); sync() }
    val setup = { game.setup(); keys.clear(); sync() }
    val start = { game.start(mode, difficulty); keys.clear(); Audio.play(Sfx.Start); sync() }

    LaunchedEffect(foreground) { if (!foreground) pause() }
    LaunchedEffect(hud.phase, showTutorial) {
        if (!showTutorial && (hud.phase == PongPhase.SERVING || hud.phase == PongPhase.PLAYING)) focus.requestFocus()
    }
    GameLoop { dt ->
        if (!showTutorial && foreground) {
            val distance = 950f * dt
            val leftDirection = (if (Key.S in keys) 1 else 0) - (if (Key.W in keys) 1 else 0)
            val arrows = (if (Key.DirectionDown in keys) 1 else 0) - (if (Key.DirectionUp in keys) 1 else 0)
            game.movePaddle(true, game.leftY + (leftDirection + if (game.mode == PongMode.CPU) arrows else 0) * distance)
            if (game.mode == PongMode.LOCAL) game.movePaddle(false, game.rightY + arrows * distance)
            game.update(dt)
            sync()
            frame.longValue++
        }
    }
    BackHandler {
        if (game.phase == PongPhase.PLAYING || game.phase == PongPhase.SERVING) pause() else onExit()
    }
    Box(Modifier.fillMaxSize()) {
        GameBackground(Neon.Teal)
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier.fillMaxSize().focusRequester(focus).onFocusChanged { if (!it.hasFocus) keys.clear() }
                    .onPreviewKeyEvent {
                        if (it.key in listOf(Key.W, Key.S, Key.DirectionUp, Key.DirectionDown)) {
                            if (it.type == KeyEventType.KeyDown) keys.add(it.key) else keys.remove(it.key)
                            true
                        } else false
                    }.focusable(),
            ) {
                GameTopBar(Neon.Teal, onExit, { pause(); showTutorial = true }, pause) {
                    StatBox(if (game.mode == PongMode.CPU) "Tu" else "G1", hud.left.toString(), Neon.Teal)
                    StatBox("A ${PongGame.WIN_SCORE}", "PONG", Neon.Yellow)
                    StatBox(if (game.mode == PongMode.CPU) "CPU" else "G2", hud.right.toString(), Neon.Pink)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (game.mode == PongMode.CPU) "1vCPU · ${game.difficulty.label.uppercase()}" else "1v1 · DUELLO LOCALE",
                        color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("SCAMBI ${hud.rally}", color = Neon.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                    PongBoard(game, frame)
                    if (hud.phase == PongPhase.SERVING && !showTutorial) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            val scorer = game.lastScorer
                            NeonText(when {
                                scorer == null -> "PRONTI?"
                                game.mode == PongMode.CPU -> if (scorer == 1) "PUNTO A TE!" else "PUNTO CPU"
                                else -> "PUNTO G$scorer"
                            }, if (scorer == 2) Neon.Pink else Neon.Teal, 24.sp)
                            NeonText(hud.countdown.toString(), Neon.Yellow, 72.sp, glow = 40f)
                        }
                    }
                }
                Text(
                    if (game.mode == PongMode.CPU) "Trascina su e giù per muovere la racchetta\nTastiera: W / S oppure ↑ / ↓"
                    else "G1: metà sinistra · G2: metà destra\nTrascinate su e giù · Tastiera: W / S e ↑ / ↓",
                    Modifier.fillMaxWidth().padding(12.dp), color = Neon.TextDim, fontSize = 12.sp,
                    textAlign = TextAlign.Center, lineHeight = 19.sp,
                )
            }

            OverlayPanel(hud.phase == PongPhase.READY && !showTutorial, Neon.Teal) {
                NeonText("PONG", Neon.Teal, 42.sp, glow = 32f, letterSpacing = 6.sp)
                val time by rememberTime()
                Canvas(Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 30.dp)) { drawPongPreview(time) }
                Text("IL DUELLO ORIGINALE", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PongChoice("1vCPU", mode == PongMode.CPU, Modifier.weight(1f)) { mode = PongMode.CPU }
                    PongChoice("1v1", mode == PongMode.LOCAL, Modifier.weight(1f)) { mode = PongMode.LOCAL }
                }
                if (mode == PongMode.CPU) {
                    Text("DIFFICOLTÀ CPU", color = Neon.TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PongDifficulty.entries.forEach { d ->
                            PongChoice(d.label, difficulty == d, Modifier.weight(1f)) { difficulty = d }
                        }
                    }
                }
                Text(
                    if (mode == PongMode.CPU) "Tu a sinistra, CPU a destra.\nTrascina su e giù: il primo a 11 vince!"
                    else "Due giocatori sullo stesso schermo.\nOgnuno trascina nella propria metà. Si vince a 11!",
                    color = Neon.Text, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
                )
                NeonButton("Inizia partita", Neon.Teal, Modifier.fillMaxWidth(), filled = true, onClick = start)
                NeonButton("Menu", Neon.TextDim, Modifier.fillMaxWidth(), onClick = onExit)
            }
            ResultPanel(
                visible = hud.phase == PongPhase.PAUSED && !showTutorial, accent = Neon.Teal, title = "PAUSA",
                lines = listOf((if (game.mode == PongMode.CPU) "Tu" else "G1") to hud.left.toString(),
                    (if (game.mode == PongMode.CPU) "CPU" else "G2") to hud.right.toString()), highlight = null,
                primary = "Riprendi" to { game.resume(); sync() },
                tertiary = "Cambia modalità" to setup, secondary = "Menu" to onExit,
            )
            ResultPanel(
                visible = hud.phase == PongPhase.OVER && !showTutorial,
                accent = if (game.leftWon) Neon.Teal else Neon.Pink,
                title = if (game.mode == PongMode.CPU) { if (game.leftWon) "HAI VINTO!" else "VINCE LA CPU" }
                    else "VINCE G${if (game.leftWon) 1 else 2}!",
                lines = listOf((if (game.mode == PongMode.CPU) "Tu" else "G1") to hud.left.toString(),
                    (if (game.mode == PongMode.CPU) "CPU" else "G2") to hud.right.toString()),
                highlight = "★ PARTITA A ${PongGame.WIN_SCORE} ★", primary = "Rivincita" to start,
                tertiary = "Cambia modalità" to setup, secondary = "Menu" to onExit,
            )
            TutorialOverlay(showTutorial, Neon.Teal, "PONG", pongTutorial()) {
                showTutorial = false
                prefs.markTutorialSeen("pong")
            }
        }
    }
}

@Composable
private fun PongChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Raised(if (selected) Surface3D.solid(Neon.Teal) else Surface3D.dark(Neon.Teal), modifier,
        corner = 12.dp, depth = 4.dp, onClick = onClick) {
        Text(label.uppercase(), Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            color = if (selected) Neon.Bg0 else Neon.Teal, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}
