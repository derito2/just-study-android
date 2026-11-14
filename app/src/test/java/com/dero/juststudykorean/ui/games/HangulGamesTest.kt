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
import com.dero.juststudykorean.korean.hangul.jamoOf
import com.dero.juststudykorean.ui.games.minigames.HangmanGame
import com.dero.juststudykorean.ui.games.minigames.HangulBuilderGame
import com.dero.juststudykorean.ui.games.minigames.HangulTypingGame
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** FASE_8_PLAN task 7: the Hangul games with invented words; no game has a TextField (the system keyboard). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HangulGamesTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private var result: RoundResult? = null

    private fun show(content: @Composable (onResult: (RoundResult) -> Unit) -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalFrozenMotion provides true, LocalTalkBack provides false) { JustStudyTheme { content { result = it } } }
    }

    private fun finished(): RoundResult { compose.waitForIdle(); compose.mainClock.advanceTimeBy(2000); compose.waitUntil(5_000) { result != null }; return result!! }

    @Test
    fun hangmanBySyllablesWinsAndThreeMissesLose() {
        val word = GameFixtures.library // 도서관
        val spec = RoundSpec.Hangman(1, word, false, word.blocks, listOf("도", "서", "관", "고", "사"))
        show { HangmanGame(spec, false, it, StageScope()) }
        compose.onAllNodesWithText("고").onLast().performClick() // one miss
        for (b in word.blocks) compose.onAllNodesWithText(b).onLast().performClick()
        val r = finished()
        assertEquals(Outcome.WON, r.outcome)
        assertEquals(1, r.mistakes)
    }

    @Test
    fun hangmanInJamoRevealsEveryPlace() {
        val word = GameFixtures.water // 물: ㅁ ㅜ ㄹ
        val spec = RoundSpec.Hangman(2, word, true, jamoOf('물').map { it.toString() }, emptyList())
        show { HangmanGame(spec, false, it, StageScope()) }
        for (name in listOf("미음", "우", "리을")) compose.onNodeWithContentDescription(name).performClick()
        assertEquals(Outcome.WON, finished().outcome)
    }

    @Test
    fun hangulBuilderBuildsTheMissingBlock() {
        val word = GameFixtures.school // 학교: build 교 = ㄱ ㅛ
        val spec = RoundSpec.HangulBuilder(1, word, 1, listOf('ㄱ', 'ㅛ'), listOf('ㅛ', 'ㅋ', 'ㄱ', 'ㅠ', 'ㄲ', 'ㅕ'))
        show { HangulBuilderGame(spec, false, it, StageScope()) }
        compose.onAllNodesWithText("ㄱ").onLast().performClick()
        compose.onAllNodesWithText("ㅛ").onLast().performClick()
        assertEquals(Outcome.WON, finished().outcome)
    }

    @Test
    fun hangulTypingWithTheOwnKeyboard() {
        val spec = RoundSpec.HangulTyping(1, GameFixtures.water, RoundSpec.TypingMode.COPY)
        show { HangulTypingGame(spec, false, true, it, StageScope()) }
        for (name in listOf("미음", "우", "리을")) compose.onNodeWithContentDescription(name).performClick()
        compose.onNodeWithText("Check", ignoreCase = true).performClick()
        assertEquals(Outcome.WON, finished().outcome)
    }

    @Test
    fun noGameOpensTheSystemKeyboard() {
        val offenders = File("src/main/java/com/dero/juststudykorean/ui/games").walkTopDown()
            .filter { it.extension == "kt" && Regex("\\b(Basic)?TextField\\b|OutlinedTextField|JsTextField").containsMatchIn(it.readText()) }.map { it.path }.toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
