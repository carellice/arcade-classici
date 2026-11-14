package com.giochiclassici.arcade.games.rigori

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.TutorialPage
import com.giochiclassici.arcade.ui.rememberTime
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/** Geometria del campo visto da dietro il dischetto: porta in alto, pallone in basso. */
/** compact: inquadratura stretta per anteprime e tutorial. */
internal class Field(val w: Float, val h: Float, compact: Boolean = false) {
    val gw = if (compact) w * 0.9f else min(w * 0.8f, h * 1.6f)
    val gh = gw * 0.36f
    val left = (w - gw) / 2f
    val top = h * (if (compact) 0.12f else 0.16f)
    val line = top + gh
    val spot = Offset(w / 2f, h * (if (compact) 0.86f else 0.8f))
    val ball = gw * 0.05f

    fun toScreen(x: Float, y: Float) = Offset(left + (x + 1f) / 2f * gw, line - y * gh)
    fun toGoal(o: Offset) = ((o.x - left) / gw * 2f - 1f) to ((line - o.y) / gh)
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)

internal fun DrawScope.drawPitch(f: Field) {
    val green = Neon.Green
    // Strisce d'erba: più larghe man mano che si avvicinano.
    var y = f.line
    var band = (f.h - f.line) * 0.08f
    var k = 0
    while (y < f.h) {
        if (k % 2 == 0) drawRect(green.copy(alpha = 0.035f), Offset(0f, y), Size(f.w, band))
        y += band; band *= 1.25f; k++
    }
    val a = green.copy(alpha = 0.45f)
    drawLine(a, Offset(0f, f.line), Offset(f.w, f.line), 3f)
    // Area piccola in prospettiva.
    val near = f.line + (f.h - f.line) * 0.28f
    val l0 = f.w / 2 - f.gw * 0.56f; val r0 = f.w / 2 + f.gw * 0.56f
    val l1 = f.w / 2 - f.gw * 0.62f; val r1 = f.w / 2 + f.gw * 0.62f
    drawLine(a, Offset(l0, f.line), Offset(l1, near), 3f)
    drawLine(a, Offset(r0, f.line), Offset(r1, near), 3f)
    drawLine(a, Offset(l1, near), Offset(r1, near), 3f)
    drawCircle(Color.White.copy(alpha = 0.7f), f.ball * 0.35f, f.spot + Offset(0f, f.ball * 0.6f))
}

/** Porta al neon con la rete; bulge è il gol appena segnato, che fa ondeggiare la rete. */
internal fun DrawScope.drawGoal(f: Field, bulge: Kick?, sinceGoal: Float) {
    val depthY = f.gh * 0.14f
    val inset = f.gw * 0.05f
    val net = Neon.Text.copy(alpha = 0.13f)
    val backL = f.left + inset; val backR = f.left + f.gw - inset; val backT = f.top - depthY
    for (i in 0..16) {
        val x = backL + (backR - backL) * i / 16f
        drawLine(net, Offset(x, backT), Offset(f.left + f.gw * i / 16f, f.line), 1.5f)
    }
    for (j in 0..7) {
        val t = j / 7f
        val y = backT + (f.line - backT) * t
        val l = backL + (f.left - backL) * t; val r = backR + (f.left + f.gw - backR) * t
        drawLine(net, Offset(l, y), Offset(r, y), 1.5f)
    }
    drawLine(net, Offset(f.left, f.top), Offset(backL, backT), 2f)
    drawLine(net, Offset(f.left + f.gw, f.top), Offset(backR, backT), 2f)
    drawLine(net, Offset(backL, backT), Offset(backR, backT), 2f)
    if (bulge != null) {
        val c = f.toScreen(bulge.tx, bulge.ty)
        val e = (sinceGoal / 0.8f).coerceIn(0f, 1f)
        drawCircle(Color.White.copy(alpha = 0.5f * (1f - e)), f.gh * (0.1f + 0.5f * e), c, style = Stroke(3f))
        drawCircle(Neon.Green.copy(alpha = 0.35f * (1f - e)), f.gh * (0.05f + 0.3f * e), c, style = Stroke(2f))
    }
    val post = f.gw * 0.018f
    for ((wd, al) in listOf(post * 3f to 0.18f, post to 1f)) {
        val c = Color.White.copy(alpha = al)
        drawLine(c, Offset(f.left, f.line), Offset(f.left, f.top), wd, StrokeCap.Round)
        drawLine(c, Offset(f.left, f.top), Offset(f.left + f.gw, f.top), wd, StrokeCap.Round)
        drawLine(c, Offset(f.left + f.gw, f.top), Offset(f.left + f.gw, f.line), wd, StrokeCap.Round)
    }
}

