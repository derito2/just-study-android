package com.dero.juststudykorean.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.data.progress.CalendarMonth
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.progress.ProgressSummary
import com.dero.juststudykorean.data.progress.Recommendation
import com.dero.juststudykorean.data.progress.Timeline
import com.dero.juststudykorean.data.progress.TimelineUnit
import com.dero.juststudykorean.data.progress.Weekly
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.quiz.QuizRequest
import com.dero.juststudykorean.data.time.AppClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Everything the dashboard shows; null while it loads. */
data class DashboardUi(
    val today: LocalDate,
    val summary: ProgressSummary? = null,
    val weekly: Weekly? = null,
    val calendar: CalendarMonth? = null,
    val recommended: List<Recommendation>? = null,
    val timeline: Timeline? = null,
    val unit: TimelineUnit = TimelineUnit.Day,
    /** The streak seen on the last visit: the number jumps when it grows. */
    val previousStreak: Int? = null,
    val error: Boolean = false,
    val notice: String? = null,
)

private data class Loaded(val summary: ProgressSummary, val weekly: Weekly, val calendar: CalendarMonth, val recommended: List<Recommendation>, val timeline: Timeline)

/** Dashboard: the web's figures from Room, computed in :core:data off the main thread; refreshed when the data changes. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val progress: ProgressRepository,
    private val quiz: QuizRepository,
    private val prefs: UserPrefs,
    clock: AppClock,
) : ViewModel() {
    private val today = clock.now().atZone(clock.zone).toLocalDate()
    private val retry = MutableStateFlow(0)
    private val previousStreak = MutableStateFlow<Int?>(null)
    private val notice = MutableStateFlow<String?>(null)
    private val quizzes = Channel<Long>(Channel.BUFFERED)

    /** The id of the quiz "Practice this" created: the app opens Quiz with it. */
    val openQuiz = quizzes.receiveAsFlow()

    init {
        viewModelScope.launch { previousStreak.value = prefs.lastStreak.first() }
    }

    // The month's calendar flow fires on every change to answers, sessions, questions or topics.
    private val loaded = combine(retry, prefs.chartUnit) { _, unit -> unit }.flatMapLatest { unit ->
        progress.calendar(today.year, today.monthValue).map<CalendarMonth, Loaded?> { month ->
            val summary = progress.summary()
            previousStreak.filterNotNull().first()
            prefs.setLastStreak(summary.streakDays)
            Loaded(summary, progress.weekly(), month, progress.recommended(3), progress.timeline(unit))
        }.catch { emit(null) }
    }

    val state: StateFlow<DashboardUi> = combine(loaded, prefs.chartUnit, previousStreak, notice) { data, unit, previous, notice ->
        DashboardUi(
            today = today, summary = data?.summary, weekly = data?.weekly, calendar = data?.calendar, recommended = data?.recommended,
            timeline = data?.timeline, unit = unit, previousStreak = previous, error = data == null, notice = notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi(today))

    fun setUnit(unit: TimelineUnit) = viewModelScope.launch { prefs.setChartUnit(unit) }

    fun retry() { retry.value++ }

    /** Practice this: a 5-question quiz on the topic, then Quiz. */
    fun practice(topic: String) = viewModelScope.launch {
        notice.value = null
        val run = runCatching { quiz.generate(QuizRequest(topic = topic, count = 5)) }.getOrNull()
        if (run != null) quizzes.send(run.id) else notice.value = "Not enough material in your notes for that topic yet."
    }
}
