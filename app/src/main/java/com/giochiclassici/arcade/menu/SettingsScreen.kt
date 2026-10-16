package com.giochiclassici.arcade.menu

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
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
import com.giochiclassici.arcade.ui.IconBtn
import com.giochiclassici.arcade.ui.Neon
import com.giochiclassici.arcade.ui.NeonButton
import com.giochiclassici.arcade.ui.NeonIcons.back
import com.giochiclassici.arcade.ui.NeonIcons.music
import com.giochiclassici.arcade.ui.NeonIcons.sound
import com.giochiclassici.arcade.ui.NeonIcons.vibrate
import com.giochiclassici.arcade.ui.NeonText
import com.giochiclassici.arcade.ui.Raised
import com.giochiclassici.arcade.ui.Surface3D
import com.giochiclassici.arcade.ui.recessed
import com.giochiclassici.arcade.update.UpdateState
import com.giochiclassici.arcade.update.Updater
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val prefs = Services.prefs
    val accent = Neon.Cyan
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize()) {
        GameBackground(accent)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(accent, onClick = onBack) { back(it) }
                NeonText(
                    "IMPOSTAZIONI", accent, 22.sp,
                    Modifier.weight(1f), textAlign = TextAlign.Center, letterSpacing = 3.sp,
                )
                Spacer(Modifier.width(46.dp))
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Section("Audio e vibrazione", Neon.Pink) {
                    ToggleRow("Musica", prefs.music, Neon.Pink, { music(it, prefs.music) }) { prefs.toggleMusic() }
                    ToggleRow("Effetti sonori", prefs.sfx, Neon.Pink, { sound(it, prefs.sfx) }) { prefs.toggleSfx() }
                    ToggleRow("Vibrazione", prefs.haptics, Neon.Pink, { vibrate(it, prefs.haptics) }) { prefs.toggleHaptics() }
                }
                Section("Aggiornamenti", Neon.Green) { UpdatesSection() }
                Section("Informazioni", Neon.Purple) { InfoSection() }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Raised(Surface3D.panel(color), Modifier.fillMaxWidth(), corner = 22.dp, depth = 8.dp, glow = 6.dp) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title.uppercase(), color = color, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, color: Color, icon: DrawScope.(Color) -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).recessed(16.dp)
            .clickable(remember { MutableInteractionSource() }, null) {
                Audio.play(Sfx.Click); Haptics.tick(); onToggle()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp)) { icon(if (on) color else Neon.TextDim) }
        Spacer(Modifier.width(14.dp))
        Text(label, color = Neon.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch3D(on, color)
    }
}

/** Interruttore 3D: binario incavato con pomello in rilievo che scorre. */
@Composable
private fun Switch3D(on: Boolean, color: Color) {
    val t by animateFloatAsState(if (on) 1f else 0f, spring(stiffness = 600f, dampingRatio = 0.7f), label = "switch")
    Canvas(Modifier.size(58.dp, 32.dp)) {
        val h = size.height
        val r = CornerRadius(h / 2)
        drawRoundRect(Color(0xFF08061A), cornerRadius = r)
        drawRoundRect(lerp(Color(0xFF08061A), color, 0.55f * t), cornerRadius = r, alpha = 0.9f)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), 0f, h * 0.5f),
            cornerRadius = r,
        )
        drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = r, style = Stroke(1.dp.toPx()))
        val kr = h * 0.42f
        val cx = h / 2 + (size.width - h) * t
        val c = Offset(cx, h / 2)
        drawCircle(Color.Black.copy(alpha = 0.4f), kr, c + Offset(0f, 2.dp.toPx()))
        drawCircle(lerp(Color(0xFF6D66A8), color, t).let { lerp(it, Color.Black, 0.35f) }, kr, c + Offset(0f, 1.dp.toPx()))
        drawCircle(
            Brush.verticalGradient(listOf(Color.White, lerp(Color(0xFFB9B4E6), color, t)), c.y - kr, c.y + kr),
            kr * 0.92f, c,
        )
    }
}

