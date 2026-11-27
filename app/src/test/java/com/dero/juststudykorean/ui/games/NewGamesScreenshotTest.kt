package com.dero.juststudykorean.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.games.SessionState
import com.dero.juststudykorean.korean.games.CrossEntry
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.wordleJamo
import com.dero.juststudykorean.ui.games.GamesScreenshotTest.Companion.Games
import com.dero.juststudykorean.ui.games.GamesScreenshotTest.Companion.REF
import com.dero.juststudykorean.ui.games.GamesScreenshotTest.Companion.snap
import com.dero.juststudykorean.ui.games.minigames.CrosswordGame
import com.dero.juststudykorean.ui.games.minigames.HangulWordleGame
import com.dero.juststudykorean.ui.games.stage.StageScope
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/beta.md 7 (FASE_11_PLAN PR 10): Hangul Wordle and Crossword, in the scene and mid-round, light and dark. Invented content. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class NewGamesScreenshotTest {
    private val school = GameFixtures.school
    private val wordle = RoundSpec.HangulWordle(2, school, wordleJamo("학교", 5)!!, 6, null)
    private val cross = RoundSpec.Crossword(
        2, 3, 3,
        listOf(
            CrossEntry(school, 0, 0, true, 1), CrossEntry(GameFixtures.word("교실", "Classroom"), 0, 1, false, 2),
            CrossEntry(GameFixtures.word("실수", "Mistake"), 1, 1, true, 3), CrossEntry(GameFixtures.word("수박", "Watermelon"), 1, 2, false, 4),
        ),
        emptySet(),
    )

    @Composable
    private fun Board(content: @Composable () -> Unit) = Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) { content() }

    @Test fun wordle() = snap("games_hangul_wordle") { Games(SessionState.Playing(REF, wordle)) }
    @Test fun wordleDark() = snap("games_hangul_wordle_dark", dark = true) { Games(SessionState.Playing(REF, wordle)) }
    @Test fun wordleGuesses() = snap("games_hangul_wordle_guesses") { Board { HangulWordleGame(wordle.copy(level = 1, firstSyllable = "학"), false, {}, StageScope(), initial = listOf("가족", "학과")) } }
    @Test fun wordleGuessesDark() = snap("games_hangul_wordle_guesses_dark", dark = true) { Board { HangulWordleGame(wordle, false, {}, StageScope(), initial = listOf("가족", "학과")) } }
    @Test fun crossword() = snap("games_crossword") { Games(SessionState.Playing(REF, cross)) }
    @Test fun crosswordDark() = snap("games_crossword_dark", dark = true) { Games(SessionState.Playing(REF, cross)) }
    @Test fun crosswordFilled() = snap("games_crossword_filled") { Board { CrosswordGame(cross, false, {}, StageScope(), initial = mapOf(0 to "학", 1 to "교", 4 to "실")) } }
    @Test fun crosswordFilledDark() = snap("games_crossword_filled_dark", dark = true) { Board { CrosswordGame(cross, false, {}, StageScope(), initial = mapOf(0 to "학", 1 to "교", 4 to "실")) } }
}
