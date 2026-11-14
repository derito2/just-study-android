package com.dero.juststudykorean.ui.games

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.GameFixtures.WORDS
import com.dero.juststudykorean.ui.games.minigames.WordMatchGame
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Word Match by taps: each Korean with its meaning; a wrong pair counts against both words. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WordMatchGameTest {
    @get:Rule(order = 0) val activity = com.dero.juststudykorean.ui.testing.RegisterComponentActivity()

    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun matchingEveryPairWithOneMistakeWins() {
        val items = WORDS.take(5)
        val spec = RoundSpec.WordMatch(1, items, listOf(4, 3, 2, 1, 0), listOf(0, 2, 4, 1, 3))
        var result: RoundResult? = null
        compose.setContent { CompositionLocalProvider(LocalFrozenMotion provides true) { JustStudyTheme { WordMatchGame(spec, false, { result = it }, StageScope()) } } }
        compose.onNodeWithText(items[0].korean).performClick()
        compose.onNodeWithText(items[1].meaning).performClick() // wrong
        compose.mainClock.advanceTimeBy(500)
        for (item in items) {
            compose.onNodeWithText(item.korean).performClick()
            compose.onNodeWithText(item.meaning).performClick()
            compose.mainClock.advanceTimeBy(500)
        }
        compose.waitUntil(5_000) { result != null }
        assertEquals(Outcome.WON, result!!.outcome)
        assertEquals(1, result!!.mistakes)
        assertEquals(setOf(items[0].key, items[1].key), result!!.failedKeys.toSet())
        assertEquals(spec.targets, result!!.itemKeys)
    }
}
