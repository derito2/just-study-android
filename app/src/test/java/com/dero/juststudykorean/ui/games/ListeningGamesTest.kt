package com.dero.juststudykorean.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.RoundSpec.SoundDetective
import com.dero.juststudykorean.korean.games.SoundWord
import com.dero.juststudykorean.ui.games.GameFixtures.apple
import com.dero.juststudykorean.ui.games.GameFixtures.cat
import com.dero.juststudykorean.ui.games.GameFixtures.water
import com.dero.juststudykorean.ui.games.minigames.ListenGuessGame
import com.dero.juststudykorean.ui.games.minigames.SoundDetectiveGame
import com.dero.juststudykorean.ui.games.pieces.LocalKoreanVoice
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.testing.FakeKoreanVoice
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** FASE_8_PLAN task 9: the listening games with a fake voice (nothing sounds). Invented words. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ListeningGamesTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private val voice = FakeKoreanVoice()
    private var result: RoundResult? = null
    private val listen = RoundSpec.ListenGuess(1, listOf(apple, GameFixtures.milk, GameFixtures.school, cat, water, GameFixtures.book), listOf(water, apple, cat), showKorean = false)

    private fun show(content: @Composable (onResult: (RoundResult) -> Unit) -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalFrozenMotion provides true, LocalTalkBack provides false, LocalKoreanVoice provides voice) { JustStudyTheme { content { result = it } } }
    }

    private fun sounds() { compose.waitForIdle(); compose.mainClock.advanceTimeBy(4500); compose.waitForIdle() }
    private fun finished(): RoundResult { compose.waitForIdle(); compose.mainClock.advanceTimeBy(2500); compose.waitUntil(5_000) { result != null }; return result!! }

    @Test
    fun listenAndGuessHuntsEachCardByEar() {
        show { ListenGuessGame(listen, false, it, StageScope()) }
        sounds()
        compose.onNodeWithText("Cat").performClick() // wrong once
        compose.onNodeWithText("Water").performClick(); sounds()
        compose.onNodeWithText("Apple").performClick(); sounds()
        compose.onNodeWithText("Cat").performClick()
        val r = finished()
        assertEquals(Outcome.WON, r.outcome) // 2 of 3 on the first try
        assertEquals(listOf("물", "사과", "고양이"), voice.said)
    }

    @Test
    fun withTheVolumeAtZeroTheRoundWaits() {
        voice.muted = true
        show { ListenGuessGame(listen, false, it, StageScope()) }
        sounds()
        compose.onNodeWithText("Turn the volume up to play.").assertExists()
        assertEquals(emptyList<String>(), voice.said)
        voice.muted = false
        sounds()
        assertEquals(listOf("물"), voice.said)
    }

    @Test
    fun aPauseWhileItSoundsStartsTheRoundAgain() {
        var paused by mutableStateOf(false)
        val slow = FakeKoreanVoice(ms = 5_000)
        compose.setContent {
            CompositionLocalProvider(LocalFrozenMotion provides true, LocalTalkBack provides false, LocalKoreanVoice provides slow) {
                JustStudyTheme { ListenGuessGame(listen, paused, { result = it }, StageScope()) }
            }
        }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        assertEquals(listOf("물"), slow.said)
        paused = true; compose.waitForIdle()
        paused = false; compose.waitForIdle(); compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        assertEquals(listOf("물", "물"), slow.said) // the same round from the start
        assertNull(result)
    }

    @Test
    fun sameOrDifferent() {
        val spec = SoundDetective.SameOrDifferent(1, SoundWord("불", "Fire", null, true), SoundWord("풀", "Grass", null, true), "the first sound", 0.85f, 700)
        show { SoundDetectiveGame(spec, false, it, StageScope()) }
        sounds()
        assertEquals(listOf("불", "풀"), voice.said)
        compose.onNodeWithContentDescription("Different").performClick()
        compose.onNodeWithText("Different: only the first sound changes.").assertExists()
        assertEquals(Outcome.WON, finished().outcome)
    }

    @Test
    fun cameTwice() {
        val spec = SoundDetective.CameTwice(1, listOf(water, apple, cat), listOf(0, 2, 1, 2), 2, 0.85f, 700)
        show { SoundDetectiveGame(spec, false, it, StageScope()) }
        sounds()
        compose.onNodeWithContentDescription("고양이").performClick()
        assertEquals(Outcome.WON, finished().outcome)
    }

    @Test
    fun whatOrder() {
        val spec = SoundDetective.WhatOrder(1, listOf(cat, water, apple), listOf(apple, cat, water), 0.85f, 700)
        show { SoundDetectiveGame(spec, false, it, StageScope()) }
        sounds()
        for (w in listOf("고양이", "물", "사과")) compose.onNodeWithText(w).performClick()
        assertEquals(Outcome.WON, finished().outcome)
    }
}
