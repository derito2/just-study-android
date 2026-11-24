package com.dero.juststudykorean.data.progress

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.FlashcardReviewsEntity
import com.dero.juststudykorean.data.db.FlashcardsEntity
import com.dero.juststudykorean.data.db.GameItemProgressEntity
import com.dero.juststudykorean.data.db.GameRoundsEntity
import com.dero.juststudykorean.data.db.QuestionAttemptsEntity
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration

/** H-03 phase A on the golden base: one word, one meaning of Learned and Hard (D11-1) over Flashcards, Games and Quiz. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WordProgressTest : GoldenDb() {
    private val words by lazy { WordProgress(db, clock) }
    private val now = NOW.toEpochMilli()
    private fun daysAgo(d: Long, h: Long = 0) = now - Duration.ofDays(d).toMillis() - Duration.ofHours(h).toMillis()

    /** A golden card brought to 600 h with five Good reviews, the last [lastDaysAgo] days ago. */
    private suspend fun learnCard(card: FlashcardsEntity, lastDaysAgo: Long): String {
        db.flashcardsDao().update(card.copy(intervalHours = 600))
        var id = db.flashcardReviewsDao().all().maxOf { it.id }
        for (i in 4 downTo 0) db.flashcardReviewsDao().insert(FlashcardReviewsEntity(++id, card.id, "good", daysAgo(lastDaysAgo + i), 100, if (i == 0) 600 else 100))
        return norm(card.koreanWord)
    }

    private suspend fun playRounds(key: String, vararg at: Long, outcome: String = "won") {
        var id = db.gameRoundsDao().maxId()
        for (t in at) db.gameRoundsDao().insert(GameRoundsEntity(++id, 1, id, "MatchPairs", 1, outcome, 0, 1000, "[\"w:$key\"]", t))
    }

    @Test
    fun theGoldenBaseUnderD11() = runBlocking {
        // The old rule (repetitions >= 1) said 26; D11-1 keeps the 4 cards with 21 days and no recent Again.
        assertEquals(4, words.learned())
        assertEquals(1 to 2, words.learnedWeekly())
        assertEquals(4, words.all().values.count { it.status.masteredAt != null })
    }

    /** A golden card that is not learned yet. */
    private suspend fun freshCard(skip: Int = 0) = words.all().let { all ->
        db.flashcardsDao().all().sortedBy { it.id }.filter { all[norm(it.koreanWord)]?.status?.mastered == false }.distinctBy { norm(it.koreanWord) }[skip]
    }

    @Test
    fun aCardLearnedThisWeekCountsInBothPlaces() = runBlocking {
        val key = learnCard(freshCard(), lastDaysAgo = 1)
        assertTrue(words.all().getValue(key).status.mastered)
        assertEquals(5, words.learned())
        assertEquals(2 to 2, words.learnedWeekly())
    }

    @Test
    fun aWordLearnedLastWeekIsNotThisWeeks() = runBlocking {
        learnCard(freshCard(0), lastDaysAgo = 9)
        learnCard(freshCard(0), lastDaysAgo = 2)
        assertEquals(6, words.learned())
        assertEquals(2 to 3, words.learnedWeekly())
    }

    @Test
    fun gamesKeysLoseTheirPrefixAndJoinTheCardsWord() = runBlocking {
        val key = norm(freshCard().koreanWord)
        playRounds(key, daysAgo(2), daysAgo(2, 1), daysAgo(1))
        db.gameItemProgressDao().upsert(GameItemProgressEntity("w:$key", 3, 3, 0, 3, daysAgo(1), null))
        val state = words.all().getValue(key)
        assertTrue(state.status.mastered)
        assertEquals(daysAgo(1), state.status.masteredAt)
        assertEquals(3L, state.gameCorrect)
        assertEquals(5, words.learned())
    }

    @Test
    fun onlyTheRoundsOfTheCurrentStreakCount() = runBlocking {
        val key = "고양이"
        playRounds(key, daysAgo(5)) // an old win, before the streak was broken
        playRounds(key, daysAgo(4), outcome = "lost")
        playRounds(key, daysAgo(1, 3), daysAgo(1, 2), daysAgo(1, 1)) // the streak: three wins on one day
        playRounds(key, daysAgo(0), outcome = "neutral") // neutral rounds don't touch an item's progress
        db.gameItemProgressDao().upsert(GameItemProgressEntity("w:$key", 5, 4, 1, 3, daysAgo(1, 1), daysAgo(4)))
        assertFalse(words.all().getValue(key).status.mastered)
    }

    @Test
    fun aQuizQuestionCountsForItsWordOnlyWhenItsAnswerOrPromptIsTheWord() = runBlocking {
        val key = "고양이" // a word only Games knows: no golden question is about it
        db.gameItemProgressDao().upsert(GameItemProgressEntity("w:$key", 1, 1, 0, 1, daysAgo(5), null))
        var qid = db.questionsDao().all().maxOf { it.id }
        var aid = db.questionAttemptsDao().all().maxOfOrNull { it.id } ?: 0
        val byAnswer = ++qid
        db.questionsDao().insert(QuestionsEntity(byAnswer, "en_to_ko", "Translate: cat", null, key, null, 1, null, null, null, null, daysAgo(3)))
        val byPrompt = ++qid
        db.questionsDao().insert(QuestionsEntity(byPrompt, "ko_to_en", key, null, "cat", null, 1, null, null, null, null, daysAgo(3)))
        val unrelated = ++qid
        db.questionsDao().insert(QuestionsEntity(unrelated, "ko_to_en", "$key and more", null, "something else", null, 1, null, null, null, null, daysAgo(3)))
        db.questionAttemptsDao().insert(QuestionAttemptsEntity(++aid, byPrompt, null, null, "x", true, 1000, daysAgo(2)))
        db.questionAttemptsDao().insert(QuestionAttemptsEntity(++aid, byAnswer, null, null, "x", false, 1000, daysAgo(1)))
        db.questionAttemptsDao().insert(QuestionAttemptsEntity(++aid, unrelated, null, null, "x", true, 1000, daysAgo(0)))
        val state = words.all().getValue(key)
        assertTrue(state.status.hard) // its last attempt failed; the later right answer was to a question not about it
        assertEquals(1, state.quizCorrect)
        assertEquals(1, state.quizWrong)
    }

    @Test
    fun aWordNeverStudiedHasNoDate() = runBlocking {
        val state = words.all().values.first { it.again == 0 && it.gameCorrect == 0L }
        assertNull(state.status.masteredAt)
    }
}
