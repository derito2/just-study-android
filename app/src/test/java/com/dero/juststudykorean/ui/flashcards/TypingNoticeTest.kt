package com.dero.juststudykorean.ui.flashcards

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.layout.boundsInRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.FlashcardsEntity
import com.dero.juststudykorean.data.flashcards.CardDetails
import com.dero.juststudykorean.data.flashcards.StudyCard
import com.dero.juststudykorean.korean.Scheduling
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.dero.juststudykorean.data.prefs.CardDirection

/** design/polish.md P-27: *Typing works on word cards.* goes under the switch's label, not inside the card; no word cards → switch off and locked. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class TypingNoticeTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun entity(id: Long, front: String) = FlashcardsEntity(id, null, front, "meaning", front, "meaning", null, null, null, 1, 2.5, 0, 0, 0, "1:4:0", null, null, null)
    private val grammar = StudyCard(entity(1, "-거든요"), true, CardDirection.KoToEn)
    private val word = StudyCard(entity(2, "도서관"), true, CardDirection.KoToEn)
    private val details = mapOf(1L to CardDetails(structure = listOf("V + 거든요")), 2L to CardDetails())

    private fun show(queue: List<StudyCard>, typeAnswer: Boolean) = compose.setContent {
        CompositionLocalProvider(LocalFrozenMotion provides true) {
            JustStudyTheme {
                StudyContent(StudyUi(loading = false, queue = queue, details = details), "Deck", CardDirection.KoToEn, typeAnswer, Scheduling(), {}, {}, {}, {}, {}, {})
            }
        }
    }

    @Test
    fun noticeSitsUnderTheSwitchAndNotInsideTheCard() {
        show(listOf(grammar), typeAnswer = true)
        val notice = compose.onNodeWithText("Typing works on word cards.").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("Type the answer").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithText("-거든요").fetchSemanticsNode().boundsInRoot
        assertTrue("under the label", notice.top >= label.bottom - 1f)
        assertTrue("above the card's content", notice.bottom <= card.top)
    }

    @Test
    fun switchIsLockedWhenNoCardInTheDeckCanBeTyped() {
        show(listOf(grammar), typeAnswer = false)
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("Typing works on word cards.").assertExists()
    }

    @Test
    fun switchWorksWhenThereAreWordCards() {
        show(listOf(grammar, word), typeAnswer = false)
        compose.onNode(isToggleable()).assertIsEnabled()
        compose.onNodeWithText("Typing works on word cards.").assertDoesNotExist()
    }
}
