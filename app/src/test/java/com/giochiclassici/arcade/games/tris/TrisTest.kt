package com.giochiclassici.arcade.games.tris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrisTest {
    @Test
    fun rowWinsAndScores() {
        val g = TrisGame(TrisMode.LOCAL, TrisDifficulty.NORMAL)
        for (i in intArrayOf(0, 3, 1, 4, 2)) assertTrue(g.play(i))
        assertTrue(g.over)
        assertEquals(1, g.winner)
        assertEquals(1, g.xWins)
        assertFalse(g.play(5))
        g.nextRound()
        assertEquals(2, g.turn) // chi comincia si alterna
    }

    @Test
    fun cpuBlocksAndWins() {
        // X minaccia 0-1-2: la CPU deve chiudere in 2.
        assertEquals(2, Tris.bestMove(intArrayOf(1, 1, 0, 0, 2, 0, 0, 0, 0), 2))
        // O può chiudere 3-4-5: vincere batte bloccare.
        assertEquals(5, Tris.bestMove(intArrayOf(1, 1, 0, 2, 2, 0, 1, 0, 0), 2))
    }

    @Test
    fun hardCpuNeverLoses() {
        val r = Random(7)
        repeat(200) {
            val g = TrisGame(TrisMode.CPU, TrisDifficulty.HARD, r)
            if (it % 2 == 1) g.nextRound()
            while (!g.over) g.play(if (g.cpuTurn) g.cpuMove() else (0 until 9).filter { i -> g.board[i] == 0 }.random(r))
            assertTrue(g.winner != 1)
        }
    }
}
