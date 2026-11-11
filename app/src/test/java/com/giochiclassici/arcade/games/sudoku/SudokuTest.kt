package com.giochiclassici.arcade.games.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SudokuTest {
    @Test
    fun everyDifficultyHasOneSolutionMatchingTheAnswer() {
        for (diff in SudokuDiff.entries) for (seed in 1..3) {
            val (puzzle, solution) = Sudoku.generate(diff.clues, Random(seed))
            assertTrue("indizi ${diff.label}", puzzle.count { it != 0 } >= diff.clues)
            assertEquals(1, Sudoku.countSolutions(puzzle.copyOf(), 2))
            for (i in 0 until 81) {
                if (puzzle[i] != 0) assertEquals(solution[i], puzzle[i])
                assertTrue(Sudoku.peers(i).none { solution[it] == solution[i] })
            }
        }
    }

    @Test
    fun fillingTheSolutionWins() {
        val game = SudokuGame(SudokuDiff.EASY, Random(7))
        for (i in 0 until 81) if (!game.given[i]) {
            game.selected = i
            game.input(game.solution[i])
        }
        assertEquals(SudokuPhase.WON, game.phase)
        assertEquals(0, game.mistakes)
    }

    @Test
    fun longPressCrossesNumbersOnlyForTheEmptySelectedCell() {
        val game = SudokuGame(SudokuDiff.EASY, Random(7))
        val empty = (0 until 81).first { !game.given[it] }
        val other = (0 until 81).last { !game.given[it] }
        game.selected = empty
        game.toggleExcluded(4)
        assertTrue(game.isExcluded(4))
        game.selected = other
        assertTrue(!game.isExcluded(4))
        game.selected = empty
        game.toggleExcluded(4)
        assertTrue(!game.isExcluded(4))
        game.selected = (0 until 81).first { game.given[it] }
        game.toggleExcluded(2)
        assertTrue(!game.isExcluded(2))
    }
}
