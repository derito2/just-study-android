package com.dero.juststudykorean.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.progress.CalendarDay
import com.dero.juststudykorean.data.progress.CalendarMonth
import com.dero.juststudykorean.data.progress.ProgressSummary
import com.dero.juststudykorean.data.progress.Recommendation
import com.dero.juststudykorean.data.progress.Timeline
import com.dero.juststudykorean.data.progress.TimelinePoint
import com.dero.juststudykorean.data.progress.TimelineUnit
import com.dero.juststudykorean.data.progress.Today
import com.dero.juststudykorean.data.progress.Weekly
import com.dero.juststudykorean.ui.components.JsBottomBar
import com.dero.juststudykorean.ui.components.niceMax
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** Dashboard with fixed fake data, at 411 dp (OPPO Find X8 Pro). Compare with design/png/dashboard*.png. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class DashboardScreenshotTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private val today = LocalDate.of(2026, 11, 8)

    private fun month(status: Map<Int, String>) = CalendarMonth(
        2026, 11, zone,
        (1..30).map { d ->
            val s = status[d] ?: "none"
            val q = if (s == "none") 0 else 10
            val correct = when (s) { "green" -> 9; "yellow" -> 6; "red" -> 3; else -> 0 }
            CalendarDay(LocalDate.of(2026, 11, d), s != "none", if (s == "none") 0 else 20, q, correct, if (q == 0) null else correct / 10.0, emptyList(), s)
        },
    )

    private fun timeline(unit: TimelineUnit, empty: Boolean = false): Timeline {
        val minutes = listOf(20, 0, 35, 25, 40, 5, 30)
        val acc = listOf(0.7, null, 0.62, 0.86, 0.82, 0.45, 0.8)
        val points = (0 until unit.periods).map { i ->
            val start = when (unit) {
                TimelineUnit.Day -> today.minusDays((unit.periods - 1 - i).toLong())
                TimelineUnit.Week -> today.minusWeeks((unit.periods - 1 - i).toLong()).minusDays(6)
                TimelineUnit.Month -> today.withDayOfMonth(1).minusMonths((unit.periods - 1 - i).toLong())
                TimelineUnit.Year -> today.withDayOfYear(1).minusYears((unit.periods - 1 - i).toLong())
            }
            val k = i % 7
            if (empty) TimelinePoint(start, 0, 0, null, 0, 0, 0)
            else TimelinePoint(start, minutes[k].toLong(), if (acc[k] == null) 0 else 10, acc[k], 12 - k, 6 + k % 3, 4 + k % 4)
        }
        return Timeline(unit, zone, points)
    }

    private val studied = DashboardUi(
        today = today,
        summary = ProgressSummary(320, 250, 0.78, 40, 24_000, 18, null, Today(today, 15, 12, 0.8, 1_500, 2, 40)),
        weekly = Weekly(37, 33, 0.84, 0.89, 50, 60),
        calendar = month(mapOf(1 to "green", 3 to "yellow", 4 to "green", 5 to "green", 6 to "red", 7 to "green", 8 to "green")),
        recommended = listOf(
            Recommendation(1, "3. -아서/어서", "high", 0.9, 0.4, listOf("4/10 in your last 10", "3 mistakes to fix")),
            Recommendation(2, "particles", "medium", 0.5, 0.6, emptyList()),
            Recommendation(3, "-거든요", "low", 0.2, 0.9, emptyList()),
        ),
        timeline = timeline(TimelineUnit.Day),
        previousStreak = 18,
    )

    /** design/png/dashboard-empty.png: not studied today and no quiz data yet. */
    private val nothing = studied.copy(
        summary = studied.summary!!.copy(today = Today(today, 0, 0, null, 0, 0, 0)),
        recommended = emptyList(),
    )

    @Test
    @Config(qualifiers = "w411dp-h1900dp-420dpi")
    fun chartWithNoStudy() = snap("dashboard_chart_empty", studied.copy(timeline = timeline(TimelineUnit.Day, empty = true)))

    private fun snap(name: String, ui: DashboardUi, dark: Boolean = false, fontScale: Float = 1f) =
        captureRoboImage("src/test/screenshots/$name.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                JustStudyTheme(darkTheme = dark) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { DashboardContent(ui, onMore = {}, onUnit = {}, onPractice = {}, onAsk = {}, onRetry = {}) }
                        JsBottomBar(current = TopLevel.Dashboard, onSelect = {})
                    }
                }
            }
        }

    @Test
    fun dashboardStudied() = snap("dashboard_studied", studied)

    @Test
    fun dashboardEmpty() = snap("dashboard_empty", nothing)

    @Test
    fun dashboardDark() = snap("dashboard_dark", studied, dark = true)

    @Test
    @Config(qualifiers = "w411dp-h1900dp-420dpi")
    fun dashboardFullChartWeek() = snap("dashboard_full_week", studied.copy(timeline = timeline(TimelineUnit.Week), unit = TimelineUnit.Week))

    @Test
    fun dashboardAt130Percent() = snap("dashboard_130", studied, fontScale = 1.3f)

    /** The streak went up since last time (17 → 18): the value at the top of its jump, 140 ms in (scale 1.15). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun streakBump() {
        // runComposeUiTest opens a ComponentActivity: registered in Robolectric only, so no ui-test-manifest in the app.
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val activity = android.content.ComponentName(app, androidx.activity.ComponentActivity::class.java)
        org.robolectric.Shadows.shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(activity)
            addIntentFilterForActivity(activity, android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply { addCategory(android.content.Intent.CATEGORY_LAUNCHER) })
        }
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { JustStudyTheme { DashboardContent(studied.copy(previousStreak = 17), onMore = {}, onUnit = {}, onPractice = {}, onAsk = {}, onRetry = {}) } }
            mainClock.advanceTimeBy(140)
            onRoot().captureRoboImage("src/test/screenshots/dashboard_streak_bump.png")
        }
    }

    @Test
    fun niceMaxRoundsUpLikeTheWeb() {
        assertEquals(listOf(5, 5, 7, 10, 20, 40, 100, 200), listOf(0, 5, 7, 10, 12, 33, 95, 101).map(::niceMax))
    }
}
