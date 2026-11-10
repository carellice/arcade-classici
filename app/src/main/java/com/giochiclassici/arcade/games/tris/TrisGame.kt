package com.giochiclassici.arcade.games.tris

import kotlin.random.Random

enum class TrisMode { CPU, LOCAL }

/** Quanto spesso la CPU sceglie la mossa perfetta invece di una a caso. */
enum class TrisDifficulty(val label: String, val smart: Float) {
    EASY("Facile", 0.25f),
    NORMAL("Normale", 0.7f),
    HARD("Imbattibile", 1f),
}

/** Le regole del tris: 1 = X, 2 = O, 0 = vuota. */
object Tris {
    val LINES = listOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
    )

    fun winLine(b: IntArray): IntArray? = LINES.firstOrNull { (a, c, d) -> b[a] != 0 && b[a] == b[c] && b[a] == b[d] }

    fun full(b: IntArray) = b.none { it == 0 }

    /** Punteggio minimax per [me] che deve muovere come [turn]: +10 vittoria, −10 sconfitta, 0 pari. */
    private fun score(b: IntArray, me: Int, turn: Int, depth: Int): Int {
        winLine(b)?.let { return if (b[it[0]] == me) 10 - depth else depth - 10 }
        if (full(b)) return 0
        var best = if (turn == me) Int.MIN_VALUE else Int.MAX_VALUE
        for (i in 0 until 9) if (b[i] == 0) {
            b[i] = turn
            val s = score(b, me, 3 - turn, depth + 1)
            b[i] = 0
            best = if (turn == me) maxOf(best, s) else minOf(best, s)
        }
        return best
    }

    /** La mossa migliore per [player]: a parità di punteggio sceglie a caso, così le partite cambiano. */
    fun bestMove(b: IntArray, player: Int, random: Random = Random.Default): Int {
        val free = (0 until 9).filter { b[it] == 0 }
        val scored = free.map { i ->
            b[i] = player
            val s = score(b, player, 3 - player, 1)
            b[i] = 0
            i to s
        }
        val top = scored.maxOf { it.second }
        return scored.filter { it.second == top }.random(random).first
    }
}

/** Una partita. Chi comincia si alterna a ogni round; in 1vCPU il giocatore è sempre X. */
class TrisGame(val mode: TrisMode, val difficulty: TrisDifficulty, private val random: Random = Random.Default) {
    val board = IntArray(9)
    var turn = 1
        private set
    var starter = 1
        private set
    var winLine: IntArray? = null
        private set
    var over = false
        private set
    var xWins = 0
        private set
    var oWins = 0
        private set
    var draws = 0
        private set

    val winner get() = winLine?.let { board[it[0]] } ?: 0
    val cpuTurn get() = mode == TrisMode.CPU && turn == 2 && !over

    /** Mette il segno di chi è di turno in [i]. Ritorna false se la mossa non vale. */
    fun play(i: Int): Boolean {
        if (over || i !in 0 until 9 || board[i] != 0) return false
        board[i] = turn
        winLine = Tris.winLine(board)
        when {
            winLine != null -> { over = true; if (turn == 1) xWins++ else oWins++ }
            Tris.full(board) -> { over = true; draws++ }
            else -> turn = 3 - turn
        }
        return true
    }

    fun cpuMove(): Int {
        val free = (0 until 9).filter { board[it] == 0 }
        return if (random.nextFloat() < difficulty.smart) Tris.bestMove(board, 2, random) else free.random(random)
    }

    fun nextRound() {
        board.fill(0)
        winLine = null
        over = false
        starter = 3 - starter
        turn = starter
    }
}
