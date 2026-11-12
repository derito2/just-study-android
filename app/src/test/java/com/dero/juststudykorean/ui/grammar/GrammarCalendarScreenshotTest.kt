package com.dero.juststudykorean.ui.grammar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.grammar.GrammarFilter
import com.dero.juststudykorean.data.grammar.GrammarPointUi
import com.dero.juststudykorean.data.grammar.GrammarSource
import com.dero.juststudykorean.data.progress.CalendarDay
import com.dero.juststudykorean.data.progress.CalendarMonth
import com.dero.juststudykorean.ui.calendar.CalendarContent
import com.dero.juststudykorean.ui.calendar.CalendarUi
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Grammar and Calendar with fixed fake data, at 411 dp (no design PNGs: design/grammar-calendar-settings.md). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class GrammarCalendarScreenshotTest {
    private val src = GrammarSource(1, "Unit 3 · At school.pdf", 4)
    private val points = listOf(
        GrammarPointUi(1, "ending", "endings", "-아서/어서", "because, so (reason)", listOf("V/A + 아서/어서"), listOf("바쁘다 → 바빠서" to "busy → because I'm busy"), listOf("Not with commands."), listOf("Past goes on the second verb."), src),
        GrammarPointUi(2, "particle", "particles", "에서", "at, in (place of an action)", emptyList(), listOf("도서관에서 공부해요." to "I study at the library."), emptyList(), emptyList(), src),
        GrammarPointUi(3, "ending", "endings", "-거든요", "you see (giving a reason the listener doesn't know)", emptyList(), emptyList(), emptyList(), emptyList(), src),
        GrammarPointUi(4, "tense", "tenses", "-았/었-", "past tense", emptyList(), emptyList(), emptyList(), emptyList(), src),
    )
    private val today = LocalDate.of(2026, 11, 8)
    private val month = CalendarMonth(2026, 11, ZoneId.of("America/Mexico_City"), (1..30).map { d ->
        val s = mapOf(1 to "green", 3 to "yellow", 4 to "green", 5 to "green", 6 to "red", 7 to "green", 8 to "green")[d] ?: "none"
        CalendarDay(LocalDate.of(2026, 11, d), s != "none", if (s == "none") 0 else 25, if (s == "none") 0 else 15, if (s == "red") 5 else 12, if (s == "none") null else if (s == "red") 1 / 3.0 else 0.8, if (s == "none") emptyList() else listOf("particles", "-아서/어서"), s)
    })

    private fun snap(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { JustStudyTheme(darkTheme = false) { content() } }
        }

    @Composable
    private fun Grammar(ui: GrammarUi) = GrammarContent(ui, {}, {}, {}, {}, {}, {}, {})

    @Composable
    private fun Calendar(ui: CalendarUi) = CalendarContent(ui, {}, {}, {}, {}, {}, {})

    @Test
    fun grammarList() = snap("grammar_list") { Grammar(GrammarUi(points = points)) }

    @Test
    fun grammarOpen() = snap("grammar_open") { Grammar(GrammarUi(points = points, open = 1)) }

    /** Phase 6, use 7: with Nano, Explain it simpler and Practice sentence, and what the model wrote (AiMark, PRACTICE). */
    @Test
    fun grammarOpenAi() = snap("grammar_open_ai") {
        Grammar(GrammarUi(points = points, open = 1, ai = true, written = mapOf(1L to com.dero.juststudykorean.ui.grammar.GrammarWritten(
            simpler = "Use it to say something happened before something else, like in your example.",
            practice = "학교에 가서 공부해요." to "I go to school and study.",
        ))))
    }

    @Test
    fun grammarEmpty() = snap("grammar_empty") { Grammar(GrammarUi(points = emptyList(), any = false)) }

    @Test
    fun grammarNoMatch() = snap("grammar_no_match") { Grammar(GrammarUi(GrammarFilter.Particles, "xyz", points = emptyList())) }

    @Test
    fun calendarMonth() = snap("calendar_month") { Calendar(CalendarUi(YearMonth.of(2026, 11), today, month)) }

    @Test
    fun calendarDaySelected() = snap("calendar_day") { Calendar(CalendarUi(YearMonth.of(2026, 11), today, month, selected = LocalDate.of(2026, 11, 6))) }

    @Test
    fun calendarAt130Percent() = snap("calendar_130", fontScale = 1.3f) { Calendar(CalendarUi(YearMonth.of(2026, 11), today, month, selected = today)) }
}
