package com.dero.juststudykorean.ui.games

import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.games.SessionRef
import com.dero.juststudykorean.data.games.SessionState
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.GameFixtures.WORDS
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * FASE_8_PLAN task 11: with Android's "Remove animations" (animator scale 0) and no test freezing, Games skips its
 * opening and runs no endless animation (the screen goes idle), for every game. The phone's setting is never touched.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RemoveAnimationsTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

    @Before fun off() { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) }
    @After fun on() { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }

    @Test
    fun noOpeningAndTheScreenGoesIdle() {
        val rounds = listOf<RoundSpec>(
            RoundSpec.WordMatch(1, WORDS.take(4), listOf(2, 0, 3, 1), listOf(1, 3, 0, 2)),
            RoundSpec.Hangman(1, GameFixtures.library, false, GameFixtures.library.blocks, listOf("도", "서", "관", "고", "사")),
            RoundSpec.ListenGuess(1, WORDS.take(6), WORDS.take(3), showKorean = false),
            RoundSpec.HangulTyping(2, GameFixtures.water, RoundSpec.TypingMode.FROM_MEANING),
        )
        var round by androidx.compose.runtime.mutableStateOf(rounds.first())
        compose.setContent {
            CompositionLocalProvider(LocalTalkBack provides false) {
                JustStudyTheme { GamesContent(SessionState.Playing(SessionRef(1, 1, 0), round), SessionCounts(), true, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
            }
        }
        for (r in rounds) {
            round = r
            compose.waitForIdle() // an endless animation would never let it go idle
            compose.onNodeWithText(GAME_TEXTS.getValue(r.game).name).assertExists()
        }
    }
}
