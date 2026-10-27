package com.giochiclassici.arcade.games.pong

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Esercita il vero gestore touch, inclusi due puntatori attivi contemporaneamente. */
class PongControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun board(mode: PongMode): PongGame {
        val game = PongGame()
        game.start(mode)
        val frame = mutableLongStateOf(0L)
        compose.setContent { PongBoard(game, frame) }
        return game
    }

    @Test fun twoFingersControlBothPaddlesAndKeepOwnershipAcrossTheCenter() {
        val game = board(PongMode.LOCAL)
        compose.onRoot().performTouchInput {
            down(0, Offset(width * 0.25f, height * 0.5f))
            down(1, Offset(width * 0.75f, height * 0.5f))
            moveTo(0, Offset(width * 0.8f, height * 0.3f))
            moveTo(1, Offset(width * 0.75f, height * 0.7f))
            up(0)
            up(1)
        }
        compose.runOnIdle {
            assertTrue("G1 deve salire anche se il dito attraversa il centro", game.leftY < PongGame.HEIGHT / 2)
            assertTrue("G2 deve scendere indipendentemente da G1", game.rightY > PongGame.HEIGHT / 2)
        }
    }

    @Test fun cpuModeAcceptsDragFromEitherHalfWithoutMovingCpuPaddle() {
        val game = board(PongMode.CPU)
        compose.onRoot().performTouchInput {
            down(0, Offset(width * 0.8f, height * 0.5f))
            moveTo(0, Offset(width * 0.8f, height * 0.7f))
            up(0)
        }
        compose.runOnIdle {
            assertTrue(game.leftY > PongGame.HEIGHT / 2)
            assertEquals(PongGame.HEIGHT / 2, game.rightY, 0f)
        }
    }

    @Test fun pausedGameIgnoresDragging() {
        val game = board(PongMode.LOCAL)
        compose.runOnIdle { game.pause() }
        compose.onRoot().performTouchInput {
            down(0, Offset(width * 0.25f, height * 0.5f))
            moveTo(0, Offset(width * 0.25f, height * 0.8f))
            up(0)
        }
        compose.runOnIdle {
            assertEquals(PongGame.HEIGHT / 2, game.leftY, 0f)
            assertEquals(PongGame.HEIGHT / 2, game.rightY, 0f)
        }
    }
}
