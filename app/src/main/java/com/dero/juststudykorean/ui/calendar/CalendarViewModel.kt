package com.dero.juststudykorean.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.progress.CalendarMonth
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.AppClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUi(
    val month: YearMonth,
    val today: LocalDate,
    val data: CalendarMonth? = null,
    val selected: LocalDate? = null,
    val error: Boolean = false,
) {
    /** The next month only up to the current one. */
    val canGoNext: Boolean get() = month < YearMonth.from(today)
}

/** Calendar (design/grammar-calendar-settings.md): one month at a time, days in the phone's zone. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(private val progress: ProgressRepository, clock: AppClock) : ViewModel() {
    private val today = clock.now().atZone(clock.zone).toLocalDate()
    private val month = MutableStateFlow(YearMonth.from(today))
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val retry = MutableStateFlow(0)

    private val data = combine(month, retry) { m, _ -> m }.flatMapLatest { m ->
        progress.calendar(m.year, m.monthValue).map<CalendarMonth, CalendarMonth?> { it }.catch { emit(null) }
    }

    val state: StateFlow<CalendarUi> = combine(month, data, selected) { m, d, s ->
        CalendarUi(m, today, d?.takeIf { it.year == m.year && it.month == m.monthValue }, s, error = d == null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUi(YearMonth.from(today), today))

    fun previous() { month.value = month.value.minusMonths(1); selected.value = null }

    fun next() { if (month.value < YearMonth.from(today)) { month.value = month.value.plusMonths(1); selected.value = null } }

    fun select(day: LocalDate) { selected.value = if (selected.value == day) null else day }

    fun retry() { retry.value++ }
}
