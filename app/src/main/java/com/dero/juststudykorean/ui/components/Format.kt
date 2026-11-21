package com.dero.juststudykorean.ui.components

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Port of the web's frontend/src/progress/format.ts (the parts the dashboard uses).

/** The web's studyTime: "6 h 40 min", "25 min", "2 h". */
fun studyTime(seconds: Long): String {
    val minutes = Math.round(seconds / 60.0)
    if (minutes < 60) return "$minutes min"
    val rest = minutes % 60
    return if (rest != 0L) "${minutes / 60} h $rest min" else "${minutes / 60} h"
}

/** "84%", or "—" with no answers. */
fun percent(value: Double?): String = if (value == null) "—" else "${Math.round(value * 100)}%"

/** The topic name without its numbering ("3. -아서/어서" → "-아서/어서"). */
fun shownTopic(name: String): String = name.replace(Regex("^\\s*\\d{1,3}[.)]\\s+"), "").ifEmpty { name }

/**
 * design/polish.md P-04: the name of a file as the screens show it. Never the `.pdf`; if it is longer than [maxChars]
 * (characters, not UTF-16 units), its head, an ellipsis and the last 6 characters, which are what tells two files apart.
 */
fun displayName(file: String, maxChars: Int): String {
    val stem = if (file.length > 4 && file.endsWith(".pdf", ignoreCase = true)) file.dropLast(4) else file
    val count = stem.codePointCount(0, stem.length)
    val max = maxChars.coerceAtLeast(DISPLAY_TAIL + 2)
    if (count <= max) return stem
    val head = stem.substring(0, stem.offsetByCodePoints(0, max - DISPLAY_TAIL - 1))
    val tail = stem.substring(stem.offsetByCodePoints(0, count - DISPLAY_TAIL))
    return "$head…$tail"
}

private const val DISPLAY_TAIL = 6

fun plural(n: Int, singular: String, pluralForm: String = "${singular}s") = "$n ${if (n == 1) singular else pluralForm}"

/** Label of a chart period: "Nov 8" (day, week), "Nov" or "Jan 27" (month), "2026" (year). */
fun periodLabel(start: LocalDate, unit: String): String {
    val month = start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    return when (unit) {
        "year" -> "${start.year}"
        "month" -> if (start.monthValue == 1) "$month ${start.year.toString().takeLast(2)}" else month
        else -> "$month ${start.dayOfMonth}"
    }
}
