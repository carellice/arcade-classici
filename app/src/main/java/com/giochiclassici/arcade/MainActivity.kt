package com.giochiclassici.arcade

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.core.AppState
import com.giochiclassici.arcade.core.Services

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Services.init(applicationContext)
        hideSystemBars()
        setContent { ArcadeApp() }
    }

    override fun onResume() {
        super.onResume()
        AppState.foreground.value = true
        Audio.onAppForeground(true)
        hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        AppState.foreground.value = false
        Audio.onAppForeground(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }
}
