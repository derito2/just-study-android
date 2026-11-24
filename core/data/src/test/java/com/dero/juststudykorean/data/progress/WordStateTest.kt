package com.dero.juststudykorean.data.progress

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.GameItemProgressEntity
import com.dero.juststudykorean.data.db.GameRoundsEntity
import com.dero.juststudykorean.data.db.QuestionAttemptsEntity
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration

/** H-03 phase B (FASE_11_PLAN PR 2): word_state is a cache of PR 1's on-the-fly numbers, word by word. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WordStateTest : GoldenDb() {
    private val temp = TempPrefs()
    private val words by lazy { WordProgress(db, clock, temp.prefs) }
    private val now = NOW.toEpochMilli()
    private fun daysAgo(d: Long, h: Long = 0) = now - Duration.ofDays(d).toMillis() - Duration.ofHours(h).toMillis()

    @After
    fun closePrefs() = temp.close()

    /** Games for a card word (learned by a streak over two days) and a Games-only word; old quiz questions about both. */
    private suspend fun synthetic(): Pair<String, String> {
        val cardWord = words.computed().values.first { it.sourceMask == FROM_CARDS && !it.status.mastered }.key // no Games or Quiz yet
        val gameWord = "고양이"
        var round = db.gameRoundsDao().maxId()
        for ((key, at) in listOf(cardWord to daysAgo(3), cardWord to daysAgo(2), cardWord to daysAgo(1), gameWord to daysAgo(4))) {
            db.gameRoundsDao().insert(GameRoundsEntity(++round, 1, round, "MatchPairs", 1, "won", 0, 900, "[\"w:$key\"]", at))
        }
        db.gameItemProgressDao().upsert(GameItemProgressEntity("w:$cardWord", 3, 3, 0, 3, daysAgo(1), null))
        db.gameItemProgressDao().upsert(GameItemProgressEntity("w:$gameWord", 3, 1, 2, 0, daysAgo(4), daysAgo(4)))
        var q = db.questionsDao().all().maxOf { it.id }
        var a = db.questionAttemptsDao().all().maxOfOrNull { it.id } ?: 0
        val old = daysAgo(30) // questions written before v6: they have no question_words row until the rebuild
        db.questionsDao().insert(QuestionsEntity(++q, "en_to_ko", "Translate", null, cardWord, null, 1, null, null, null, null, old))
        db.questionAttemptsDao().insert(QuestionAttemptsEntity(++a, q, null, null, "x", false, 800, daysAgo(1, 2)))
        db.questionsDao().insert(QuestionsEntity(++q, "ko_to_en", gameWord, null, "cat", null, 1, null, null, null, null, old))
        db.questionAttemptsDao().insert(QuestionAttemptsEntity(++a, q, null, null, "x", true, 800, daysAgo(2)))
        return cardWord to gameWord
    }

    @Test
    fun beforeTheFirstRebuildTheNumbersAreComputedOnTheFly() = runBlocking {
        synthetic()
        assertFalse(temp.prefs.wordStateBuiltV6.first())
        assertEquals(words.computed(), words.all())
        assertEquals(0, db.wordStateDao().all().size)
    }

    @Test
    fun afterTheRebuildTheCacheGivesTheSameWordsAsTheSources() = runBlocking {
        val (cardWord, gameWord) = synthetic()
        val onTheFly = words.computed()
        words.rebuildWordState()
        assertTrue(temp.prefs.wordStateBuiltV6.first())
        val cached = words.all()
        assertEquals(onTheFly.keys, cached.keys)
        for (key in onTheFly.keys) assertEquals(key, onTheFly.getValue(key), cached.getValue(key))
        // The old questions were linked by PR 1's rule, so Quiz counts backwards too.
        assertTrue(cached.getValue(cardWord).let { it.status.mastered && it.status.hard }) // learned in Games, last quiz answer failed
        assertEquals(FROM_GAMES or FROM_QUIZ, cached.getValue(gameWord).sourceMask)
        assertEquals(5, words.learned())
    }

    @Test
    fun rebuildingTwiceGivesTheSameRows() = runBlocking {
        synthetic()
        words.rebuildWordState()
        val state = db.wordStateDao().all().sortedBy { it.wordKey }
        val links = db.questionWordsDao().all().sortedBy { it.questionId }
        words.rebuildWordState()
        assertEquals(state, db.wordStateDao().all().sortedBy { it.wordKey })
        assertEquals(links, db.questionWordsDao().all().sortedBy { it.questionId })
        assertTrue(links.size >= 2)
    }
}
