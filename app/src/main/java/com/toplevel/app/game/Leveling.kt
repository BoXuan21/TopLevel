package com.toplevel.app.game

import com.toplevel.app.data.Stat
import com.toplevel.app.data.StatTotal
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Level curve: XP needed to reach level L (from 1) = 50 * (L-1)^2.
 * L2 = 50, L5 = 800, L10 = 4050, L20 = 18050, L50 ≈ 120k.
 */
object Leveling {
    fun levelFor(xp: Int): Int = floor(sqrt(xp.coerceAtLeast(0) / 50.0)).toInt() + 1
    fun xpForLevel(level: Int): Int = 50 * (level - 1) * (level - 1)

    data class Progress(val level: Int, val intoLevel: Int, val levelSpan: Int) {
        val fraction: Float get() = if (levelSpan == 0) 0f else intoLevel.toFloat() / levelSpan
    }

    fun progress(xp: Int): Progress {
        val l = levelFor(xp)
        val base = xpForLevel(l)
        val next = xpForLevel(l + 1)
        return Progress(l, xp - base, next - base)
    }

    /** Stat "points" are on a gentler curve so they move visibly. */
    fun statLevel(xp: Int): Int = floor(sqrt(xp.coerceAtLeast(0) / 20.0)).toInt() + 1

    fun rank(level: Int): String = when {
        level >= 60 -> "S"
        level >= 40 -> "A"
        level >= 25 -> "B"
        level >= 15 -> "C"
        level >= 7 -> "D"
        else -> "E"
    }

    fun title(level: Int): String = when {
        level >= 60 -> "Apex"
        level >= 40 -> "Vanguard"
        level >= 25 -> "Ascendant"
        level >= 15 -> "Challenger"
        level >= 7 -> "Climber"
        else -> "Awakened"
    }

    fun totals(list: List<StatTotal>): Map<Stat, Int> =
        Stat.entries.associateWith { s -> list.firstOrNull { it.stat == s }?.total ?: 0 }
}

/** Standard XP rewards outside quests. */
object Rewards {
    const val CHECK_IN = 30
    const val FOOD_LOG = 5
    const val FOOD_LOG_DAILY_CAP = 4
    const val WORK_LOG = 10
    const val LEARNED_LOG = 15
    const val TRAINING_LOG = 15
    const val SKILL_LEARNED = 60
    const val ALL_QUESTS_BONUS = 40
    const val PROTEIN_HIT = 20
}
