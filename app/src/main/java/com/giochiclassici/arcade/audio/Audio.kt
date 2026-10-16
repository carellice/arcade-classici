package com.giochiclassici.arcade.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import kotlin.concurrent.thread

object Audio {
    private var pool: SoundPool? = null
    private val ids = IntArray(Sfx.entries.size)
    private val music = MusicEngine()
    private var currentSong: Song? = null
    private var musicEnabled = true
    private var appInForeground = true

    @Volatile var sfxEnabled = true

    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        pool = p
        val dir = context.cacheDir
        thread(name = "sfx-render") {
            for (s in Sfx.entries) {
                val f = File(dir, "sfx_v1_${s.name}.wav")
                if (!f.exists()) f.writeBytes(SfxSynth.wav(SfxLib.render(s)))
                ids[s.ordinal] = p.load(f.path, 1)
            }
        }
    }

    fun play(s: Sfx, rate: Float = 1f, volume: Float = 1f) {
        if (!sfxEnabled) return
        val p = pool ?: return
        val id = ids[s.ordinal]
        if (id == 0) return
        p.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun music(song: Song?) {
        currentSong = song
        refreshMusic()
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        refreshMusic()
    }

    fun onAppForeground(foreground: Boolean) {
        appInForeground = foreground
        refreshMusic()
    }

    private fun refreshMusic() {
        if (musicEnabled && appInForeground && currentSong != null) music.play(currentSong) else music.stop()
    }
}
