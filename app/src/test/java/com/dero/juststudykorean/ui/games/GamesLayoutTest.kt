package com.dero.juststudykorean.ui.games

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.layout.boundsInRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.games.SessionState
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.GameFixtures.apple
import com.dero.juststudykorean.ui.games.GameFixtures.cat
import com.dero.juststudykorean.ui.games.GameFixtures.milk
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Games' board: a game shorter than the space under its instruction sits in the middle of it, not glued to the top. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class GamesLayoutTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun aShortGameIsCentredInTheBoard() {
        // Hangman is short; Word Sort used to be the example but now fills the free height on purpose (G-2).
        val sort = RoundSpec.Hangman(1, GameFixtures.library, false, listOf("도", "서", "관"), emptyList())
        compose.setContent {
            CompositionLocalProvider(LocalFrozenMotion provides true) {
                JustStudyTheme { GamesScreenshotTest.Games(SessionState.Playing(GamesScreenshotTest.REF, sort)) }
            }
        }
        compose.waitForIdle()
        val area = compose.onNodeWithTag("game-area").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("game-content").fetchSemanticsNode().boundsInRoot
        assertTrue("the game should be shorter than its area for this to mean anything", content.height < area.height - 120f)
        assertEquals("space above and below the game should match", content.top - area.top, area.bottom - content.bottom, 6f)
    }
}
