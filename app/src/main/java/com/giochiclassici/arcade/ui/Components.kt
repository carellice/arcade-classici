package com.giochiclassici.arcade.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Sfx
import com.giochiclassici.arcade.core.Haptics
import com.giochiclassici.arcade.ui.NeonIcons.back
import com.giochiclassici.arcade.ui.NeonIcons.help
import com.giochiclassici.arcade.ui.NeonIcons.pause
import kotlin.math.min

/** Tempo in secondi aggiornato ad ogni frame, da leggere nei Canvas. */
@Composable
fun rememberTime(): State<Float> {
    val s = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { s.floatValue = (it - start) / 1e9f }
    }
    return s
}

/** Loop di gioco: chiama onFrame(dt) ad ogni frame. */
@Composable
fun GameLoop(onFrame: (Float) -> Unit) {
    val cb = remember { arrayOf(onFrame) }
    cb[0] = onFrame
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                cb[0](dt)
            }
        }
    }
}

fun Modifier.pressable(sound: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = 700f), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(src, null) {
            if (sound) Audio.play(Sfx.Click)
            Haptics.tick()
            onClick()
        }
}

// ---------------------------------------------------------------------------
// Sistema 3D: superfici in rilievo (tasti, schede, pannelli) e superfici incavate
// ---------------------------------------------------------------------------

/** Colori di una superficie in rilievo. */
class Surface3D(
    val faceTop: Color,
    val faceBottom: Color,
    val side: Color,
    val rim: Color,
    val glow: Color? = null,
) {
    companion object {
        /** Tasto colorato pieno (azione principale). */
        fun solid(c: Color) = Surface3D(
            faceTop = lerp(c, Color.White, 0.38f),
            faceBottom = lerp(c, Color.Black, 0.08f),
            side = lerp(c, Color.Black, 0.55f),
            rim = Color.White.copy(alpha = 0.45f),
            glow = c,
        )

        /** Tasto scuro con accento colorato (azione secondaria). */
        fun dark(accent: Color) = Surface3D(
            faceTop = lerp(Color(0xFF2E2575), accent, 0.10f),
            faceBottom = Color(0xFF19133F),
            side = lerp(Color(0xFF07051A), accent, 0.25f),
            rim = accent.copy(alpha = 0.55f),
        )

        /** Pannello / scheda grande. */
        fun panel(accent: Color) = Surface3D(
            faceTop = Color(0xFF2A2068),
            faceBottom = Color(0xFF151038),
            side = lerp(Color(0xFF05040F), accent, 0.45f),
            rim = accent.copy(alpha = 0.85f),
            glow = accent,
        )
    }
}

/** Disegna una superficie in rilievo: ombra, fianco, faccia con gradiente, riflesso e bordo. */
fun DrawScope.drawRaised(
    s: Surface3D,
    depth: Float,
    press: Float,
    corner: Float,
    glowSize: Float = 0f,
) {
    val w = size.width
    val fh = size.height - depth
    val r = min(corner, min(w, fh) / 2f)
    val off = depth * press
    // Ombra morbida a terra
    for (i in 1..3) {
        val g = i * 2.5f * density
        drawRoundRect(
            Color.Black.copy(alpha = 0.16f * (1f - press * 0.5f)),
            Offset(-g * 0.5f, depth + g * 0.6f), Size(w + g, fh),
            CornerRadius(r + g),
        )
    }
    // Alone neon
    if (s.glow != null && glowSize > 0f) {
        for (i in 3 downTo 1) {
            val g = glowSize * i / 3f
            drawRoundRect(
                s.glow.copy(alpha = 0.07f),
                Offset(-g, off - g), Size(w + 2 * g, fh + depth - off + 2 * g),
                CornerRadius(r + g),
            )
        }
    }
    // Fianco (spessore)
    drawRoundRect(s.side, Offset(0f, depth), Size(w, fh), CornerRadius(r))
    // Faccia
    drawRoundRect(
        Brush.verticalGradient(listOf(s.faceTop, s.faceBottom), off, off + fh),
        Offset(0f, off), Size(w, fh), CornerRadius(r),
    )
    // Riflesso lucido nella metà superiore
    val inset = 2f * density
    val glossH = min(fh * 0.5f, 30f * density)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0f)), off + inset, off + inset + glossH),
        Offset(inset, off + inset), Size(w - 2 * inset, glossH), CornerRadius(min(r - inset, glossH / 2f)),
    )
    // Bordo luminoso
    drawRoundRect(s.rim, Offset(0f, off), Size(w, fh), CornerRadius(r), style = Stroke(1.4f * density))
}

/**
 * Contenitore in rilievo. Se [onClick] non è nullo diventa un tasto che si abbassa alla pressione.
 * Lo spessore [depth] viene aggiunto sotto al contenuto.
 */
