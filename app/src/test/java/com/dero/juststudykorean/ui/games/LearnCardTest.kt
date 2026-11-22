package com.dero.juststudykorean.ui.games

import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.ItemStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** design/games-characters.md 6: which word the card teaches, when the voice sounds, and how long the card stays. */
class LearnCardTest {
    private fun result(outcome: Outcome, failed: List<String> = emptyList()) = RoundResult(outcome, failed.size, 1000, emptyList(), failed)
    private val match = RoundSpec.WordMatch(1, listOf(GameFixtures.apple, GameFixtures.milk, GameFixtures.school), listOf(0, 1, 2), listOf(0, 1, 2))

    @Test
    fun aSingleWordRoundTeachesItsWord() {
        val hangman = RoundSpec.Hangman(1, GameFixtures.library, false, listOf("도", "서", "관"), emptyList())
        assertEquals(Learn("도서관", "Library", key = GameFixtures.library.key), learnOf(hangman, result(Outcome.WON)))
        assertEquals(Learn("도서관", "Library", key = GameFixtures.library.key), learnOf(hangman, result(Outcome.LOST)))
    }

    @Test
    fun aLostMultiWordRoundTeachesTheFirstWordYouMissed() {
        val lost = result(Outcome.LOST, failed = listOf(GameFixtures.school.key, GameFixtures.milk.key))
        assertEquals("학교", learnOf(match, lost)!!.korean)
        // Nothing recorded as failed (e.g. time ran out): the first word.
        assertEquals("사과", learnOf(match, result(Outcome.LOST))!!.korean)
    }

    @Test
    fun aWonMultiWordRoundTeachesTheFirstUntilTheProgressIsRead() {
        assertEquals("사과", learnOf(match, result(Outcome.WON))!!.korean)
    }

    @Test
    fun aSentenceRoundTeachesTheSentenceAndItsTranslation() {
        val s = GameFixtures.sentence("학교에 가요.", "I go to school.")
        val spec = RoundSpec.SentenceBuilder(1, s, listOf("학교에", "가요."), listOf("학교에", "가요."), "sign", emptyList())
        assertEquals(Learn("학교에 가요.", "I go to school.", sentence = true, key = s.key), learnOf(spec, result(Outcome.NEUTRAL)))
    }

    @Test
    fun aRoundWithNoWordsTeachesNothing() {
        assertNull(learnOf(RoundSpec.WordMatch(1, emptyList(), emptyList(), emptyList()), result(Outcome.WON)))
    }

    @Test
    fun theVoiceStaysSilentWhenYouWinAListeningGame() {
        val listen = RoundSpec.ListenGuess(1, listOf(GameFixtures.apple, GameFixtures.milk), listOf(GameFixtures.apple), false)
        assertTrue(isListening(listen))
        assertFalse(isListening(match))
    }

    @Test
    fun theCardStaysAsLongAsTheSheetSays() {
        assertEquals(900L, cardMs(won = true, voiceEndMs = null)) // no voice: 900 ms
        assertEquals(900L, cardMs(won = true, voiceEndMs = 400)) // the voice ends early: still 900
        assertEquals(1500L, cardMs(won = true, voiceEndMs = 1200)) // 300 ms after the voice
        assertEquals(2500L, cardMs(won = true, voiceEndMs = 5000)) // capped
        assertEquals(2200L, cardMs(won = false, voiceEndMs = null))
        assertEquals(2200L, cardMs(won = false, voiceEndMs = 1000))
        assertEquals(3000L, cardMs(won = false, voiceEndMs = 2400)) // 600 ms after the voice
        assertEquals(4000L, cardMs(won = false, voiceEndMs = 9000)) // capped
    }

    private fun stats(correct: Long, streak: Long = 0) = ItemStats(correct, correct, 0, streak, 0, null)

    @Test
    fun aWonMultiWordRoundTeachesTheWordWithTheFewestHits() {
        val progress = mapOf(GameFixtures.apple.key to stats(5), GameFixtures.milk.key to stats(1), GameFixtures.school.key to stats(3))
        assertEquals("우유", learnOf(match, result(Outcome.WON), progress)!!.korean)
        // A word never seen counts as 0 hits.
        assertEquals("학교", learnOf(match, result(Outcome.WON), mapOf(GameFixtures.apple.key to stats(2), GameFixtures.milk.key to stats(2)))!!.korean)
        // A lost round still teaches the first one you missed, whatever the hits.
        assertEquals("우유", learnOf(match, result(Outcome.LOST, failed = listOf(GameFixtures.milk.key)), progress)!!.korean)
    }

    @Test
    fun aWordIsMasteredWhenThisWinTookItsStreakToThree() {
        val hangman = RoundSpec.Hangman(1, GameFixtures.library, false, listOf("도", "서", "관"), emptyList())
        val k = GameFixtures.library.key
        assertTrue(learnOf(hangman, result(Outcome.WON), mapOf(k to stats(3, streak = 3)))!!.mastered)
        assertFalse(learnOf(hangman, result(Outcome.WON), mapOf(k to stats(4, streak = 4)))!!.mastered) // it was already
        assertFalse(learnOf(hangman, result(Outcome.WON), mapOf(k to stats(2, streak = 2)))!!.mastered)
        assertFalse(learnOf(hangman, result(Outcome.LOST), mapOf(k to stats(3, streak = 3)))!!.mastered)
    }
}
