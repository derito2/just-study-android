package com.dero.juststudykorean.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.games.SessionRef
import com.dero.juststudykorean.data.games.SessionState
import com.dero.juststudykorean.data.games.SessionSummary
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.GameFixtures.WORDS
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/games.md 13: the scene, the board and the states, with frozen motion, light and dark. Invented content. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class GamesScreenshotTest {
    companion object {
        val REF = SessionRef(1, 42, 3)
        val COUNTS = SessionCounts(rounds = 9, won = 7, streak = 4, bestStreak = 5, seconds = 412, failed = listOf(GameFixtures.library, GameFixtures.cat))

        fun snap(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) =
            captureRoboImage("src/test/screenshots/$name.png") {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalFrozenMotion provides true, LocalTalkBack provides false) {
                    JustStudyTheme(darkTheme = dark) { content() }
                }
            }

        @Composable
        fun Games(state: SessionState, counts: SessionCounts = COUNTS) = GamesContent(
            state, counts, true, {}, {}, {}, {}, {}, {}, {}, {}, {}, openingAtStart = false, pauseSheetInline = true,
        )
    }

    private val match = RoundSpec.WordMatch(1, WORDS.take(5), listOf(2, 0, 4, 1, 3), listOf(1, 3, 0, 4, 2))

    @Test fun stage() = snap("games_stage") { Games(SessionState.Preparing) }
    @Test fun stageDark() = snap("games_stage_dark", dark = true) { Games(SessionState.Preparing) }
    @Test fun wordMatch() = snap("games_word_match") { Games(SessionState.Playing(REF, match)) }
    @Test fun wordMatchDark() = snap("games_word_match_dark", dark = true) { Games(SessionState.Playing(REF, match)) }
    @Test fun wordMatch130() = snap("games_word_match_130", fontScale = 1.3f) { Games(SessionState.Playing(REF, match.copy(items = WORDS.take(4), koreanOrder = listOf(2, 0, 3, 1), meaningOrder = listOf(1, 3, 0, 2)))) }
    @Test fun correct() = snap("games_ribbon_correct") { Games(SessionState.Feedback(REF, match, RoundResult(Outcome.WON, 0, 9000, match.targets))) }
    @Test fun correctDark() = snap("games_ribbon_correct_dark", dark = true) { Games(SessionState.Feedback(REF, match, RoundResult(Outcome.WON, 0, 9000, match.targets))) }
    @Test fun notQuite() = snap("games_ribbon_not_quite") {
        val hangman = RoundSpec.Hangman(1, GameFixtures.library, false, GameFixtures.library.blocks, listOf("도", "서", "관", "도", "서"))
        Games(SessionState.Feedback(REF, hangman, RoundResult(Outcome.LOST, 3, 9000, hangman.targets, hangman.targets)), COUNTS.copy(streak = 0))
    }
    @Test fun notQuiteDark() = snap("games_ribbon_not_quite_dark", dark = true) {
        val hangman = RoundSpec.Hangman(1, GameFixtures.library, false, GameFixtures.library.blocks, listOf("도", "서", "관"))
        Games(SessionState.Feedback(REF, hangman, RoundResult(Outcome.LOST, 3, 9000, hangman.targets, hangman.targets)), COUNTS.copy(streak = 0))
    }
    @Test fun pause() = snap("games_pause") { Games(SessionState.Paused(REF, match)) }
    @Test fun pauseDark() = snap("games_pause_dark", dark = true) { Games(SessionState.Paused(REF, match)) }
    @Test fun summary() = snap("games_summary") { Games(SessionState.Ended(SessionSummary(9, 7, 5, 412))) }
    @Test fun summaryDark() = snap("games_summary_dark", dark = true) { Games(SessionState.Ended(SessionSummary(9, 7, 5, 412))) }
    @Test fun empty() = snap("games_empty") { Games(SessionState.Empty, SessionCounts()) }
    @Test fun emptyDark() = snap("games_empty_dark", dark = true) { Games(SessionState.Empty, SessionCounts()) }

    // Task 6: one capture per tile game, in its initial state.
    @Test fun memoryFlip() = snap("games_memory_flip") {
        val items = WORDS.take(6)
        Games(SessionState.Playing(REF, RoundSpec.MemoryFlip(1, items, items.indices.flatMap { listOf(it to false, it to true) }.shuffled(kotlin.random.Random(3)), 3, 1000)))
    }
    @Test fun wordScramble() = snap("games_word_scramble") { Games(SessionState.Playing(REF, RoundSpec.WordScramble(1, GameFixtures.library, listOf("관", "도", "서"), null))) }
    @Test fun sentenceBuilder() = snap("games_sentence_builder") {
        val item = GameFixtures.sentence("저는 학교에 가요.", "I go to school")
        Games(SessionState.Playing(REF, RoundSpec.SentenceBuilder(1, item, listOf("가요", "저는", "학교에"), listOf("저는", "학교에", "가요"), ".", emptyList())))
    }
    @Test fun wordRecall() = snap("games_word_recall") {
        val sequence = listOf(GameFixtures.school, GameFixtures.water, GameFixtures.friend)
        Games(SessionState.Playing(REF, RoundSpec.WordRecall(1, sequence, sequence.reversed())))
    }

    // Task 7: the Hangul games (own keyboard, no system keyboard).
    @Test fun hangman() = snap("games_hangman") {
        val word = GameFixtures.library
        Games(SessionState.Playing(REF, RoundSpec.Hangman(1, word, false, word.blocks, listOf("도", "고", "서", "사", "관", "소", "노", "보", "간", "선"))))
    }
    @Test fun hangmanJamo() = snap("games_hangman_jamo") {
        val word = GameFixtures.water
        Games(SessionState.Playing(REF, RoundSpec.Hangman(2, word, true, listOf("ㅁ", "ㅜ", "ㄹ"), emptyList())))
    }
    @Test fun hangulBuilder() = snap("games_hangul_builder") {
        Games(SessionState.Playing(REF, RoundSpec.HangulBuilder(1, GameFixtures.school, 1, listOf('ㄱ', 'ㅛ'), listOf('ㅛ', 'ㅋ', 'ㄱ', 'ㅠ', 'ㄲ', 'ㅕ'))))
    }
    @Test fun hangulTyping() = snap("games_hangul_typing") {
        Games(SessionState.Playing(REF, RoundSpec.HangulTyping(1, GameFixtures.water, RoundSpec.TypingMode.COPY)))
    }

    // Task 8: the board games (drag or taps).
    @Test fun wordSort() = snap("games_word_sort") {
        val tiles = listOf(GameFixtures.apple to 0, GameFixtures.cat to 1, GameFixtures.milk to 0, GameFixtures.word("개", "Dog") to 1, GameFixtures.word("빵", "Bread") to 0, GameFixtures.word("새", "Bird") to 1)
        Games(SessionState.Playing(REF, RoundSpec.WordSort(1, listOf("food", "animals"), tiles, showMeaning = true)))
    }
    @Test fun wordPath() = snap("games_word_path") {
        Games(SessionState.Playing(REF, RoundSpec.WordPath(2, GameFixtures.library, 4, listOf("도", "서", "가", "나", "다", "고", "관", "라", "마", "바", "사", "아", "자", "차", "카", "타"), listOf(0, 1, 6))))
    }
    @Test fun grammarFixer() = snap("games_grammar_fixer") {
        val item = GameFixtures.sentence("저는 학교를 가요", "I go to school")
        Games(SessionState.Playing(REF, RoundSpec.GrammarFixer(1, item, listOf("저는", "학교을", "가요"), ".", 1, "학교", "를", listOf("은", "를"), "After a vowel: 를. After a consonant: 을.")))
    }
}
