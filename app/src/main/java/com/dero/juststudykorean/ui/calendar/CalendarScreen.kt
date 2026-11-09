package com.dero.juststudykorean.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.ui.components.CalendarMonthFull
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.LoadingState
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerSize
import com.dero.juststudykorean.ui.components.dayDescription
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

@Composable
fun CalendarScreen(onMore: () -> Unit, onBack: () -> Unit, vm: CalendarViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    CalendarContent(ui, onMore, onBack, vm::previous, vm::next, vm::select, vm::retry)
}

/** Calendar (design/grammar-calendar-settings.md): ← month / month →, the month, the chosen day's detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarContent(
    ui: CalendarUi,
    onMore: () -> Unit,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (java.time.LocalDate) -> Unit,
    onRetry: () -> Unit,
) {
    val c = JustStudyTheme.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar("Calendar", "달력", "Every day you studied, and every day you didn't.", "공부한 날도, 안 한 날도 전부.", scroll, onMore = onMore, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val prev = ui.month.minusMonths(1)
                val next = ui.month.plusMonths(1)
                StickerButton("← ${monthName(prev.monthValue)}", onPrevious, ko = "${prev.monthValue}월", size = StickerSize.Small)
                StickerButton("${monthName(next.monthValue)} →", onNext, ko = "${next.monthValue}월", size = StickerSize.Small, enabled = ui.canGoNext)
            }
            when {
                ui.data != null -> {
                    CalendarMonthFull(ui.data, ui.today, ui.selected, onSelect)
                    ui.selected?.let { d ->
                        ui.data.days.firstOrNull { it.date == d }?.let { day ->
                            Text(
                                dayDescription(day, ui.data.month),
                                Modifier.fillMaxWidth().border(Tokens.Border, c.line, MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                                    .padding(Tokens.Space4).semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Text("Days are counted in ${ui.data.zone.id}.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                ui.error -> ErrorState("Couldn't load the calendar.", onRetry)
                else -> LoadingState("Loading…")
            }
        }
    }
}

private fun monthName(m: Int) = java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
