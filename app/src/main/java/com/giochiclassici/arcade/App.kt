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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.giochiclassici.arcade.audio.Audio
import com.giochiclassici.arcade.audio.Songs
import com.giochiclassici.arcade.games.blocks.BlocksScreen
import com.giochiclassici.arcade.games.breakout.BreakoutScreen
import com.giochiclassici.arcade.games.invaders.InvadersScreen
import com.giochiclassici.arcade.games.mines.MinesScreen
import com.giochiclassici.arcade.games.snake.SnakeScreen
import com.giochiclassici.arcade.games.pong.PongScreen
import com.giochiclassici.arcade.menu.GameId
import com.giochiclassici.arcade.menu.MenuScreen
import com.giochiclassici.arcade.menu.SettingsScreen
import com.giochiclassici.arcade.update.Updater
import com.giochiclassici.arcade.ui.Neon

private const val MENU = "menu"
private const val SETTINGS = "settings"

@Composable
fun ArcadeApp() {
    // Schermata corrente: menu, impostazioni o il nome di un gioco
    var route by rememberSaveable { mutableStateOf(MENU) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Updater.checkOnStartup(scope) }
    LaunchedEffect(route) {
        Audio.music(
            when (route) {
                GameId.SNAKE.name -> Songs.snake
                GameId.MINES.name -> Songs.mines
                GameId.BREAKOUT.name -> Songs.breakout
                GameId.INVADERS.name -> Songs.invaders
                GameId.BLOCKS.name -> Songs.blocks
                GameId.PONG.name -> Songs.pong
                else -> Songs.menu
            }
        )
    }
    Box(Modifier.fillMaxSize().background(Neon.Bg0)) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.94f)) togetherWith fadeOut(tween(220))
            },
            label = "screen",
        ) { r ->
            val exit = { route = MENU }
            when (r) {
                MENU -> MenuScreen(onPlay = { route = it.name }, onSettings = { route = SETTINGS })
                SETTINGS -> SettingsScreen(onBack = exit)
                GameId.SNAKE.name -> SnakeScreen(onExit = exit)
                GameId.MINES.name -> MinesScreen(onExit = exit)
                GameId.BREAKOUT.name -> BreakoutScreen(onExit = exit)
                GameId.INVADERS.name -> InvadersScreen(onExit = exit)
                GameId.BLOCKS.name -> BlocksScreen(onExit = exit)
                GameId.PONG.name -> PongScreen(onExit = exit)
            }
        }
    }
}