@Composable
private fun UpdatesSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = Updater.state
    val foreground by AppState.foreground // ricompone al ritorno dalle impostazioni di sistema
    foreground
    LaunchedEffect(Unit) { if (state is UpdateState.Idle) Updater.check(scope) }

    Row(Modifier.fillMaxWidth().recessed(16.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Versione installata", color = Neon.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            NeonText(Updater.currentVersion, Neon.Text, 20.sp, glow = 10f, letterSpacing = 1.sp)
        }
        val badge = when (state) {
            is UpdateState.UpToDate -> "AGGIORNATA" to Neon.Green
            is UpdateState.Available, is UpdateState.Downloading, is UpdateState.ReadyToInstall -> "NUOVA: ${Updater.availableVersion}" to Neon.Yellow
            else -> null
        }
        if (badge != null) Text(badge.first, color = badge.second, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }

    when (state) {
        UpdateState.Idle, UpdateState.Checking -> {
            StatusText(if (state == UpdateState.Checking) "Controllo in corso su GitHub…" else "Controlla se è disponibile una nuova versione.")
            NeonButton(
                if (state == UpdateState.Checking) "Controllo…" else "Controlla aggiornamenti",
                Neon.Green, Modifier.fillMaxWidth(), filled = true,
            ) { Updater.check(scope) }
        }
        UpdateState.UpToDate -> {
            StatusText("Hai già l'ultima versione disponibile. 🎉")
            NeonButton("Controlla di nuovo", Neon.Green, Modifier.fillMaxWidth()) { Updater.check(scope) }
        }
        is UpdateState.Available -> {
            ReleaseNotes(state.info.version, state.info.notes)
            NeonButton(
                "Scarica e installa (${formatSize(state.info.size)})",
                Neon.Green, Modifier.fillMaxWidth(), filled = true,
            ) { Updater.download(context.applicationContext, scope) }
        }
        is UpdateState.Downloading -> {
            StatusText("Download della versione ${state.info.version}… ${(state.progress * 100).toInt()}%")
            ProgressBar3D(state.progress, Neon.Green)
        }
        is UpdateState.ReadyToInstall -> {
            StatusText(
                if (Updater.needsInstallPermission(context))
                    "Download completato. Consenti ad Arcade Classici di installare app, poi premi Installa."
                else "Download completato: conferma l'installazione nella finestra di Android."
            )
            NeonButton("Installa", Neon.Green, Modifier.fillMaxWidth(), filled = true) {
                Updater.install(context.applicationContext, state.file)
            }
        }
        is UpdateState.Error -> {
            StatusText(state.message, Neon.Red)
            NeonButton("Riprova", Neon.Green, Modifier.fillMaxWidth(), filled = true) { Updater.check(scope) }
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color = Neon.Text) =
    Text(text, color = color, fontSize = 14.sp, lineHeight = 20.sp)

@Composable
private fun ReleaseNotes(version: String, notes: String) {
    Column(Modifier.fillMaxWidth().recessed(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NeonText("Novità della $version", Neon.Yellow, 15.sp, glow = 10f, letterSpacing = 0.5.sp)
        Text(
            notes.ifBlank { "Miglioramenti e correzioni." }.lines().take(12).joinToString("\n"),
            color = Neon.Text, fontSize = 13.sp, lineHeight = 19.sp,
        )
    }
}

@Composable
private fun ProgressBar3D(progress: Float, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color(0xFF08061A), cornerRadius = r)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), 0f, size.height * 0.5f),
            cornerRadius = r,
        )
        val w = (size.width * progress).coerceAtLeast(size.height)
        drawRoundRect(lerp(color, Color.Black, 0.5f), Offset(0f, 2.dp.toPx()), Size(w, size.height - 2.dp.toPx()), r)
        drawRoundRect(
            Brush.verticalGradient(listOf(lerp(color, Color.White, 0.5f), color), 0f, size.height),
            Offset.Zero, Size(w, size.height - 3.dp.toPx()), r,
        )
    }
}

@Composable
private fun InfoSection() {
    val context = LocalContext.current
    Text(
        "Arcade Classici è open source (licenza MIT). Codice, segnalazioni e nuove versioni sono su GitHub.",
        color = Neon.Text, fontSize = 14.sp, lineHeight = 20.sp,
    )
    NeonButton("Apri su GitHub", Neon.Purple, Modifier.fillMaxWidth()) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.repoUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes <= 0) "APK" else String.format(Locale.ITALY, "%.1f MB", bytes / 1_048_576f)
