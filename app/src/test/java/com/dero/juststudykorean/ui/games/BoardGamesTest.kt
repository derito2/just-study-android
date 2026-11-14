package com.dero.juststudykorean.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.games.GameBank
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.GameFixtures.apple
import com.dero.juststudykorean.ui.games.GameFixtures.cat
import com.dero.juststudykorean.ui.games.GameFixtures.library
import com.dero.juststudykorean.ui.games.GameFixtures.milk
import com.dero.juststudykorean.ui.games.minigames.GrammarFixerGame
import com.dero.juststudykorean.ui.games.minigames.WordPathGame
import com.dero.juststudykorean.ui.games.minigames.WordSortGame
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** FASE_8_PLAN task 8: Word Sort, Word Path and Grammar Fixer, each by taps and (where it drags) by dragging. Invented words. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BoardGamesTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private var result: RoundResult? = null
    private val sort = RoundSpec.WordSort(1, listOf("food", "animals"), listOf(apple to 0, cat to 1, milk to 0), showMeaning = true)
    private val grid = listOf("도", "서", "관", "가", "나", "다", "라", "마", "바")
    private val path = RoundSpec.WordPath(1, library, 3, grid, listOf(0, 1, 2))

    private fun show(bank: GameBank = GameBank(emptyList(), emptyList()), content: @Composable (onResult: (RoundResult) -> Unit) -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalFrozenMotion provides true, LocalTalkBack provides false, LocalGameBank provides bank) { JustStudyTheme { content { result = it } } }
    }

    private fun finished(): RoundResult { compose.waitForIdle(); compose.mainClock.advanceTimeBy(2500); compose.waitUntil(5_000) { result != null }; return result!! }

    private fun bin(name: String) = compose.onNodeWithContentDescription("$name,", substring = true)

    @Test
    fun wordSortByTaps() {
        show { WordSortGame(sort, false, it, StageScope()) }
        compose.onNodeWithText("사과").performClick(); bin("animals").performClick() // one mistake
        compose.onNodeWithText("사과").performClick(); bin("food").performClick()
        compose.onNodeWithText("고양이").performClick(); bin("animals").performClick()
        compose.onNodeWithText("우유").performClick(); bin("food").performClick()
        val r = finished()
        assertEquals(Outcome.WON, r.outcome)
        assertEquals(1, r.mistakes)
        assertEquals(listOf(apple.key), r.failedKeys)
    }

    @Test
    fun wordSortByDragging() {
        show { WordSortGame(sort.copy(tiles = listOf(cat to 1)), false, it, StageScope()) }
        val to = bin("animals").fetchSemanticsNode().boundsInRoot.center
        val from = compose.onNodeWithText("고양이").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("고양이").performTouchInput {
            down(center)
            val step = (to - (from.topLeft + center)) / 10f
            repeat(10) { moveBy(step) }
            up()
        }
        assertEquals(Outcome.WON, finished().outcome)
    }

    @Test
    fun wordPathByTapsAndTheLastCellAgain() {
        show { WordPathGame(path, false, it, StageScope()) }
        for (cell in listOf("도", "서", "관", "관")) compose.onNodeWithContentDescription("$cell, row", substring = true).performClick()
        val r = finished()
        assertEquals(Outcome.WON, r.outcome)
        assertEquals(0, r.mistakes)
    }

    @Test
    fun wordPathByDragging() {
        show { WordPathGame(path, false, it, StageScope()) }
        val a = compose.onNodeWithContentDescription("도, row", substring = true).fetchSemanticsNode().boundsInRoot
        val c = compose.onNodeWithContentDescription("관, row", substring = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("도, row", substring = true).performTouchInput {
            down(center)
            val step = Offset((c.center.x - a.center.x) / 12f, 0f)
            repeat(12) { moveBy(step) }
            up()
        }
        assertEquals(Outcome.WON, finished().outcome)
    }

    @Test
    fun anotherWordOfYourNotesCostsNoTry() {
        val other = GameFixtures.word("가나", "Ganache")
        show(GameBank(listOf(library, other), emptyList())) { WordPathGame(path, false, it, StageScope()) }
        for (cell in listOf("가", "나", "나")) compose.onNodeWithContentDescription("$cell, row", substring = true).performClick()
        compose.onNodeWithText("Ganache", substring = true).assertExists()
        compose.onNodeWithContentDescription("3 tries left").assertExists()
    }

    @Test
    fun grammarFixerFoundOnTheSecondTryAndFixed() {
        val item = GameFixtures.sentence("저는 학교를 가요", "I go to school")
        val spec = RoundSpec.GrammarFixer(1, item, listOf("저는", "학교을", "가요"), ".", 1, "학교", "를", listOf("은", "를"), "After a vowel: 를. After a consonant: 을.")
        show { GrammarFixerGame(spec, false, it, StageScope()) }
        compose.onNodeWithText("저는").performClick() // one miss
        compose.onNodeWithText("학교을").performClick()
        compose.onNodeWithText("를").performClick()
        val r = finished()
        assertEquals(Outcome.WON, r.outcome)
        assertEquals(1, r.mistakes)
    }
}
