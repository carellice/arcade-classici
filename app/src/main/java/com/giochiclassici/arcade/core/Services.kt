package com.giochiclassici.arcade.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.giochiclassici.arcade.audio.Audio

/** Stato globale dell'app (primo piano / background). */
object AppState {
    val foreground = mutableStateOf(true)
}

object Services {
    lateinit var prefs: Prefs
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = Prefs(context)
        Haptics.init(context)
        Haptics.enabled = prefs.haptics
        Audio.init(context)
        Audio.sfxEnabled = prefs.sfx
        Audio.setMusicEnabled(prefs.music)
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("arcade", Context.MODE_PRIVATE)

    var music by mutableStateOf(sp.getBoolean("music", true))
        private set
    var sfx by mutableStateOf(sp.getBoolean("sfx", true))
        private set
    var haptics by mutableStateOf(sp.getBoolean("haptics", true))
        private set

    fun toggleMusic() {
        music = !music
        sp.edit { putBoolean("music", music) }
        Audio.setMusicEnabled(music)
    }

    fun toggleSfx() {
        sfx = !sfx
        sp.edit { putBoolean("sfx", sfx) }
        Audio.sfxEnabled = sfx
    }

    fun toggleHaptics() {
        haptics = !haptics
        sp.edit { putBoolean("haptics", haptics) }
        Haptics.enabled = haptics
    }

    fun best(key: String, default: Int = 0): Int = sp.getInt(key, default)

    /** Salva un record se migliore. Ritorna true se è un nuovo record. */
    fun submitHigh(key: String, value: Int): Boolean {
        if (value <= best(key)) return false
        sp.edit { putInt(key, value) }
        return true
    }

    /** Per i tempi: più basso è meglio. 0 = nessun record. */
    fun submitLow(key: String, value: Int): Boolean {
        val cur = best(key)
        if (cur != 0 && value >= cur) return false
        sp.edit { putInt(key, value) }
        return true
    }

    fun tutorialSeen(game: String) = sp.getBoolean("tut_$game", false)
    fun markTutorialSeen(game: String) = sp.edit { putBoolean("tut_$game", true) }
}

object Haptics {
    private var vibrator: Vibrator? = null
    var enabled = true

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    private fun vibrate(ms: Long, amplitude: Int) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val amp = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        v.vibrate(VibrationEffect.createOneShot(ms, amp))
    }

    fun tick() = vibrate(8, 60)
    fun bump() = vibrate(18, 130)
    fun heavy() = vibrate(70, 255)
}