/** Omino al neon visto di spalle o di fronte; con i guanti è il portiere. */
private fun DrawScope.drawPlayer(c: Offset, s: Float, angle: Float, color: Color, gloves: Boolean, reach: Float) {
    rotate(angle, c) {
        val stroke = s * 0.07f
        // Braccia: in alto e aperte quando si tuffa (reach 1), lungo i fianchi da fermo.
        val shoulder = Offset(0f, -s * 0.16f)
        val hand = Offset(s * (0.2f + 0.14f * reach), -s * (0.05f + 0.3f * reach))
        for (side in listOf(-1f, 1f)) {
            val h = c + Offset(hand.x * side, hand.y)
            for ((wd, al) in listOf(stroke * 2.4f to 0.2f, stroke to 1f))
                drawLine(color.copy(alpha = al), c + Offset(shoulder.x + side * s * 0.1f, shoulder.y), h, wd, StrokeCap.Round)
            if (gloves) drawCircle(Neon.Yellow, s * 0.06f, h)
            val foot = c + Offset(side * s * 0.12f, s * 0.36f)
            for ((wd, al) in listOf(stroke * 2.4f to 0.2f, stroke to 1f))
                drawLine(color.copy(alpha = al), c + Offset(side * s * 0.06f, s * 0.16f), foot, wd, StrokeCap.Round)
        }
        drawRoundRect(color.copy(alpha = 0.25f), c + Offset(-s * 0.15f, -s * 0.24f), Size(s * 0.3f, s * 0.46f), CornerRadius(s * 0.1f))
        drawRoundRect(color, c + Offset(-s * 0.11f, -s * 0.2f), Size(s * 0.22f, s * 0.38f), CornerRadius(s * 0.08f))
        drawCircle(color.copy(alpha = 0.25f), s * 0.12f, c + Offset(0f, -s * 0.32f))
        drawCircle(color, s * 0.085f, c + Offset(0f, -s * 0.32f))
    }
}

/** Portiere in goal: da (0, 0.35) si tuffa verso (kx, ky) con avanzamento q. */
internal fun DrawScope.drawKeeper(f: Field, kx: Float, ky: Float, q: Float, color: Color) {
    val x = kx * q; val y = 0.35f + (ky - 0.35f) * q
    val angle = (kx * 75f).coerceIn(-80f, 80f) * q
    drawPlayer(f.toScreen(x, y), f.gh * 0.8f, angle, color, gloves = true, reach = q.coerceAtLeast(0.15f))
}

internal fun DrawScope.drawBall(c: Offset, r: Float, spin: Float) {
    drawCircle(Color.White.copy(alpha = 0.15f), r * 1.35f, c)
    drawCircle(Color.White, r, c)
    for (i in 0 until 5) {
        val a = spin + i * (2 * PI / 5).toFloat()
        drawCircle(Neon.Bg1.copy(alpha = 0.8f), r * 0.22f, c + Offset(kotlin.math.cos(a) * r * 0.55f, sin(a) * r * 0.55f))
    }
    drawCircle(Neon.Bg1.copy(alpha = 0.8f), r * 0.25f, c)
}

