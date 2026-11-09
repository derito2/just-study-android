package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.progress.CalendarDay
import com.dero.juststudykorean.data.progress.CalendarMonth
import com.dero.juststudykorean.ui.theme.JustStudyColors
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import java.time.LocalDate

internal val koWeekdays = listOf("월", "화", "수", "목", "금", "토", "일")
private val cellShape = RoundedCornerShape(Tokens.RadiusSm)

internal fun JustStudyColors.cell(status: String): Pair<Color, Color> = when (status) {
    "green" -> correctFill to correct
    "yellow" -> warnFill to fillWarn
    "red" -> wrongFill to wrong
    else -> canvas to muted
}

internal fun symbol(status: String) = when (status) { "green" -> "✓"; "yellow" -> "~"; "red" -> "!"; else -> "" }

/** The day's detail (the web's describe(), with its "80 %"): TalkBack, and Calendar's detail card. */
fun dayDescription(day: CalendarDay, month: Int): String {
    val date = "${month}월 ${day.date.dayOfMonth}일"
    if (!day.studied) return "$date: not studied"
    val topics = if (day.topics.isNotEmpty()) ", topics: ${day.topics.joinToString(", ") { shownTopic(it) }}" else ""
    return "$date: ${plural(day.questions, "question")}, ${day.accuracy?.let { "${Math.round(it * 100)} %" } ?: "—"} accuracy, ${day.minutes} min$topics"
}

/** The month's calendar, compact (design/dashboard.md §3): Monday first, 26 dp cells, today with a `brand` ring. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarMonthCompact(month: CalendarMonth, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = JustStudyTheme.colors
    val first = LocalDate.of(month.year, month.month, 1)
    val cells: List<CalendarDay?> = List(first.dayOfWeek.value - 1) { null } + month.days
    Column(
        modifier.fillMaxWidth()
            .border(Tokens.Border, colors.line, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(horizontal = Tokens.Space4, vertical = Tokens.Space3),
    ) {
        Text("${month.year}년 ${month.month}월", style = JustStudyTheme.korean.bodyBold.copy(fontFamily = FontFamily.Default), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Tokens.Space2))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
            koWeekdays.forEach { Text(it, Modifier.weight(1f), style = TextStyle(fontSize = 12.sp), color = colors.muted, textAlign = TextAlign.Center) }
        }
        Spacer(Modifier.height(Tokens.Space1))
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        if (day == null) { Spacer(Modifier.weight(1f)); continue }
                        val (fill, ink) = colors.cell(day.status)
                        Row(
                            Modifier.weight(1f).heightIn(min = 26.dp).background(fill, cellShape)
                                .then(if (day.date == today) Modifier.border(2.dp, colors.brand, cellShape) else Modifier)
                                .semantics { contentDescription = dayDescription(day, month.month) },
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${day.date.dayOfMonth}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = ink)
                            if (day.studied) Text(symbol(day.status), Modifier.padding(start = 2.dp).alpha(0.75f), style = TextStyle(fontSize = 10.sp), color = ink)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Tokens.Space2))
        FlowRow(Modifier.semantics { contentDescription = "Legend" }, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("green" to "80 %+", "yellow" to "50–79 %", "red" to "under 50 %", "none" to "not studied").forEach { (status, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
                    Box(Modifier.size(12.dp).background(colors.cell(status).first, RoundedCornerShape(4.dp)))
                    Text(listOf(symbol(status), label).filter { it.isNotEmpty() }.joinToString(" "), style = TextStyle(fontSize = 11.sp), color = colors.muted)
                }
            }
        }
    }
}
