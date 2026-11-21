package com.dero.juststudykorean.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.progress.Recommendation
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** design/polish.md P-10 and P-11: what the hero offers with nothing to fix, and its chips. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TodayHeroTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun theChipsAreTheThreeFactsInOrder() {
        assertEquals(listOf("mastery 10 %", "25 days ago", "1 card due"), heroChips(listOf("mastery 10 % (1 answer)", "not reviewed in 25 days", "1 flashcard due")))
        assertEquals(listOf("mastery 70 %", "3 cards due"), heroChips(listOf("mastery 70 % (19 answers)", "3 flashcards due")))
        assertEquals(listOf("never reviewed"), heroChips(listOf("new topic: not practised yet")))
        assertEquals(listOf("1 day ago"), heroChips(listOf("not reviewed in 1 days")))
        assertEquals(emptyList<String>(), heroChips(emptyList()))
        assertEquals(listOf("something new"), heroChips(listOf("something new"))) // a reason the chips do not know is not lost
    }

    private fun hero(documents: Boolean, recommended: List<Recommendation>, library: () -> Unit = {}, quiz: () -> Unit = {}) = compose.setContent {
        JustStudyTheme(darkTheme = false) { TodayHero(null, recommended, {}, {}, photo = false, hasDocuments = documents, onGoLibrary = library, onStartQuiz = quiz) }
    }

    @Test
    fun withoutDocumentsAndWithoutAnswersItOffersTheLibrary() {
        var went = false
        hero(documents = false, recommended = emptyList(), library = { went = true })
        compose.onNodeWithText("GO TO LIBRARY").assertIsDisplayed().performClick()
        assertTrue(went)
        compose.onNodeWithText("START A QUIZ").assertDoesNotExist()
    }

    @Test
    fun withDocumentsAndWithoutAnswersItOffersAQuiz() {
        var went = false
        hero(documents = true, recommended = emptyList(), quiz = { went = true })
        compose.onNodeWithText("START A QUIZ").assertIsDisplayed().performClick()
        assertTrue(went)
        compose.onNodeWithText("GO TO LIBRARY").assertDoesNotExist()
    }

    @Test
    fun withDataItShowsChipsAndTwoStackedButtons() {
        hero(true, listOf(Recommendation(1, "3. -아서/어서", "high", 1.0, 0.1, listOf("mastery 10 % (1 answer)", "not reviewed in 25 days", "1 flashcard due"))))
        compose.onNodeWithText("mastery 10 %").assertIsDisplayed()
        compose.onNodeWithText("25 days ago").assertIsDisplayed()
        compose.onNodeWithText("1 card due").assertIsDisplayed()
        compose.onNodeWithText("PRACTICE THIS").assertIsDisplayed()
        compose.onNodeWithText("ASK WHAT TO DO").assertIsDisplayed()
        compose.onNodeWithText("아니면").assertDoesNotExist()
        assertFalse(false)
    }
}
