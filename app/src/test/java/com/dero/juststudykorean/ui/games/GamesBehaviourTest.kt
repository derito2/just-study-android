package com.dero.juststudykorean.ui.games

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.games.GameContentProvider
import com.dero.juststudykorean.data.games.GameRepository
import com.dero.juststudykorean.data.games.SessionState
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.FakeKoreanVoices
import com.dero.juststudykorean.ui.testing.idleUntil
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Games' screen (FASE_8_PLAN task 5) with the real engine on the golden fixture's (synthetic) notes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GamesBehaviourTest {
    @get:Rule(order = 0)
    val activity = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<android.app.Application>()
            val name = ComponentName(context.packageName, ComponentActivity::class.java.name)
            shadowOf(context.packageManager).apply {
                addActivityIfNotPresent(name)
                addIntentFilterForActivity(name, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val app = GoldenApp()
    private val repository = GameRepository(app.db, app.clock)
    private val vm = GamesViewModel(GameContentProvider(app.db, app.notes, DocumentFiles(app.context)), repository, app.clock, app.prefs, SavedStateHandle(), FakeKoreanVoices(app.context))

    @After
    fun close() = app.close()

    private fun show(talkBack: Boolean = false) = compose.setContent {
        CompositionLocalProvider(LocalFrozenMotion provides true, LocalTalkBack provides talkBack) {
            JustStudyTheme(darkTheme = false) { GamesScreen(onBack = {}, onLibrary = {}, vm = vm) }
        }
    }

    /** The main looper (viewModelScope) and the compose clock both move on until [done]. */
    private fun until(done: () -> Boolean) = idleUntil { compose.waitForIdle(); done() }

    private fun playing(): SessionState.Playing {
        until { vm.state.value is SessionState.Playing }
        return vm.state.value as SessionState.Playing
    }

    private fun win(round: SessionState.Playing) {
        vm.submit(RoundResult(Outcome.WON, 0, 2000, round.round.targets))
        until { vm.state.value is SessionState.Feedback }
    }

    @Test
    fun theFeedbackMovesOnByItself() {
        show()
        val first = playing()
        win(first)
        compose.mainClock.advanceTimeBy(1200)
        until { (vm.state.value as? SessionState.Playing)?.ref?.roundIndex == first.ref.roundIndex + 1 }
    }

    @Test
    fun backOpensThePause() {
        show()
        playing()
        Espresso.pressBack()
        until { vm.state.value is SessionState.Paused }
        compose.onNodeWithText("Resume", ignoreCase = true).assertExists()
    }

    @Test
    fun endSessionSavesAndShowsTheSummary() {
        show()
        win(playing())
        compose.mainClock.advanceTimeBy(1200)
        playing()
        vm.pause()
        compose.onNodeWithText("End session", ignoreCase = true).performScrollTo().performClick()
        until { vm.state.value is SessionState.Ended }
        compose.onNodeWithText("Nice session").assertExists()
        val ended = vm.state.value as SessionState.Ended
        assertEquals(1L, ended.summary.won)
        assertNotNull(runBlocking { repository.session(1)!!.endedAt })
    }

    @Test
    fun withTalkBackItWaitsForNext() {
        show(talkBack = true)
        val first = playing()
        win(first)
        compose.mainClock.advanceTimeBy(3000)
        assertTrue(vm.state.value is SessionState.Feedback)
        compose.onNodeWithText("Next", ignoreCase = true).performClick()
        until { (vm.state.value as? SessionState.Playing)?.ref?.roundIndex == first.ref.roundIndex + 1 }
    }
}
