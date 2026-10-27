package com.giochiclassici.arcade.games.pong

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class PongGameTest {
    private val dt = 1f / 120f

    private fun launch(game: PongGame) {
        repeat(400) { if (game.phase == PongPhase.SERVING) game.update(dt) }
        assertEquals(PongPhase.PLAYING, game.phase)
    }

    @Test fun countdownKeepsBallStillAndThenServes() {
        val game = PongGame(Random(1))
        game.start(PongMode.LOCAL)
        repeat(120) { game.update(dt) }
        assertEquals(PongPhase.SERVING, game.phase)
        assertEquals(PongGame.WIDTH / 2, game.ballX, 0f)
        assertEquals(0f, game.velocityX, 0f)
        launch(game)
        assertTrue(abs(game.velocityX) > 500f)
    }

    @Test fun pauseFreezesBothCountdownAndRally() {
        val game = PongGame(Random(2))
        game.start()
        val time = game.serveTime
        game.pause()
        repeat(500) { game.update(dt) }
        assertEquals(time, game.serveTime, 0f)
        game.resume()
        launch(game)
        game.update(dt)
        val position = PongPoint(game.ballX, game.ballY)
        game.pause()
        game.movePaddle(true, 100f)
        repeat(500) { game.update(dt) }
        assertEquals(position, PongPoint(game.ballX, game.ballY))
        game.resume()
        game.update(dt)
        assertNotEquals(position, PongPoint(game.ballX, game.ballY))
    }

    @Test fun localPaddlesMoveIndependentlyAndStayInsideWalls() {
        val game = PongGame(Random(3))
        game.start(PongMode.LOCAL)
        game.movePaddle(true, -5000f)
        game.movePaddle(false, 5000f)
        assertEquals(PongGame.WALL + PongGame.PADDLE_HEIGHT / 2, game.leftY, 0f)
        assertEquals(PongGame.HEIGHT - PongGame.WALL - PongGame.PADDLE_HEIGHT / 2, game.rightY, 0f)
        game.movePaddle(true, 500f)
        assertEquals(500f, game.leftY, 0f)
        assertTrue(game.rightY > 1000f)
    }

    @Test fun cpuIgnoresPlayerInputAndHasBoundedSpeedAtEveryDifficulty() {
        PongDifficulty.entries.forEach { difficulty ->
            val game = PongGame(Random(4))
            game.start(PongMode.CPU, difficulty)
            game.movePaddle(false, 200f)
            assertEquals(PongGame.HEIGHT / 2, game.rightY, 0f)
            launch(game)
            repeat(4000) {
                val before = game.rightY
                game.movePaddle(true, game.ballY)
                game.update(dt)
                assertTrue(abs(game.rightY - before) <= difficulty.speed * dt + 0.001f)
                assertTrue(game.rightY >= PongGame.WALL + PongGame.PADDLE_HEIGHT / 2)
                assertTrue(game.rightY <= PongGame.HEIGHT - PongGame.WALL - PongGame.PADDLE_HEIGHT / 2)
            }
        }
    }

    @Test fun longRallyBouncesAtWallsAndNeverTunnelsThroughPaddles() {
        var hits = 0
        var walls = 0
        val game = PongGame(Random(5)) {
            if (it == PongEvent.PADDLE) hits++
            if (it == PongEvent.WALL) walls++
        }
        game.start(PongMode.LOCAL)
        launch(game)
        repeat(4000) {
            game.movePaddle(true, game.ballY)
            game.movePaddle(false, game.ballY)
            // Include frame lenti per verificare la suddivisione della fisica.
            game.update(if (it % 10 == 0) 0.05f else dt)
            assertEquals(PongPhase.PLAYING, game.phase)
            assertTrue(game.ballY >= PongGame.WALL + PongGame.BALL_RADIUS - 0.001f)
            assertTrue(game.ballY <= PongGame.HEIGHT - PongGame.WALL - PongGame.BALL_RADIUS + 0.001f)
        }
        assertTrue(hits > 50)
        assertTrue(walls > 0)
        assertEquals(0, game.leftScore + game.rightScore)
    }

    @Test fun paddleEdgeChangesReboundAngle() {
        val game = PongGame(Random(6))
        game.start(PongMode.LOCAL)
        launch(game)
        repeat(400) {
            if (game.rally == 0) {
                game.movePaddle(true, game.ballY + 85f)
                game.movePaddle(false, game.ballY + 85f)
                game.update(dt)
            }
        }
        assertEquals(1, game.rally)
        assertTrue(game.velocityY < -300f)
    }

    @Test fun missedBallAwardsOnePointAndStartsNewCountdown() {
        var points = 0
        val game = PongGame(Random(7)) { if (it == PongEvent.POINT) points++ }
        game.start(PongMode.LOCAL)
        game.movePaddle(true, 0f)
        game.movePaddle(false, 0f)
        launch(game)
        repeat(500) { if (game.leftScore + game.rightScore == 0) game.update(dt) }
        assertEquals(1, game.leftScore + game.rightScore)
        assertEquals(1, points)
        assertEquals(PongPhase.SERVING, game.phase)
        assertEquals(PongGame.WIDTH / 2, game.ballX, 0f)
        repeat(100) { game.update(dt) }
        assertEquals(1, game.leftScore + game.rightScore)
    }

    @Test fun firstToElevenEndsMatchAndRematchResetsState() {
        var wins = 0
        val game = PongGame(Random(8)) { if (it == PongEvent.WIN) wins++ }
        game.start(PongMode.LOCAL, PongDifficulty.HARD)
        game.movePaddle(true, 0f)
        game.movePaddle(false, 0f)
        repeat(100000) { if (game.phase != PongPhase.OVER) game.update(0.05f) }
        assertEquals(PongPhase.OVER, game.phase)
        assertEquals(11, maxOf(game.leftScore, game.rightScore))
        assertEquals(1, wins)
        val score = game.leftScore + game.rightScore
        repeat(100) { game.update(dt) }
        assertEquals(score, game.leftScore + game.rightScore)
        game.start()
        assertEquals(PongMode.LOCAL, game.mode)
        assertEquals(PongDifficulty.HARD, game.difficulty)
        assertEquals(0, game.leftScore + game.rightScore)
        assertEquals(PongPhase.SERVING, game.phase)
        assertEquals(PongGame.HEIGHT / 2, game.leftY, 0f)
        assertEquals(PongGame.HEIGHT / 2, game.rightY, 0f)
    }
}
