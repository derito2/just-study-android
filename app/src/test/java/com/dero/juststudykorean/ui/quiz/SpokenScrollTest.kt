package com.dero.juststudykorean.ui.quiz

import com.dero.juststudykorean.ui.components.speech.SpeakingUi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.quiz.QuizRun
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** design/polish.md P-32: with the notes' answer shown, *Listen* can always be scrolled above *Next question* (even on a short screen). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h560dp-420dpi")
class SpokenScrollTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private val sentence = QuestionsEntity(5, "write_ko", "Write in Korean: \"I go to school\"", null, "저는 학교에 가요", null, 1, null, "[\"1:4:0\"]", null, "rules", 0)

    @Test
    fun listenCanBeScrolledAboveNextQuestion() {
        val ui = QuestionUi(loading = false, run = QuizRun(9, listOf(sentence, sentence.copy(id = 6))), documents = mapOf(5L to "Unit 3 · At school.pdf"), spoken = setOf(5L))
        compose.setContent {
            CompositionLocalProvider(LocalFrozenMotion provides true) {
                JustStudyTheme { QuestionContent(ui, {}, {}, {}, {}, speaking = SpeakingUi(shownAnswer = true, attempts = 1), voiceReady = true, permissionNeeded = false) }
            }
        }
        compose.onNodeWithContentDescription("Listen").performScrollTo()
        val listen = compose.onNodeWithContentDescription("Listen").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithText("Next question", ignoreCase = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Listen (bottom ${listen.bottom}) must end above Next question (top ${next.top})", listen.bottom <= next.top)
    }

    /** Show answer brings the answer and Listen into view by itself: nobody should have to know the card scrolls. */
    @Test
    fun showingTheAnswerScrollsItIntoView() {
        val ui = QuestionUi(loading = false, run = QuizRun(9, listOf(sentence, sentence.copy(id = 6))), documents = mapOf(5L to "Unit 3 · At school.pdf"), spoken = setOf(5L))
        var speaking by mutableStateOf(SpeakingUi())
        compose.setContent {
            CompositionLocalProvider(LocalFrozenMotion provides true) {
                JustStudyTheme { QuestionContent(ui, {}, {}, {}, {}, speaking = speaking, voiceReady = true, permissionNeeded = false) }
            }
        }
        compose.waitForIdle()
        speaking = SpeakingUi(shownAnswer = true, attempts = 1) // what tapping Show answer does
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Listen").assertIsDisplayed()
    }
}
