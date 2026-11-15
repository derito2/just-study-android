package com.dero.juststudykorean.ui.games

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.games.SessionSummary
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** design/games-characters.md 5.5: an empty session doesn't pretend it was a clean one. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SummaryTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun show(summary: SessionSummary) = compose.setContent {
        JustStudyTheme { SessionSummaryCard(summary, SessionCounts(rounds = summary.rounds.toInt()), {}, {}) }
    }

    @Test
    fun zeroRoundsSaysSoAndOffersPlay() {
        show(SessionSummary(0, 0, 0, 0))
        compose.onNodeWithText("See you next time").assertExists()
        compose.onNodeWithText("No rounds this time.").assertExists()
        compose.onNodeWithText("Nothing to review. Clean session.").assertDoesNotExist()
        compose.onNodeWithText("ROUNDS", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Play", ignoreCase = true).assertExists()
        compose.onNodeWithText("Play again", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun aRealSessionWithoutMistakesIsClean() {
        show(SessionSummary(4, 4, 4, 120))
        compose.onNodeWithText("Nice session").assertExists()
        compose.onNodeWithText("Nothing to review. Clean session.").assertExists()
        compose.onNodeWithText("Play again", ignoreCase = true).assertExists()
    }
}
