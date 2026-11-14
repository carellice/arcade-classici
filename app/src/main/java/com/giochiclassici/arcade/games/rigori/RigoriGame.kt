package com.giochiclassici.arcade.games.rigori

import kotlin.math.hypot
import kotlin.random.Random

/**
 * Logica dei rigori, senza grafica. Coordinate della porta: x da -1 (palo sinistro) a 1 (palo destro),
 * y da 0 (terra) a 1 (traversa). Un tiro fuori da questo rettangolo è fuori.
 */
enum class RigoriMode(val label: String) { MATCH("Partita"), SHOOT("Tiratore"), SAVE("Portiere") }

/** read: quanto spesso il portiere CPU indovina il tiro. tell: quanto spesso la rincorsa CPU tradisce il lato giusto. */
enum class RigoriDifficulty(val label: String, val read: Float, val tell: Float) {
    EASY("Facile", 0.2f, 0.85f),
    NORMAL("Normale", 0.35f, 0.7f),
    HARD("Difficile", 0.5f, 0.55f),
}

enum class Outcome { GOAL, SAVED, OUT }

data class Kick(val tx: Float, val ty: Float, val kx: Float, val ky: Float, val outcome: Outcome, val byPlayer: Boolean)

/** Tiro della CPU in attesa del tuffo del giocatore. lean: -1 o 1, il lato verso cui si sbilancia il tiratore. */
data class CpuShot(val tx: Float, val ty: Float, val lean: Int)

object Rigori {
    /** Raggio d'azione del portiere attorno al punto del tuffo. */
    const val REACH = 0.5f
    /** Imprecisione del tiro: più si mira all'angolo, più si rischia di finire fuori. */
    const val SPREAD = 0.11f
    const val CHALLENGE_KICKS = 10
    const val SERIES = 5

    fun outcome(tx: Float, ty: Float, kx: Float, ky: Float): Outcome = when {
        tx < -1f || tx > 1f || ty > 1f || ty < 0f -> Outcome.OUT
        hypot(tx - kx, (ty - ky) * 1.2f) < REACH -> Outcome.SAVED
        else -> Outcome.GOAL
    }
}

class RigoriGame(val mode: RigoriMode, val difficulty: RigoriDifficulty, private val rnd: Random = Random.Default) {
    val myKicks = mutableListOf<Outcome>()
    val cpuKicks = mutableListOf<Outcome>()
    var pending: CpuShot? = null
        private set

    val myGoals get() = myKicks.count { it == Outcome.GOAL }
    val cpuGoals get() = cpuKicks.count { it == Outcome.GOAL }
    val saves get() = cpuKicks.count { it != Outcome.GOAL }

    /** Nella partita si alterna: prima tiri tu, poi la CPU. */
    val playerShoots: Boolean
        get() = when (mode) {
            RigoriMode.SHOOT -> true
            RigoriMode.SAVE -> false
            RigoriMode.MATCH -> myKicks.size == cpuKicks.size
        }

    val over: Boolean
        get() = when (mode) {
            RigoriMode.SHOOT -> myKicks.size >= Rigori.CHALLENGE_KICKS
            RigoriMode.SAVE -> cpuKicks.size >= Rigori.CHALLENGE_KICKS
            RigoriMode.MATCH -> {
                val n = Rigori.SERIES
                if (myKicks.size <= n && cpuKicks.size <= n && !(myKicks.size == n && cpuKicks.size == n)) {
                    // Serie da cinque: finisce prima se una squadra non può più rimontare.
                    myGoals + (n - myKicks.size) < cpuGoals || cpuGoals + (n - cpuKicks.size) < myGoals
                } else {
                    // Oltre il quinto: a oltranza, a coppie di tiri.
                    myKicks.size == cpuKicks.size && myGoals != cpuGoals
                }
            }
        }

    val playerWon get() = over && mode == RigoriMode.MATCH && myGoals > cpuGoals

    /** Punteggio delle sfide da 10 tiri: gol segnati o tiri parati. */
    val score get() = if (mode == RigoriMode.SAVE) saves else myGoals

    private fun gauss() = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) / 0.5f

    /** Tiri tu verso (ax, ay); il portiere CPU sceglie dove tuffarsi. */
    fun shoot(ax: Float, ay: Float): Kick {
        check(!over && playerShoots)
        val tx = ax + gauss() * Rigori.SPREAD
        val ty = ay + gauss() * Rigori.SPREAD * 0.7f
        val (kx, ky) = if (rnd.nextFloat() < difficulty.read) {
            (tx + gauss() * 0.15f).coerceIn(-1f, 1f) to (ty + gauss() * 0.1f).coerceIn(0.1f, 0.9f)
        } else {
            (rnd.nextFloat() * 1.8f - 0.9f) to (0.2f + rnd.nextFloat() * 0.5f)
        }
        val out = Rigori.outcome(tx, ty, kx, ky)
        myKicks += out
        return Kick(tx, ty, kx, ky, out, byPlayer = true)
    }

    /** La CPU prende la rincorsa: il tiro è già deciso, la postura a volte lo tradisce. */
    fun prepareCpuShot(): CpuShot {
        check(!over && !playerShoots)
        pending?.let { return it }
        val side = if (rnd.nextBoolean()) 1 else -1
        val tx = side * (0.25f + rnd.nextFloat() * 0.6f) + gauss() * Rigori.SPREAD * 0.6f
        val ty = 0.1f + rnd.nextFloat() * 0.75f
        val lean = if (rnd.nextFloat() < difficulty.tell) side else -side
        return CpuShot(tx, ty, lean).also { pending = it }
    }

    /** Il giocatore si è tuffato verso (kx, ky): si risolve il tiro in attesa. */
    fun save(kx: Float, ky: Float): Kick {
        val shot = pending ?: prepareCpuShot()
        pending = null
        val out = Rigori.outcome(shot.tx, shot.ty, kx, ky)
        cpuKicks += out
        return Kick(shot.tx, shot.ty, kx, ky, out, byPlayer = false)
    }
}
