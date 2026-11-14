package com.dero.juststudykorean.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.GameFixtures.WORDS
import com.dero.juststudykorean.ui.games.minigames.MemoryFlipGame
import com.dero.juststudykorean.ui.games.minigames.SentenceBuilderGame
import com.dero.juststudykorean.ui.games.minigames.WordRecallGame
import com.dero.juststudykorean.ui.games.minigames.WordScrambleGame
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** FASE_8_PLAN task 6: one behaviour test per tile game, with invented content. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TileGamesTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private var result: RoundResult? = null

    private fun show(content: @Composable (onResult: (RoundResult) -> Unit) -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalFrozenMotion provides true, LocalTalkBack provides false) { JustStudyTheme { content { result = it } } }
    }

    private fun finished(): RoundResult { compose.waitForIdle(); compose.mainClock.advanceTimeBy(2000); compose.waitUntil(5_000) { result != null }; return result!! }

    @Test
    fun memoryFlipPairsEveryCard() {
        val items = WORDS.take(6)
        val cards = items.indices.flatMap { listOf(it to false, it to true) }.reversed()
        val spec = RoundSpec.MemoryFlip(1, items, cards, 3, 1000)
        show { MemoryFlipGame(spec, false, it, StageScope()) }
        for (item in items.indices) {
            val a = cards.indexOf(item to false)
            val b = cards.indexOf(item to true)
            compose.onNodeWithContentDescription("Card ${a + 1}, face down").performClick()
            compose.onNodeWithContentDescription("Card ${b + 1}, face down").performClick()
            compose.mainClock.advanceTimeBy(100)
        }
        val r = finished()
        assertEquals(Outcome.WON, r.outcome)
        assertEquals(0, r.mistakes)
    }

    @Test
    fun wordScramblePlacedInOrderWins() {
        val word = GameFixtures.library // 도 서 관
        val spec = RoundSpec.WordScramble(1, word, listOf("관", "도", "서"), null)
        show { WordScrambleGame(spec, false, it, StageScope()) }
        for (b in word.blocks) compose.onAllNodesWithText(b).onLast().performClick()
        assertEquals(Outcome.WON, finished().outcome)
    }

    @Test
    fun sentenceBuilderInTheOrderOfTheNotesWins() {
        val item = GameFixtures.sentence("저는 학교에 가요.", "I go to school")
        val spec = RoundSpec.SentenceBuilder(1, item, listOf("가요", "저는", "학교에"), listOf("저는", "학교에", "가요"), ".", emptyList())
        show { SentenceBuilderGame(spec, false, it, StageScope()) }
        for (w in spec.answer) compose.onAllNodesWithText(w).onLast().performClick()
        compose.onNodeWithText("Check", ignoreCase = true).performClick()
        assertEquals(Outcome.WON, finished().outcome)
    }

    @Test
    fun wordRecallTappedInOrderWins() {
        val sequence = listOf(GameFixtures.school, GameFixtures.water, GameFixtures.friend)
        val spec = RoundSpec.WordRecall(1, sequence, sequence.reversed())
        show { WordRecallGame(spec, false, it, StageScope()) }
        compose.mainClock.advanceTimeBy(3 * 700L + 3 * 900L + 500)
        for (w in sequence) compose.onAllNodesWithText(w.korean).onLast().performClick()
        val r = finished()
        assertEquals(Outcome.WON, r.outcome)
        assertEquals(0, r.mistakes)
    }
}
