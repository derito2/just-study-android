package com.dero.juststudykorean.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dero.juststudykorean.data.progress.TimelineUnit
import com.dero.juststudykorean.ui.components.CalendarMonthCompact
import com.dero.juststudykorean.ui.components.ErrorState
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.ProgressChart
import com.dero.juststudykorean.ui.components.StatTile
import com.dero.juststudykorean.ui.components.TodayHero
import com.dero.juststudykorean.ui.components.accuracyTrend
import com.dero.juststudykorean.ui.components.percent
import com.dero.juststudykorean.ui.components.streakLook
import com.dero.juststudykorean.ui.components.studyTime
import com.dero.juststudykorean.ui.components.wordsTrend
import com.dero.juststudykorean.ui.theme.Tokens

@Composable
fun DashboardScreen(onMore: () -> Unit, onOpenQuiz: (Long) -> Unit, onAsk: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openQuiz.collect(onOpenQuiz) }
    DashboardContent(ui, onMore = onMore, onUnit = viewModel::setUnit, onPractice = { viewModel.practice(it) }, onAsk = onAsk, onRetry = viewModel::retry)
}

/** Design/dashboard.md: top bar, four KPIs, Today's session on your photo, the month and Progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    ui: DashboardUi,
    onMore: () -> Unit,
    onUnit: (TimelineUnit) -> Unit,
    onPractice: (String) -> Unit,
    onAsk: () -> Unit,
    onRetry: () -> Unit,
    photo: Boolean = true,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar(
            "Dashboard", "대시보드", "No excuses. This is where you really stand.", "변명은 없어. 이게 진짜 네 실력이야.",
            scroll, onMore = onMore, onBack = null,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
        ) {
            if (ui.error) ErrorState("Couldn't load your progress.", onRetry)
            val s = ui.summary
            val w = ui.weekly
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                val streak = s?.streakDays
                StatTile(
                    "Study streak", if (streak == null) "—" else "$streak ${if (streak == 1) "day" else "days"}",
                    Modifier.weight(1f).fillMaxHeight(),
                    look = streak?.let { streakLook(it) },
                    bump = streak != null && ui.previousStreak != null && streak > ui.previousStreak,
                )
                StatTile("Study time", s?.let { studyTime(it.totalStudySeconds) } ?: "—", Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                StatTile(
                    "Words learned this week", w?.let { "${it.wordsThisWeek} ${if (it.wordsThisWeek == 1) "word" else "words"}" } ?: "—",
                    Modifier.weight(1f).fillMaxHeight(), trend = w?.let { wordsTrend(it.wordsThisWeek, it.wordsLastWeek) },
                )
                StatTile(
                    "Quiz accuracy this week", w?.let { percent(it.accuracyThisWeek) } ?: "—",
                    Modifier.weight(1f).fillMaxHeight(), trend = w?.let { accuracyTrend(it.accuracyThisWeek, it.accuracyLastWeek) },
                )
            }
            TodayHero(s?.today, ui.recommended, onPractice, onAsk, photo = photo, notice = ui.notice)
            ui.calendar?.let { CalendarMonthCompact(it, ui.today) }
            ProgressChart(ui.timeline, ui.unit, onUnit)
        }
    }
}
