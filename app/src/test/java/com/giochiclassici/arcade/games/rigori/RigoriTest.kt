package com.giochiclassici.arcade.games.rigori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RigoriTest {
    @Test
    fun outcomeRules() {
        assertEquals(Outcome.OUT, Rigori.outcome(1.1f, 0.5f, 0f, 0.35f))
        assertEquals(Outcome.OUT, Rigori.outcome(0f, 1.05f, 0f, 0.35f))
        assertEquals(Outcome.SAVED, Rigori.outcome(0.1f, 0.4f, 0f, 0.35f))
        assertEquals(Outcome.GOAL, Rigori.outcome(0.9f, 0.8f, -0.5f, 0.35f))
    }

    @Test
    fun matchAlternatesAndEnds() {
        val g = RigoriGame(RigoriMode.MATCH, RigoriDifficulty.NORMAL, Random(1))
        var kicks = 0
        while (!g.over) {
            if (g.playerShoots) g.shoot(0.5f, 0.5f) else g.save(0f, 0.35f)
            kicks++
            assertTrue("partita infinita", kicks < 200)
        }
        assertTrue(g.myGoals != g.cpuGoals)
        assertEquals(g.playerWon, g.myGoals > g.cpuGoals)
    }

    @Test
    fun matchEndsEarlyWhenOutOfReach() {
        val g = RigoriGame(RigoriMode.MATCH, RigoriDifficulty.NORMAL)
        // 3 gol contro 0 dopo tre coppie: la CPU con due tiri non può rimontare.
        g.myKicks += listOf(Outcome.GOAL, Outcome.GOAL, Outcome.GOAL)
        g.cpuKicks += listOf(Outcome.SAVED, Outcome.SAVED, Outcome.SAVED)
        assertTrue(g.over)
        assertTrue(g.playerWon)
    }

    @Test
    fun suddenDeathNeedsPairs() {
        val g = RigoriGame(RigoriMode.MATCH, RigoriDifficulty.NORMAL)
        repeat(5) { g.myKicks += Outcome.GOAL; g.cpuKicks += Outcome.GOAL }
        assertFalse(g.over)
        g.myKicks += Outcome.SAVED
        assertFalse(g.over) // la CPU deve ancora tirare
        g.cpuKicks += Outcome.GOAL
        assertTrue(g.over)
        assertFalse(g.playerWon)
    }

    @Test
    fun challengeLastsTenKicks() {
        val g = RigoriGame(RigoriMode.SAVE, RigoriDifficulty.EASY, Random(3))
        repeat(Rigori.CHALLENGE_KICKS) { assertFalse(g.over); g.prepareCpuShot(); g.save(0f, 0.4f) }
        assertTrue(g.over)
        assertEquals(g.cpuKicks.count { it != Outcome.GOAL }, g.score)
    }
}
