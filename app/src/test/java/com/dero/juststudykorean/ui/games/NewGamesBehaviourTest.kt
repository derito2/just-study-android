package com.dero.juststudykorean.ui.games

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.games.CrossEntry
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.wordleJamo
import com.dero.juststudykorean.ui.games.minigames.CrosswordGame
import com.dero.juststudykorean.ui.games.minigames.HangulWordleGame
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** PR 10, owner's changes: a full Wordle row takes no more jamo; Crossword's keys open on a word and close with Done. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class NewGamesBehaviourTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun key(name: String) = compose.onAllNodesWithContentDescription(name).onLast().performClick()

    @Test
    fun aFullWordleRowTakesNoMoreJamo() {
        val spec = RoundSpec.HangulWordle(2, GameFixtures.school, wordleJamo("학교", 5)!!, 6, null)
        compose.setContent { CompositionLocalProvider(LocalFrozenMotion provides true) { JustStudyTheme { HangulWordleGame(spec, false, {}, StageScope()) } } }
        listOf("기역", "아", "기역", "니은", "아", "미음", "우").forEach(::key) // 각나 fills the 5 cells; ㅁ and ㅜ do nothing
        compose.onNodeWithText("CHECK", substring = true, ignoreCase = true).performClick()
        compose.waitForIdle()
        val marked = compose.onAllNodesWithContentDescription("in place", substring = true).fetchSemanticsNodes().size +
            compose.onAllNodesWithContentDescription("elsewhere", substring = true).fetchSemanticsNodes().size +
            compose.onAllNodesWithContentDescription("not in the word", substring = true).fetchSemanticsNodes().size
        assertTrue("각나 was checked as a guess of 5 jamo ($marked marked)", marked == 5)
    }

    @Test
    fun crosswordKeysOpenOnAWordAndCloseWithDone() {
        val spec = RoundSpec.Crossword(2, 2, 2, listOf(CrossEntry(GameFixtures.school, 0, 0, true, 1), CrossEntry(GameFixtures.word("교실", "Classroom"), 0, 1, false, 2)), emptySet())
        compose.setContent { CompositionLocalProvider(LocalFrozenMotion provides true) { JustStudyTheme { CrosswordGame(spec, false, {}, StageScope()) } } }
        compose.onAllNodesWithContentDescription("Delete").assertCountEquals(0) // closed: the grid and every clue
        compose.onAllNodesWithText("Across").assertCountEquals(1)
        compose.onNodeWithText("1. School").performClick()
        compose.onAllNodesWithContentDescription("Delete").assertCountEquals(1) // open: the keys
        compose.onAllNodesWithText("Across").assertCountEquals(0) // only the chosen clue above them
        compose.onNodeWithText("DONE", ignoreCase = true).performClick()
        compose.onAllNodesWithContentDescription("Delete").assertCountEquals(0)
    }
}
