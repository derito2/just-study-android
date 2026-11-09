package com.dero.juststudykorean.korean

import java.math.BigDecimal
import java.math.RoundingMode

// Port of backend/app/flashcards/scheduler.py (simplified SM-2, like Anki), checked against golden/sm2.json.

private const val MIN_EASE = 1.3
private const val MAX_INTERVAL_HOURS = 365 * 24 // a card comes back at least once a year

data class CardState(val ease: Double = 2.5, val intervalHours: Int = 0, val repetitions: Int = 0)

/** Review options (like an Anki deck's): Again in minutes, the first Hard/Good/Easy in hours. */
data class Scheduling(val againMinutes: Int = 10, val hardHours: Int = 12, val goodHours: Int = 24, val easyHours: Int = 48)

enum class Rating { Again, Hard, Good, Easy }

data class Next(val ease: Double, val intervalHours: Int, val repetitions: Int, val dueInMinutes: Long)

// Python's round(x, 2): half to even on the double's exact value.
private fun round2(x: Double) = BigDecimal(x).setScale(2, RoundingMode.HALF_EVEN).toDouble()

// Python's round(x): half to even.
private fun pyRound(x: Double) = Math.rint(x).toLong()

private fun grow(previous: Int, factor: Double): Int =
    minOf(MAX_INTERVAL_HOURS.toLong(), maxOf(previous + 1L, pyRound(previous * factor))).toInt()

fun schedule(state: CardState, rating: Rating, cfg: Scheduling = Scheduling()): Next {
    var ease = state.ease
    val prev = state.intervalHours
    val new = state.repetitions == 0
    val hours = when (rating) {
        Rating.Again -> return Next(round2(maxOf(MIN_EASE, ease - 0.20)), 0, 0, cfg.againMinutes.toLong())
        Rating.Hard -> { ease = maxOf(MIN_EASE, ease - 0.15); if (new) cfg.hardHours else grow(prev, 1.2) }
        Rating.Good -> if (new) cfg.goodHours else grow(prev, ease)
        Rating.Easy -> (if (new) cfg.easyHours else grow(prev, ease * 1.3)).also { ease += 0.15 }
    }.coerceAtMost(MAX_INTERVAL_HOURS)
    return Next(round2(ease), hours, state.repetitions + 1, hours * 60L)
}

/** When the card comes back, as the web shows it: "10 min", "12 h", "2 d", "3 mo", "1 y". */
fun label(dueInMinutes: Long): String {
    val minutes = dueInMinutes
    if (minutes < 60) return "$minutes min"
    val hours = pyRound(minutes / 60.0)
    if (hours < 48) return "$hours h"
    val days = pyRound(hours / 24.0)
    if (days < 30) return "$days d"
    if (days < 365) return "${pyRound(days / 30.0)} mo"
    return "${pyRound(days / 365.0)} y"
}
