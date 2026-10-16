package com.giochiclassici.arcade.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val REST = -1
const val HOLD = -2

class Song(
    val bpm: Int,
    val lead: IntArray,
    val arp: IntArray,
    val bass: IntArray,
    val drums: String,
    val leadWave: Wave = Wave.SQUARE,
    val leadDuty: Float = 0.25f,
    val leadVolume: Float = 0.15f,
)

private const val TWO_PI = (2 * PI).toFloat()

private class Voice(val wave: Wave, val duty: Float, val vibrato: Float = 0f) {
    private var freq = 0f
    private var phase = 0f
    private var gate = false
    private var t = 0f
    private var level = 0f

    fun on(midi: Int) { freq = Notes.freq(midi); gate = true; t = 0f }
    fun off() { gate = false }
    fun reset() { gate = false; level = 0f }

    fun sample(dt: Float, decay: Float, sustain: Float): Float {
        t += dt
        level = if (gate) {
            if (t < 0.006f) max(level, t / 0.006f) else max(sustain, level - decay * dt)
        } else level * 0.9992f
        if (level < 0.0005f) return 0f
        var f = freq
        if (vibrato > 0f && t > 0.2f) f *= 1f + vibrato * sin(t * 34f)
        phase += f * dt
        if (phase >= 1f) phase -= phase.toInt()
        val s = when (wave) {
            Wave.SQUARE -> if (phase < duty) 1f else -1f
            Wave.TRIANGLE -> 4f * abs(phase - 0.5f) - 1f
            Wave.SINE -> sin(TWO_PI * phase)
            else -> 2f * phase - 1f
        }
        return s * level
    }
}

/** Sequencer chiptune: lead + arpeggio + basso + batteria. */
private class Sequencer(private val sr: Int) {
    var song: Song? = null
        private set
    private val dt = 1f / sr
    private var lead = Voice(Wave.SQUARE, 0.25f, 0.006f)
    private val arp = Voice(Wave.SQUARE, 0.125f)
    private val bass = Voice(Wave.TRIANGLE, 0.5f)
    private var step = 0
    private var samplesLeft = 0f
    private var samplesPerStep = 1f
    private var fade = 0f

    private var kickT = 9f
    private var kickPhase = 0f
    private var snareT = 9f
    private var hatT = 9f
    private var seed = 22222
    private var lastNoise = 0f

    fun load(s: Song?) {
        song = s
        step = 0
        samplesLeft = 0f
        fade = 0f
        if (s != null) {
            samplesPerStep = sr * 60f / s.bpm / 2f
            lead = Voice(s.leadWave, s.leadDuty, 0.006f)
        }
        arp.reset(); bass.reset()
    }

    private fun noise(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 16) and 0x7FFF) / 16384f - 1f
    }

    private fun trigger(s: Song) {
        fun apply(v: Voice, line: IntArray) {
            val n = line[step % line.size]
            if (n >= 0) v.on(n) else if (n == REST) v.off()
        }
        apply(lead, s.lead)
        apply(arp, s.arp)
        apply(bass, s.bass)
        when (s.drums[step % s.drums.length]) {
            'k' -> kick()
            's' -> snareT = 0f
            'h' -> hatT = 0f
            'x' -> { kick(); hatT = 0f }
            'o' -> { snareT = 0f; hatT = 0f }
        }
        step++
    }

    private fun kick() { kickT = 0f; kickPhase = 0f }

    private fun drums(): Float {
        var v = 0f
        if (kickT < 0.35f) {
            kickT += dt
            val f = 45f + 110f * exp(-kickT * 28f)
            kickPhase += f * dt
            v += sin(TWO_PI * kickPhase) * exp(-kickT * 10f)
        }
        if (snareT < 0.3f) {
            snareT += dt
            v += noise() * exp(-snareT * 20f) * 0.5f + sin(TWO_PI * 185f * snareT) * exp(-snareT * 30f) * 0.3f
        }
        if (hatT < 0.1f) {
            hatT += dt
            val n = noise()
            v += (n - lastNoise) * 0.5f * exp(-hatT * 70f) * 0.35f
            lastNoise = n
        }
        return v
    }

    fun render(out: FloatArray, volume: Float) {
        val s = song
        if (s == null) { out.fill(0f); return }
        for (i in out.indices) {
            if (samplesLeft <= 0f) { trigger(s); samplesLeft += samplesPerStep }
            samplesLeft -= 1f
            val v = lead.sample(dt, 1.1f, 0.55f) * s.leadVolume +
                arp.sample(dt, 6f, 0f) * 0.055f +
                bass.sample(dt, 1.4f, 0.55f) * 0.28f +
                drums() * 0.3f
            fade = min(1f, fade + dt * 1.2f)
            out[i] = (v * volume * fade).coerceIn(-1f, 1f)
        }
    }
}

/** Riproduce le musiche in streaming su un thread dedicato. */
internal class MusicEngine {
    @Volatile private var song: Song? = null
    @Volatile private var generation = 0
    @Volatile var volume = 0.6f
    private var running = false

    fun play(s: Song?) {
        if (s == null) { stop(); return }
        song = s
        if (!running) start()
    }

    fun stop() {
        running = false
        generation++
    }

    private fun start() {
        running = true
        val gen = ++generation
        Thread({ loop(gen) }, "arcade-music").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun loop(gen: Int) {
        val sr = 32000
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sr)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, 4096 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            return
        }
        val seq = Sequencer(sr)
        val buf = FloatArray(1024)
        try {
            track.play()
            while (generation == gen) {
                val s = song
                if (s !== seq.song) seq.load(s)
                seq.render(buf, volume)
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
            }
            track.pause()
            track.flush()
        } catch (_: Exception) {
        } finally {
            track.release()
        }
    }
}
