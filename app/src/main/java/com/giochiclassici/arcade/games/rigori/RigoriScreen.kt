package com.giochiclassici.arcade.games.rigori

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Sfx
import com.giochiclassici.arcade.core.Haptics
import com.giochiclassici.arcade.core.Services
import com.giochiclassici.arcade.ui.GameBackground
import com.giochiclassici.arcade.ui.GameTopBar
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.NeonButton
import com.giochiclassici.arcade.ui.NeonText
import com.giochiclassici.arcade.ui.OverlayPanel
import com.giochiclassici.arcade.ui.Raised
import com.giochiclassici.arcade.ui.StatBox
import com.giochiclassici.arcade.ui.Surface3D
import com.giochiclassici.arcade.ui.TutorialOverlay
import com.giochiclassici.arcade.ui.rememberTime
import kotlinx.coroutines.delay

private enum class Phase { AIM, RUNUP, FLIGHT, RESULT }

private const val RUNUP_S = 1.3f
private const val FLIGHT_S = 0.55f
private const val RESULT_S = 1.2f

fun rigoriBestKey(mode: RigoriMode) = "best_rigori_${mode.name.lowercase()}"

@Composable
fun RigoriScreen(onExit: () -> Unit) {
    val prefs = Services.prefs
    val accent = Neon.Red
    var showTutorial by remember { mutableStateOf(!prefs.tutorialSeen("rigori")) }
    var mode by remember { mutableStateOf(RigoriMode.MATCH) }
    var difficulty by remember { mutableStateOf(RigoriDifficulty.NORMAL) }
    var game by remember { mutableStateOf<RigoriGame?>(null) }
    var phase by remember { mutableStateOf(Phase.AIM) }
    var phaseStart by remember { mutableFloatStateOf(0f) }
    var kick by remember { mutableStateOf<Kick?>(null) }
    var dive by remember { mutableStateOf<Offset?>(null) }
    var newRecord by remember { mutableStateOf(false) }
    // Cresce a ogni tiro: fa ridisegnare l'HUD.
    var version by remember { mutableIntStateOf(0) }
    val time by rememberTime()

    fun begin(g: RigoriGame) {
        kick = null; dive = null
        phase = if (g.playerShoots) Phase.AIM else Phase.RUNUP
        if (phase == Phase.RUNUP) g.prepareCpuShot()
        phaseStart = time
    }

    fun kicked(k: Kick) {
        kick = k; version++
        phase = Phase.FLIGHT; phaseStart = time
        Audio.play(Sfx.Paddle); Haptics.bump()
    }

    fun start() {
        val g = RigoriGame(mode, difficulty)
        game = g; newRecord = false; version++
        Audio.play(Sfx.Start)
        begin(g)
    }

    // Sequenza del tiro: rincorsa CPU, volo del pallone, esito, poi il tiro successivo.
    LaunchedEffect(game, phase) {
        val g = game ?: return@LaunchedEffect
        when (phase) {
            Phase.AIM -> Unit
            Phase.RUNUP -> {
                delay((RUNUP_S * 1000).toLong())
                val d = dive ?: Offset(0f, 0.35f)
                kicked(g.save(d.x, d.y))
            }
            Phase.FLIGHT -> {
                delay((FLIGHT_S * 1000).toLong())
                val k = kick ?: return@LaunchedEffect
                val good = (k.outcome == Outcome.GOAL) == k.byPlayer
                when {
                    k.outcome == Outcome.OUT -> Audio.play(Sfx.Wall)
                    good -> { Audio.play(if (k.byPlayer) Sfx.Win else Sfx.Bonus); Haptics.heavy() }
                    else -> { Audio.play(if (k.byPlayer) Sfx.ShieldHit else Sfx.LifeLost); Haptics.heavy() }
                }
                phase = Phase.RESULT; phaseStart = time
            }
            Phase.RESULT -> {
                delay((RESULT_S * 1000).toLong())
                if (g.over) {
                    if (g.mode == RigoriMode.MATCH) Audio.play(if (g.playerWon) Sfx.LevelClear else Sfx.Die)
                    else newRecord = prefs.submitHigh(rigoriBestKey(g.mode), g.score)
                    version++
                } else begin(g)
            }
        }
    }
    BackHandler { if (game != null) game = null else onExit() }

    Box(Modifier.fillMaxSize()) {
        GameBackground(accent)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val g = game
            version
            GameTopBar(accent = accent, onBack = onExit, onHelp = { showTutorial = true }, onPause = null) {
                when (g?.mode ?: mode) {
                    RigoriMode.MATCH -> {
                        StatBox("Tu", (g?.myGoals ?: 0).toString(), Neon.Cyan)
                        StatBox("Tiro", ((g?.cpuKicks?.size ?: 0) + 1).coerceAtMost(99).toString(), Neon.TextDim)
                        StatBox("CPU", (g?.cpuGoals ?: 0).toString(), Neon.Pink)
                    }
                    RigoriMode.SHOOT, RigoriMode.SAVE -> {
                        val m = g?.mode ?: mode
                        StatBox(if (m == RigoriMode.SAVE) "Parate" else "Gol", (g?.score ?: 0).toString(), Neon.Cyan)
                        StatBox("Tiri", "${if (m == RigoriMode.SAVE) g?.cpuKicks?.size ?: 0 else g?.myKicks?.size ?: 0}/${Rigori.CHALLENGE_KICKS}", Neon.TextDim)
                        StatBox("Record", prefs.best(rigoriBestKey(m)).toString(), Neon.Yellow)
                    }
                }
            }
            if (g != null) KickDots(g)
            Text(
                when {
                    g == null || g.over -> ""
                    phase == Phase.AIM -> "TOCCA LA PORTA PER TIRARE"
                    phase == Phase.RUNUP -> if (dive == null) "TOCCA DOVE TUFFARTI!" else "PRONTO…"
                    else -> ""
                },
                Modifier.fillMaxWidth().padding(6.dp),
                color = if (phase == Phase.RUNUP) Neon.Pink else Neon.Cyan,
                fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, textAlign = TextAlign.Center,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (g != null) {
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(g) {
                            detectTapGestures { o ->
                                val f = Field(size.width.toFloat(), size.height.toFloat())
                                val (x, y) = f.toGoal(o)
                                if (g.over) return@detectTapGestures
                                when (phase) {
                                    Phase.AIM -> if (x in -1.4f..1.4f && y in -0.2f..1.5f) kicked(g.shoot(x, y.coerceAtLeast(0f)))
                                    Phase.RUNUP -> if (dive == null && x in -1.3f..1.3f && y in -0.3f..1.3f) {
                                        dive = Offset(x.coerceIn(-1f, 1f), y.coerceIn(0.1f, 0.9f)); Haptics.tick()
                                    }
                                    else -> Unit
                                }
                            }
                        },
                    ) {
                        val f = Field(size.width, size.height)
                        val p = time - phaseStart
                        drawPitch(f)
                        drawGoal(f, if (phase == Phase.RESULT && kick?.outcome == Outcome.GOAL) kick else null, p)
                        drawSceneParts(f, phase == Phase.RUNUP, phase == Phase.FLIGHT, phase == Phase.RESULT, p, kick, dive, g.pending, time, RUNUP_S, FLIGHT_S)
                    }
                    if (phase == Phase.RESULT) kick?.let { k ->
                        val good = (k.outcome == Outcome.GOAL) == k.byPlayer
                        NeonText(
                            when (k.outcome) { Outcome.GOAL -> "GOL!"; Outcome.SAVED -> "PARATA!"; Outcome.OUT -> "FUORI!" },
                            if (good) Neon.Green else Neon.Red, 52.sp, glow = 36f, letterSpacing = 4.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }

        val g = game
        OverlayPanel(g != null && g.over && phase == Phase.RESULT && kick != null && !showTutorial && overShown(g, time - phaseStart), accent) {
            g ?: return@OverlayPanel
            if (g.mode == RigoriMode.MATCH) {
                NeonText(if (g.playerWon) "HAI VINTO!" else "HAI PERSO", if (g.playerWon) Neon.Green else Neon.Red, 38.sp, glow = 30f, letterSpacing = 4.sp)
                Text("${g.myGoals} – ${g.cpuGoals}", color = Neon.Text, fontSize = 30.sp, fontWeight = FontWeight.Black)
            } else {
                NeonText(if (g.mode == RigoriMode.SAVE) "PARATE: ${g.score}" else "GOL: ${g.score}", accent, 38.sp, glow = 30f, letterSpacing = 4.sp)
                Text(if (newRecord) "NUOVO RECORD!" else "Record: ${prefs.best(rigoriBestKey(g.mode))}",
                    color = if (newRecord) Neon.Yellow else Neon.TextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            NeonButton("Ancora", accent, Modifier.fillMaxWidth(), filled = true) { start() }
            NeonButton("Cambia modalità", Neon.TextDim, Modifier.fillMaxWidth()) { game = null }
        }

        OverlayPanel(game == null && !showTutorial, accent) {
            NeonText("RIGORI", accent, 42.sp, glow = 32f, letterSpacing = 6.sp)
            Text("UNO CONTRO UNO DAL DISCHETTO", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RigoriMode.entries.forEach { m -> RigoriChoice(m.label, mode == m, Modifier.weight(1f)) { mode = m } }
            }
            Text("DIFFICOLTÀ CPU", color = Neon.TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RigoriDifficulty.entries.forEach { d -> RigoriChoice(d.label, difficulty == d, Modifier.weight(1f)) { difficulty = d } }
            }
            Text(
                when (mode) {
                    RigoriMode.MATCH -> "Cinque tiri a testa, alternati:\ntiri tu, poi pari tu. Se è pari, a oltranza!"
                    RigoriMode.SHOOT -> "Dieci tiri contro il portiere CPU.\nQuanti gol riesci a fare?"
                    RigoriMode.SAVE -> "Dieci tiri della CPU.\nQuanti riesci a pararne?"
                },
                color = Neon.Text, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
            NeonButton("Inizia", accent, Modifier.fillMaxWidth(), filled = true) { start() }
            NeonButton("Menu", Neon.TextDim, Modifier.fillMaxWidth(), onClick = onExit)
        }
        TutorialOverlay(showTutorial, accent, "RIGORI", rigoriTutorial()) {
            showTutorial = false
            prefs.markTutorialSeen("rigori")
        }
    }
}

/** Il riepilogo finale compare dopo aver visto l'ultimo esito. */
private fun overShown(g: RigoriGame, sincePhase: Float) = g.over && sincePhase >= RESULT_S * 0.95f

@Composable
private fun RigoriChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Raised(if (selected) Surface3D.solid(Neon.Red) else Surface3D.dark(Neon.Red), modifier,
        corner = 12.dp, depth = 4.dp, onClick = onClick) {
        Text(label.uppercase(), Modifier.padding(horizontal = 2.dp, vertical = 12.dp),
            color = if (selected) Neon.Bg0 else Neon.Red, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    }
}

