package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.progress.CalendarDay
import com.dero.juststudykorean.data.progress.CalendarMonth
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * design/quiz.md E, in dark: what H-01 showed on the phone (answer text lost in a pale box). The four option states, the typed
 * answer right and wrong, and the month's three coloured cells. Games' ribbon has its own (games_ribbon_*_dark).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class DarkFeedbackScreenshotTest {
    private fun snap(name: String, content: @Composable () -> Unit) = captureRoboImage("src/test/screenshots/$name.png") {
        CompositionLocalProvider(LocalFrozenMotion provides true) {
            JustStudyTheme(darkTheme = true) {
                Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }

    @Test
    fun optionsInDark() = snap("quiz_options_dark") {
        QuizOption(1, "도서관", OptionState.Open, {})
        QuizOption(2, "학교", OptionState.Correct, {})
        QuizOption(3, "시장", OptionState.Chosen, {})
        QuizOption(4, "근처", OptionState.Other, {})
    }

    @Test
    fun typedAnswersInDark() = snap("quiz_typed_dark") {
        AnswerFeedback(correct = true, given = "도서관", answer = "도서관", korean = true, modifier = Modifier.fillMaxWidth())
        AnswerFeedback(correct = false, given = "to be tall", answer = "to be short", korean = false, modifier = Modifier.fillMaxWidth())
    }

    @Test
    fun calendarCellsInDark() = snap("calendar_cells_dark") {
        val month = CalendarMonth(2026, 11, ZoneId.of("America/Mexico_City"), (1..30).map { d ->
            val s = mapOf(1 to "green", 3 to "yellow", 4 to "red")[d] ?: "none"
            CalendarDay(LocalDate.of(2026, 11, d), s != "none", if (s == "none") 0 else 25, if (s == "none") 0 else 15, if (s == "red") 5 else 12, if (s == "none") null else if (s == "red") 1 / 3.0 else 0.9, emptyList(), s)
        })
        CalendarMonthCompact(month, LocalDate.of(2026, 11, 8))
    }
}