/**
 * Pallone, portiere e tiratore secondo la fase. p: secondi dall'inizio della fase.
 * Il tiratore CPU compare durante la rincorsa e si sbilancia verso pending.lean.
 */
internal fun DrawScope.drawSceneParts(
    f: Field, runup: Boolean, flying: Boolean, result: Boolean, p: Float,
    kick: Kick?, dive: Offset?, pending: CpuShot?, time: Float,
    runupS: Float, flightS: Float,
) {
    val cpuShooting = runup || (kick != null && !kick.byPlayer)
    val keeperColor = if (cpuShooting) Neon.Cyan else Neon.Pink
    val q = when {
        flying -> easeOut((p / flightS).coerceIn(0f, 1f))
        result -> 1f
        else -> 0f
    }
    if (kick != null && (flying || result)) drawKeeper(f, kick.kx, kick.ky, q, keeperColor)
    else drawKeeper(f, sin(time * 2.5f) * 0.06f, 0.35f, 1f, keeperColor)
    if (runup && dive != null) {
        val c = f.toScreen(dive.x, dive.y)
        drawCircle(Neon.Cyan.copy(alpha = 0.6f), f.gw * Rigori.REACH / 2f * 0.5f, c, style = Stroke(3f))
        drawCircle(Neon.Cyan, f.ball * 0.25f, c)
    }

    // Tiratore della CPU: rincorsa da sinistra fino al pallone.
    if (cpuShooting) {
        val r = if (runup) (p / runupS).coerceIn(0f, 1f) else 1f
        val from = Offset(f.w / 2 - f.gw * 0.4f, f.h * 1.02f)
        val to = Offset(f.spot.x - f.ball * 2.2f, f.spot.y + f.ball * 0.2f)
        val lean = (pending?.lean ?: 0) * 18f * r
        val bob = if (runup) sin(r * 18f) * f.ball * 0.2f else 0f
        drawPlayer(lerp(from, to, r) + Offset(0f, bob), f.gh * 1.25f, lean, Neon.Pink, gloves = false, reach = 0f)
    }

    // Pallone.
    var c = f.spot
    var r = f.ball
    if (kick != null && (flying || result)) {
        val target = f.toScreen(kick.tx, kick.ty)
        val t = if (flying) (p / flightS).coerceIn(0f, 1f) else 1f
        c = lerp(f.spot, target, t) + Offset(0f, -sin(PI.toFloat() * t) * f.gh * 0.35f)
        r = f.ball * (1f - 0.5f * t)
        // Ombra sul prato.
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(c.x - r, f.spot.y + (f.line - f.spot.y) * t - r * 0.3f), Size(r * 2f, r * 0.6f))
        if (result) {
            val e = easeOut((p / 0.6f).coerceIn(0f, 1f))
            when (kick.outcome) {
                Outcome.SAVED -> {
                    // Respinta verso l'esterno e a terra.
                    val dir = if (kick.tx == 0f) 1f else sign(kick.tx)
                    c = target + Offset(dir * f.gw * 0.2f * e, f.gh * 0.5f * e)
                }
                Outcome.GOAL -> { c = target + Offset(0f, -f.gh * 0.08f * e); r *= 1f - 0.15f * e }
                Outcome.OUT -> { c = target + (target - f.spot) * (0.15f * e); r *= 1f - 0.3f * e }
            }
        }
    }
    drawBall(c, r, if (flying) p * 20f else 0f)
}

