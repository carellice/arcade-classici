package com.giochiclassici.arcade.games.sudoku

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Quante caselle restano scoperte: meno indizi, più ragionamento. */
enum class SudokuDiff(val label: String, val clues: Int) {
    EASY("Facile", 40),
    MEDIUM("Medio", 32),
    HARD("Difficile", 26);

    val bestKey get() = "best_sudoku_${name.lowercase()}"
}

enum class SudokuPhase { PLAYING, WON }

/**
 * Una partita: lo schema generato ha sempre una sola soluzione. I numeri sbagliati restano scritti ma
 * si colorano quando vanno contro una riga, una colonna o un riquadro.
 */
class SudokuGame(val diff: SudokuDiff, random: Random = Random.Default) {
    val solution: IntArray
    val given: BooleanArray
    val grid: IntArray

    init {
        val (puzzle, solved) = Sudoku.generate(diff.clues, random)
        solution = solved
        grid = puzzle.copyOf()
        given = BooleanArray(81) { puzzle[it] != 0 }
    }

    var selected by mutableIntStateOf(-1)
    var phase by mutableStateOf(SudokuPhase.PLAYING)
        private set
    var mistakes by mutableIntStateOf(0)
        private set

    /** Cresce a ogni cambiamento: il Canvas lo legge per ridisegnarsi. */
    var frame by mutableLongStateOf(0L)
        private set
    var clock by mutableFloatStateOf(0f)
        private set
    private var elapsed = 0f
    val seconds get() = elapsed.toInt()
    var endTime = 0f
        private set

    fun update(dt: Float, frozen: Boolean) {
        clock += dt
        if (phase == SudokuPhase.PLAYING && !frozen) elapsed += dt
        frame++
    }

    fun select(i: Int) {
        if (phase != SudokuPhase.PLAYING) return
        selected = if (selected == i) -1 else i
        frame++
    }

    /** Scrive [n] (0 cancella) nella casella scelta. */
    fun input(n: Int) {
        val i = selected
        if (phase != SudokuPhase.PLAYING || i < 0 || given[i]) return
        grid[i] = if (grid[i] == n) 0 else n
        if (grid[i] != 0 && grid[i] != solution[i]) mistakes++
        if (grid.contentEquals(solution)) {
            phase = SudokuPhase.WON
            endTime = clock
            selected = -1
        }
        frame++
    }

    /** Per ogni casella, i numeri che il giocatore ha escluso (bit n acceso = n sbarrato). */
    private val excluded = IntArray(81)

    fun isExcluded(n: Int): Boolean {
        val i = selected
        return i >= 0 && grid[i] == 0 && excluded[i] and (1 shl n) != 0
    }

    /** Sbarra o ripristina [n] sul tastierino, solo per la casella vuota scelta. */
    fun toggleExcluded(n: Int) {
        val i = selected
        if (phase != SudokuPhase.PLAYING || i < 0 || grid[i] != 0) return
        excluded[i] = excluded[i] xor (1 shl n)
        frame++
    }

    /** Il numero va contro un altro uguale nella stessa riga, colonna o riquadro. */
    fun conflict(i: Int): Boolean {
        val n = grid[i]
        if (n == 0) return false
        return Sudoku.peers(i).any { grid[it] == n }
    }

    /** Quante volte [n] è già nello schema: a 9 il tasto si spegne. */
    fun count(n: Int) = grid.count { it == n }
}

object Sudoku {
    private val peerCache = Array(81) { i ->
        val r = i / 9; val c = i % 9; val br = r / 3 * 3; val bc = c / 3 * 3
        buildSet {
            for (k in 0 until 9) { add(r * 9 + k); add(k * 9 + c) }
            for (dr in 0 until 3) for (dc in 0 until 3) add((br + dr) * 9 + bc + dc)
            remove(i)
        }.toIntArray()
    }

    fun peers(i: Int): IntArray = peerCache[i]

    fun canPlace(g: IntArray, i: Int, n: Int) = peerCache[i].none { g[it] == n }

    /** Uno schema con [clues] indizi e la sua unica soluzione. */
    fun generate(clues: Int, random: Random): Pair<IntArray, IntArray> {
        val full = IntArray(81)
        fill(full, random)
        val puzzle = full.copyOf()
        var left = 81
        // Toglie caselle in ordine casuale finché la soluzione resta unica.
        for (i in (0 until 81).shuffled(random)) {
            if (left <= clues) break
            val keep = puzzle[i]
            puzzle[i] = 0
            if (countSolutions(puzzle.copyOf(), 2) == 1) left-- else puzzle[i] = keep
        }
        return puzzle to full
    }

    private fun fill(g: IntArray, random: Random): Boolean {
        val i = g.indexOfFirst { it == 0 }
        if (i < 0) return true
        for (n in (1..9).shuffled(random)) {
            if (canPlace(g, i, n)) {
                g[i] = n
                if (fill(g, random)) return true
                g[i] = 0
            }
        }
        return false
    }

    /** Conta le soluzioni fino a [limit]: basta sapere se ce n'è più di una. */
    fun countSolutions(g: IntArray, limit: Int): Int {
        // La casella con meno candidati per prima: la ricerca resta rapida anche con pochi indizi.
        var best = -1
        var bestCount = 10
        for (i in 0 until 81) {
            if (g[i] != 0) continue
            val c = (1..9).count { canPlace(g, i, it) }
            if (c < bestCount) { best = i; bestCount = c }
            if (c == 0) return 0
        }
        if (best < 0) return 1
        var total = 0
        for (n in 1..9) {
            if (!canPlace(g, best, n)) continue
            g[best] = n
            total += countSolutions(g, limit - total)
            g[best] = 0
            if (total >= limit) break
        }
        return total
    }
}
