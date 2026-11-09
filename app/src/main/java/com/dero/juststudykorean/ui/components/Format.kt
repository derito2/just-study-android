package com.dero.juststudykorean.ui.components

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Port of the web's frontend/src/progress/format.ts (the parts the dashboard uses).

/** "6 h 40 m", "25 m" (design/dashboard.md: the web's studyTime with "m"). */
fun studyTime(seconds: Long): String {
    val minutes = Math.round(seconds / 60.0)
    if (minutes < 60) return "$minutes m"
    val rest = minutes % 60
    return if (rest != 0L) "${minutes / 60} h $rest m" else "${minutes / 60} h"
}

/** "84%", or "—" with no answers. */
fun percent(value: Double?): String = if (value == null) "—" else "${Math.round(value * 100)}%"

/** The topic name without its numbering ("3. -아서/어서" → "-아서/어서"). */
fun shownTopic(name: String): String = name.replace(Regex("^\\s*\\d{1,3}[.)]\\s+"), "").ifEmpty { name }

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
