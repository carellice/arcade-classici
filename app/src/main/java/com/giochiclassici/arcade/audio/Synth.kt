package com.giochiclassici.arcade.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class Wave { SQUARE, TRIANGLE, SAW, SINE, NOISE }

internal object Notes {
    private val names = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** "A4", "C#5", "Bb3" -> numero MIDI */
    fun midi(n: String): Int {
        var semi = names.getValue(n[0])
        var i = 1
        if (n[i] == '#') { semi++; i++ } else if (n[i] == 'b') { semi--; i++ }
        val octave = n.substring(i).toInt()
        return 12 * (octave + 1) + semi
    }

    fun freq(midi: Int): Float = 440f * 2f.pow((midi - 69) / 12f)
    fun freq(name: String): Float = freq(midi(name))
}

/** Generatore di effetti sonori in stile chiptune. */
internal object SfxSynth {
    const val SR = 22050

    fun tone(
        dur: Float,
        wave: Wave,
        f0: Float,
        f1: Float = f0,
        duty: Float = 0.5f,
        vol: Float = 0.5f,
        attack: Float = 0.003f,
        curve: Float = 1.5f,
        vibrato: Float = 0f,
    ): FloatArray {
        val n = (dur * SR).toInt()
        val out = FloatArray(n)
        val rnd = Random(n)
        var phase = 0f
        var noise = 0f
        for (i in 0 until n) {
            val t = i / SR.toFloat()
            val p = t / dur
            var f = f0 + (f1 - f0) * p
            if (vibrato > 0f) f *= 1f + vibrato * sin(2f * PI.toFloat() * 9f * t)
            phase += f / SR
            if (phase >= 1f) {
                phase -= phase.toInt()
                if (wave == Wave.NOISE) noise = rnd.nextFloat() * 2f - 1f
            }
            val s = when (wave) {
                Wave.SQUARE -> if (phase < duty) 1f else -1f
                Wave.TRIANGLE -> 4f * abs(phase - 0.5f) - 1f
                Wave.SAW -> 2f * phase - 1f
                Wave.SINE -> sin(2f * PI.toFloat() * phase)
                Wave.NOISE -> noise
            }
            val env = (if (t < attack) t / attack else 1f) * (1f - p).pow(curve)
            out[i] = s * env * vol
        }
        return out
    }

    fun seq(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var o = 0
        for (p in parts) { p.copyInto(out, o); o += p.size }
        return out
    }

    fun mix(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.maxOf { it.size })
        for (p in parts) for (i in p.indices) out[i] += p[i]
        return out
    }

    fun delay(a: FloatArray, seconds: Float): FloatArray = seq(FloatArray((seconds * SR).toInt()), a)

    fun arp(notes: List<String>, dur: Float, wave: Wave, duty: Float, vol: Float, last: Float = dur): FloatArray =
        seq(*notes.mapIndexed { i, n ->
            val d = if (i == notes.lastIndex) last else dur
            tone(d, wave, Notes.freq(n), duty = duty, vol = vol, curve = if (i == notes.lastIndex) 1.4f else 0.6f)
        }.toTypedArray())

    fun wav(data: FloatArray, sr: Int = SR): ByteArray {
        val bb = ByteBuffer.allocate(44 + data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + data.size * 2); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(sr); bb.putInt(sr * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(data.size * 2)
        var peak = 0.0001f
        for (v in data) peak = max(peak, abs(v))
        val norm = if (peak > 0.95f) 0.95f / peak else 1f
        for (v in data) bb.putShort((v * norm * 32767f).toInt().toShort())
        return bb.array()
    }
}

enum class Sfx {
    Click, Start, Eat, Bonus, Die, Reveal, Flag, Unflag, Boom, Win,
    Paddle, Brick, BrickHard, Wall, PowerUp, LifeLost, LevelClear,
    Laser, Invader, March, Ufo, ShieldHit,
}

