package com.giochiclassici.arcade.games.pong

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput

@Composable
internal fun PongBoard(game: PongGame, frame: State<Long>, modifier: Modifier = Modifier) {
    Canvas(
        modifier.fillMaxSize().clipToBounds().pointerInput(game) {
            // Ogni dito mantiene la propria metà anche attraversando il centro.
            awaitEachGesture {
                val s = minOf(size.width / PongGame.WIDTH, size.height / PongGame.HEIGHT)
                val originY = (size.height - PongGame.HEIGHT * s) / 2
                val owners = mutableMapOf<PointerId, Pair<Boolean, Float>>()
                fun acquire(id: PointerId, position: Offset) {
                    val left = game.mode == PongMode.CPU || position.x < size.width / 2f
                    if (owners.values.none { it.first == left }) {
                        val paddleY = if (left) game.leftY else game.rightY
                        owners[id] = left to (paddleY - (position.y - originY) / s)
                    }
                }
                val down = awaitFirstDown(requireUnconsumed = false)
                acquire(down.id, down.position)
                down.consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.filter { !it.pressed }.forEach { owners.remove(it.id) }
                    event.changes.filter { it.pressed && !it.previousPressed }.forEach { acquire(it.id, it.position) }
                    for (change in event.changes) {
                        if (change.pressed) owners[change.id]?.let { (left, offset) ->
                            game.movePaddle(left, (change.position.y - originY) / s + offset)
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
        frame.value
        val s = minOf(size.width / PongGame.WIDTH, size.height / PongGame.HEIGHT)
        translate((size.width - PongGame.WIDTH * s) / 2, (size.height - PongGame.HEIGHT * s) / 2) {
            scale(s, s, pivot = Offset.Zero) { drawPongArena(game) }
        }
    }
}