/** Pallini dei tiri: verde riuscito, rosso no. In partita: i tuoi a sinistra, quelli della CPU a destra. */
@Composable
internal fun KickDots(g: RigoriGame) {
    Canvas(Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 24.dp)) {
        val r = size.height * 0.32f
        val gap = r * 3f
        fun row(list: List<Outcome>, slots: Int, startX: Float, good: (Outcome) -> Boolean) {
            for (i in 0 until slots) {
                val c = Offset(startX + i * gap, size.height / 2)
                val o = list.getOrNull(i)
                if (o == null) drawCircle(Neon.TextDim.copy(alpha = 0.5f), r, c, style = Stroke(2f))
                else drawCircle(if (good(o)) Neon.Green else Neon.Red, r, c)
            }
        }
        when (g.mode) {
            RigoriMode.MATCH -> {
                // Oltre il quinto rigore si vedono solo gli ultimi cinque.
                val shown = maxOf(Rigori.SERIES, g.myKicks.size, g.cpuKicks.size)
                val skip = shown - Rigori.SERIES
                val my = g.myKicks.drop(skip); val cpu = g.cpuKicks.drop(skip)
                row(my, Rigori.SERIES, r, { it == Outcome.GOAL })
                row(cpu, Rigori.SERIES, size.width - r - gap * (Rigori.SERIES - 1), { it == Outcome.GOAL })
            }
            RigoriMode.SHOOT -> row(g.myKicks, Rigori.CHALLENGE_KICKS, size.width / 2 - gap * 4.5f, { it == Outcome.GOAL })
            RigoriMode.SAVE -> row(g.cpuKicks, Rigori.CHALLENGE_KICKS, size.width / 2 - gap * 4.5f, { it != Outcome.GOAL })
        }
    }
}

/** Anteprima del menu: tiri a rotazione negli angoli, il portiere a volte para. */
fun DrawScope.drawRigoriPreview(t: Float) = drawDemo(t, keeperIsPlayer = false)

private fun DrawScope.drawDemo(t: Float, keeperIsPlayer: Boolean) {
    val f = Field(size.width, size.height, compact = true)
    val cycle = 2.2f
    val n = (t / cycle).toInt()
    val local = t - n * cycle
    val targets = listOf(0.75f to 0.7f, -0.7f to 0.25f, 0.1f to 0.5f, -0.8f to 0.8f)
    val (tx, ty) = targets[n % targets.size]
    val kx = if (n % 3 == 2) tx else -tx * 0.8f
    val k = Kick(tx, ty, kx, 0.4f, Rigori.outcome(tx, ty, kx, 0.4f), byPlayer = !keeperIsPlayer)
    drawPitch(f)
    val flying = local in 0.6f..1.15f
    val result = local > 1.15f
    drawGoal(f, if (result && k.outcome == Outcome.GOAL) k else null, local - 1.15f)
    drawSceneParts(
        f, runup = keeperIsPlayer && local < 0.6f, flying = flying, result = result,
        p = if (flying) local - 0.6f else if (result) local - 1.15f else local,
        kick = if (local >= 0.6f) k else null, dive = null,
        pending = CpuShot(tx, ty, sign(tx).toInt()), time = t, runupS = 0.6f, flightS = 0.55f,
    )
}

fun rigoriTutorial(): List<TutorialPage> = listOf(
    TutorialPage("Tira", "Quando tocca a te tirare, tocca il punto della porta dove vuoi mandare il pallone. Gli angoli sono imprendibili, ma il tiro non è mai perfetto: mira troppo vicino al palo e rischi di finire fuori!") {
        TutorialArt(keeperIsPlayer = false)
    },
    TutorialPage("Para", "Quando tira la CPU guarda la rincorsa: spesso si sbilancia dal lato dove tirerà. Tocca la porta per scegliere dove tuffarti, prima che calci!") {
        TutorialArt(keeperIsPlayer = true)
    },
    TutorialPage("Tre modalità", "Partita: cinque rigori a testa e poi a oltranza. Tiratore: dieci tiri, quanti gol fai? Portiere: dieci tiri della CPU, quanti ne pari?") {
        TutorialArt(keeperIsPlayer = false)
    },
)

@Composable
private fun TutorialArt(keeperIsPlayer: Boolean) {
    val time by rememberTime()
    Canvas(Modifier.fillMaxSize().padding(12.dp)) { drawDemo(time, keeperIsPlayer) }
}