internal object SfxLib {
    fun render(s: Sfx): FloatArray = with(SfxSynth) {
        when (s) {
            Sfx.Click -> tone(0.045f, Wave.SQUARE, 1400f, 900f, duty = 0.25f, vol = 0.3f)
            Sfx.Start -> arp(listOf("C5", "E5", "G5", "C6"), 0.06f, Wave.SQUARE, 0.25f, 0.35f, last = 0.2f)
            Sfx.Eat -> mix(
                tone(0.1f, Wave.SQUARE, 520f, 1100f, duty = 0.5f, vol = 0.35f, curve = 1f),
                tone(0.1f, Wave.TRIANGLE, 260f, 550f, vol = 0.3f),
            )
            Sfx.Bonus -> mix(
                arp(listOf("E5", "G#5", "B5", "E6", "G#6", "B6"), 0.045f, Wave.SQUARE, 0.125f, 0.3f, last = 0.25f),
                delay(arp(listOf("E4", "B4", "E5"), 0.09f, Wave.TRIANGLE, 0.5f, 0.3f, last = 0.2f), 0.02f),
            )
            Sfx.Die -> mix(
                tone(0.75f, Wave.SQUARE, 480f, 50f, duty = 0.5f, vol = 0.35f, curve = 1.2f, vibrato = 0.05f),
                tone(0.5f, Wave.NOISE, 3000f, 400f, vol = 0.25f, curve = 2f),
            )
            Sfx.Reveal -> tone(0.06f, Wave.TRIANGLE, 900f, 1500f, vol = 0.45f)
            Sfx.Flag -> seq(
                tone(0.05f, Wave.SQUARE, 700f, duty = 0.25f, vol = 0.28f, curve = 0.5f),
                tone(0.08f, Wave.SQUARE, 1050f, duty = 0.25f, vol = 0.28f),
            )
            Sfx.Unflag -> seq(
                tone(0.05f, Wave.SQUARE, 1050f, duty = 0.25f, vol = 0.28f, curve = 0.5f),
                tone(0.08f, Wave.SQUARE, 700f, duty = 0.25f, vol = 0.28f),
            )
            Sfx.Boom -> mix(
                tone(1.0f, Wave.NOISE, 5000f, 200f, vol = 0.6f, curve = 2.2f),
                tone(0.6f, Wave.SINE, 120f, 30f, vol = 0.8f, curve = 1.5f),
            )
            Sfx.Win -> mix(
                arp(listOf("G4", "C5", "E5", "G5", "E5", "G5", "C6"), 0.085f, Wave.SQUARE, 0.25f, 0.3f, last = 0.55f),
                arp(listOf("C3", "C3", "G3", "G3", "C4"), 0.17f, Wave.TRIANGLE, 0.5f, 0.35f, last = 0.55f),
            )
            Sfx.Paddle -> tone(0.07f, Wave.SQUARE, 380f, 330f, duty = 0.5f, vol = 0.3f)
            Sfx.Brick -> mix(
                tone(0.08f, Wave.SQUARE, 980f, 760f, duty = 0.25f, vol = 0.3f),
                tone(0.05f, Wave.NOISE, 9000f, 6000f, vol = 0.07f),
            )
            Sfx.BrickHard -> mix(
                tone(0.12f, Wave.SQUARE, 1600f, 1500f, duty = 0.125f, vol = 0.15f),
                tone(0.12f, Wave.TRIANGLE, 400f, 380f, vol = 0.4f),
            )
            Sfx.Wall -> tone(0.04f, Wave.TRIANGLE, 260f, 240f, vol = 0.35f)
            Sfx.PowerUp -> tone(0.35f, Wave.SQUARE, 400f, 1600f, duty = 0.125f, vol = 0.28f, curve = 0.8f, vibrato = 0.05f)
            Sfx.LifeLost -> arp(listOf("G4", "E4", "C4", "G3"), 0.09f, Wave.SQUARE, 0.5f, 0.3f, last = 0.35f)
            Sfx.Laser -> mix(
                tone(0.13f, Wave.SQUARE, 1900f, 380f, duty = 0.25f, vol = 0.22f, curve = 1.2f),
                tone(0.06f, Wave.TRIANGLE, 900f, 300f, vol = 0.2f),
            )
            Sfx.Invader -> mix(
                tone(0.24f, Wave.NOISE, 7000f, 700f, vol = 0.32f, curve = 1.6f),
                tone(0.16f, Wave.SQUARE, 620f, 160f, duty = 0.5f, vol = 0.2f),
            )
            Sfx.March -> tone(0.1f, Wave.TRIANGLE, 98f, 92f, vol = 0.8f, curve = 2f)
            Sfx.Ufo -> tone(0.22f, Wave.SQUARE, 780f, 820f, duty = 0.5f, vol = 0.13f, curve = 0.2f, vibrato = 0.18f)
            Sfx.ShieldHit -> tone(0.06f, Wave.NOISE, 4500f, 2000f, vol = 0.22f)
            Sfx.LevelClear -> mix(
                arp(listOf("C5", "D5", "E5", "G5", "A5", "C6"), 0.06f, Wave.SQUARE, 0.25f, 0.3f, last = 0.5f),
                delay(tone(0.6f, Wave.TRIANGLE, Notes.freq("C4"), vol = 0.35f), 0.3f),
            )
        }
    }
}
