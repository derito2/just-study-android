package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
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
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import java.time.LocalDate

/** The Calendar screen's month (design/grammar-calendar-settings.md): 52 dp cells, today in `brand`, the chosen day in `action`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarMonthFull(month: CalendarMonth, today: LocalDate, selected: LocalDate?, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier, footnote: String? = null) {
    val c = JustStudyTheme.colors
    val shape = RoundedCornerShape(Tokens.RadiusSm)
    val first = LocalDate.of(month.year, month.month, 1)
    val cells: List<CalendarDay?> = List(first.dayOfWeek.value - 1) { null } + month.days
    Column(
        modifier.fillMaxWidth().border(Tokens.Border, c.line, MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(Tokens.Space4),
    ) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
            koWeekdays.forEach { Text(it, Modifier.weight(1f), style = TextStyle(fontSize = 12.sp), color = c.muted, textAlign = TextAlign.Center) }
        }
        Spacer(Modifier.height(Tokens.Space1))
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        if (day == null) { Spacer(Modifier.weight(1f)); continue }
                        val (fill, ink) = c.cell(day.status)
                        val ring = if (day.date == selected) c.action else null // P-13: today's ring is outside, below
                        Column(
                            Modifier.weight(1f).height(52.dp).background(fill, shape)
                                .then(if (day.date == today) Modifier.todayRing(c.brand) else Modifier)
                                .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier)
                                .selectable(day.date == selected, role = Role.Button) { onSelect(day.date) }
                                .semantics { contentDescription = dayDescription(day, month.month) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text("${day.date.dayOfMonth}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = ink)
                            if (day.studied) Text(symbol(day.status), Modifier.alpha(0.75f), style = TextStyle(fontSize = 12.sp), color = ink)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Tokens.Space3))
        CalendarLegend(long = true)
        if (footnote != null) ZoneNote(footnote, Modifier.padding(top = Tokens.Space3))
    }
}
