package com.dero.juststudykorean.ui.games

import android.Manifest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.speech.SpeechEvent
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.RunnerQuestion
import com.dero.juststudykorean.korean.grammar.Ending
import com.dero.juststudykorean.ui.games.minigames.ConjugationRunnerGame
import com.dero.juststudykorean.ui.games.minigames.GamesSpeech
import com.dero.juststudykorean.ui.games.minigames.LocalGamesSpeech
import com.dero.juststudykorean.ui.games.minigames.SpeakItGame
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.testing.FakeKoreanSpeech
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** FASE_11_PLAN PR 11 (F7): Conjugation Runner wins with 8 of 10 and a missed form counts; Speak It is never graded (D9-6, D11-8). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class SpeakAndRunnerTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private val eat = GameFixtures.word("먹다", "To eat")
    private val go = GameFixtures.word("가다", "To go")
    /** 10 forms; the right one is always "R{i}" so the test can tap it (the board shows what the factory gives). */
    private val runner = RoundSpec.ConjugationRunner(1, List(10) { i -> RunnerQuestion(if (i % 2 == 0) eat else go, Ending.PoliteAeo, listOf("W$i", "R$i", "X$i"), "R$i") }, 6000)

    private fun play(spec: RoundSpec.ConjugationRunner, onResult: (RoundResult) -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalFrozenMotion provides true) { JustStudyTheme { ConjugationRunnerGame(spec, false, onResult, StageScope()) } }
    }

    private fun next() = repeat(6) { compose.mainClock.advanceTimeBy(300); compose.waitForIdle() }

    @Test
    fun eightOfTenWinsAndTheWrongOnesAreFailed() {
        compose.mainClock.autoAdvance = false
        var result: RoundResult? = null
        play(runner) { result = it }
        for (i in 0 until 10) { compose.onNodeWithText(if (i == 3 || i == 6) "W$i" else "R$i").performClick(); next() }
        assertEquals(Outcome.WON, result?.outcome)
        assertEquals(2, result?.mistakes)
        assertEquals(listOf(go.key, eat.key), result?.failedKeys)
    }

    @Test
    fun withRemoveAnimationsAFormLeftFiveSecondsIsMissed() {
        compose.mainClock.autoAdvance = false
        var result: RoundResult? = null
        play(runner) { result = it }
        compose.onNodeWithText("5 s").assertExists() // no fall: a counter
        for (i in 0 until 7) { compose.onNodeWithText("R$i").performClick(); next() }
        repeat(3) { compose.mainClock.advanceTimeBy(5_000); compose.waitForIdle(); next() } // 3 forms run out
        assertEquals(Outcome.LOST, result?.outcome)
        assertEquals(3, result?.mistakes)
    }

    private val apple = GameFixtures.word("사과", "Apple")

    private fun speak(speech: FakeKoreanSpeech, onResult: (RoundResult) -> Unit) {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.setContent {
            CompositionLocalProvider(LocalFrozenMotion provides true, LocalGamesSpeech provides GamesSpeech(speech)) {
                JustStudyTheme { SpeakItGame(RoundSpec.SpeakIt(1, apple), false, onResult, StageScope()) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun saidRightIsCelebratedButNeutral() {
        var result: RoundResult? = null
        speak(FakeKoreanSpeech().apply { script(SpeechEvent.Listening, SpeechEvent.Final(listOf("사과"), null)) }) { result = it }
        compose.onNodeWithContentDescription("Practice out loud").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(Outcome.NEUTRAL, result?.outcome) // D11-8: never graded
        assertEquals(emptyList<String>(), result?.failedKeys)
        assertEquals("You said it!", neutralText(RoundSpec.SpeakIt(1, apple), result))
    }

    @Test
    fun heardSomethingElseShowsItAndSkipIsNeutral() {
        var result: RoundResult? = null
        speak(FakeKoreanSpeech().apply { script(SpeechEvent.Listening, SpeechEvent.Final(listOf("사자"), null)) }) { result = it }
        compose.onNodeWithContentDescription("Practice out loud").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onAllNodesWithText("We heard").assertCountEquals(1)
        compose.onAllNodesWithText("Not quite", substring = true).assertCountEquals(0) // D9-6
        assertNull(result)
        compose.onNodeWithText("SKIP", ignoreCase = true).performClick()
        compose.waitForIdle()
        assertEquals(Outcome.NEUTRAL, result?.outcome)
        assertEquals(listOf(apple.key), result?.failedKeys)
        assertEquals("Here it is:", neutralText(RoundSpec.SpeakIt(1, apple), result))
        assertNull(tipOf(RoundSpec.SpeakIt(1, apple)))
        assertNull(tipOf(runner))
    }
}
