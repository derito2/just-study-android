package com.dero.juststudykorean.ui.calendar

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.progress.CalendarMonth
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** design/polish.md P-45 and P-47: one header (chevron, 2026년 11월, chevron), the zone note inside the card. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CalendarHeaderTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private val zone = ZoneId.of("America/Mexico_City")

    /** Showing November 2026 while "today" is [today]: the next chevron works only if today is later. */
    private fun content(today: LocalDate, previous: () -> Unit = {}) = compose.setContent {
        val month = CalendarMonth(2026, 11, zone, emptyList())
        JustStudyTheme(darkTheme = false) { CalendarContent(CalendarUi(YearMonth.of(2026, 11), today, month), {}, {}, previous, {}, {}, {}) }
    }

    @Test
    fun theHeaderNamesTheMonthAndBothNeighbours() {
        var went = 0
        content(LocalDate.of(2026, 11, 8), previous = { went++ })
        compose.onNodeWithText("2026년 11월").assertExists()
        compose.onNodeWithContentDescription("Previous month, October").assertIsEnabled().performClick()
        assertEquals(1, went)
        compose.onNodeWithContentDescription("Next month, December").assertIsNotEnabled() // this is the current month
    }

    @Test
    fun withALaterMonthTheNextChevronWorks() {
        content(LocalDate.of(2026, 12, 8))
        compose.onNodeWithContentDescription("Next month, December").assertIsEnabled()
    }

    @Test
    fun theZoneNoteIsInsideTheCard() {
        content(LocalDate.of(2026, 11, 8))
        compose.onNodeWithText("Days are counted in America/Mexico_City.").assertExists()
    }
}
