package com.giochiclassici.arcade

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Songs
import com.giochiclassici.arcade.games.breakout.BreakoutScreen
import com.giochiclassici.arcade.games.invaders.InvadersScreen
import com.giochiclassici.arcade.games.mines.MinesScreen
import com.giochiclassici.arcade.games.snake.SnakeScreen
import com.giochiclassici.arcade.menu.GameId
import com.giochiclassici.arcade.menu.MenuScreen
import com.giochiclassici.arcade.ui.Neon

@Composable
fun ArcadeApp() {
    var current by rememberSaveable { mutableStateOf<GameId?>(null) }
    LaunchedEffect(current) {
        Audio.music(
            when (current) {
                null -> Songs.menu
                GameId.SNAKE -> Songs.snake
                GameId.MINES -> Songs.mines
                GameId.BREAKOUT -> Songs.breakout
                GameId.INVADERS -> Songs.invaders
            }
        )
    }
    Box(Modifier.fillMaxSize().background(Neon.Bg0)) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.94f)) togetherWith fadeOut(tween(220))
            },
            label = "screen",
        ) { g ->
            val exit = { current = null }
            when (g) {
                null -> MenuScreen(onPlay = { current = it })
                GameId.SNAKE -> SnakeScreen(onExit = exit)
                GameId.MINES -> MinesScreen(onExit = exit)
                GameId.BREAKOUT -> BreakoutScreen(onExit = exit)
                GameId.INVADERS -> InvadersScreen(onExit = exit)
            }
        }
    }
}