@Composable
fun Raised(
    surface: Surface3D,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    depth: Dp = 6.dp,
    glow: Dp = 0.dp,
    sound: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 1200f), label = "press3d")
    val clickMod = if (onClick != null) {
        Modifier.clickable(src, null) {
            if (sound) Audio.play(Sfx.Click)
            Haptics.tick()
            onClick()
        }
    } else Modifier
    Box(
        modifier
            .then(clickMod)
            .drawBehind { drawRaised(surface, depth.toPx(), press, corner.toPx(), glow.toPx()) }
            .padding(bottom = depth),
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier.graphicsLayer { translationY = press * depth.toPx() },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** Superficie incavata (binari, contenitori dell'HUD). */
fun Modifier.recessed(corner: Dp = 18.dp): Modifier = drawBehind {
    val r = CornerRadius(min(corner.toPx(), size.minDimension / 2))
    drawRoundRect(Color(0xFF08061A), cornerRadius = r)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), 0f, 10.dp.toPx()),
        cornerRadius = r,
    )
    drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = r, style = Stroke(1.dp.toPx()))
    drawRoundRect(
        Color.Black.copy(alpha = 0.5f), Offset(0f, 0f), Size(size.width, size.height - 1.dp.toPx()),
        cornerRadius = r, style = Stroke(1.5.dp.toPx()),
    )
}

// ---------------------------------------------------------------------------

@Composable
fun NeonText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Black,
    letterSpacing: TextUnit = 2.sp,
    glow: Float = 22f,
    textAlign: TextAlign? = null,
    fontFamily: FontFamily? = null,
) {
    Text(
        text,
        modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = weight,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        fontFamily = fontFamily,
        style = TextStyle(shadow = Shadow(color.copy(alpha = 0.85f), Offset.Zero, glow)),
    )
}

/** Testo in rilievo (con ombra scura sotto), per titoli e tasti. */
@Composable
fun EmbossText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = 2.sp,
    shadow: Color = Color.Black.copy(alpha = 0.45f),
) {
    Text(
        text,
        modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        letterSpacing = letterSpacing,
        style = TextStyle(shadow = Shadow(shadow, Offset(0f, 2.5f), 2f)),
    )
}

/** Tasto 3D principale. [filled] = colore pieno, altrimenti scuro con accento. */
@Composable
fun NeonButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Raised(
        if (filled) Surface3D.solid(color) else Surface3D.dark(color),
        modifier,
        corner = 16.dp,
        depth = 6.dp,
        glow = if (filled) 10.dp else 0.dp,
        onClick = onClick,
    ) {
        EmbossText(
            text.uppercase(),
            if (filled) Neon.Bg0 else color,
            16.sp,
            Modifier.padding(horizontal = 22.dp, vertical = 15.dp),
            shadow = if (filled) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.6f),
        )
    }
}

/** Tasto rotondo 3D con icona. */
@Composable
fun IconBtn(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    onClick: () -> Unit,
    icon: DrawScope.(Color) -> Unit,
) {
    val depth = 4.dp
    Raised(
        Surface3D(
            faceTop = lerp(Color(0xFF332A7A), color, 0.28f),
            faceBottom = lerp(Color(0xFF1A1442), color, 0.08f),
            side = lerp(Color(0xFF05040F), color, 0.35f),
            rim = color.copy(alpha = 0.7f),
        ),
        modifier.size(size, size + depth),
        corner = size,
        depth = depth,
        onClick = onClick,
    ) {
        Canvas(Modifier.size(size * 0.44f)) { icon(color) }
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), color = Neon.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        NeonText(value, color, 21.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.sp, glow = 14f)
    }
}

/** Barra superiore comune a tutti i giochi. */
@Composable
fun GameTopBar(
    accent: Color,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onPause: (() -> Unit)?,
    center: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(accent, onClick = onBack) { back(it) }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).height(52.dp).recessed(16.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            center()
        }
        Spacer(Modifier.width(8.dp))
        IconBtn(accent, onClick = onHelp) { help(it) }
        if (onPause != null) {
            Spacer(Modifier.width(6.dp))
            IconBtn(accent, onClick = onPause) { pause(it) }
        }
    }
}

/** Pannello sovrapposto (pausa, game over, tutorial). */
@Composable
fun OverlayPanel(visible: Boolean, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color(0xD905040F)) }
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            Raised(
                Surface3D.panel(accent),
                Modifier
                    .animateEnterExit(
                        enter = scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.85f),
                        exit = scaleOut(targetScale = 0.9f),
                    )
                    .padding(22.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                corner = 26.dp,
                depth = 10.dp,
                glow = 14.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

/** Pannello standard di fine partita / pausa. */
@Composable
fun ResultPanel(
    visible: Boolean,
    accent: Color,
    title: String,
    lines: List<Pair<String, String>>,
    highlight: String?,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    tertiary: Pair<String, () -> Unit>? = null,
) {
    OverlayPanel(visible, accent) {
        NeonText(title, accent, 32.sp, textAlign = TextAlign.Center)
        if (highlight != null) {
            NeonText(highlight, Neon.Yellow, 16.sp, glow = 16f, textAlign = TextAlign.Center)
        }
        if (lines.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().recessed(16.dp).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for ((l, v) in lines) StatBox(l, v, Neon.Text)
            }
        }
        Spacer(Modifier.height(2.dp))
        NeonButton(primary.first, accent, Modifier.fillMaxWidth(), filled = true, onClick = primary.second)
        tertiary?.let { NeonButton(it.first, accent, Modifier.fillMaxWidth(), onClick = it.second) }
        NeonButton(secondary.first, Neon.TextDim, Modifier.fillMaxWidth(), onClick = secondary.second)
    }
}

fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
