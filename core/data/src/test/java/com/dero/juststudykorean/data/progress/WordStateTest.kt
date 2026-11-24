package com.dero.juststudykorean.data.progress

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.GameItemProgressEntity
import com.dero.juststudykorean.data.db.GameRoundsEntity
import com.dero.juststudykorean.data.db.QuestionAttemptsEntity
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.documents.DocumentRepository
import com.dero.juststudykorean.data.documents.goldenParsed
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.processing.ProcessingQueue
import com.dero.juststudykorean.data.games.GameRepository
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.quiz.QuizRequest
import com.dero.juststudykorean.korean.Rating
import com.dero.juststudykorean.korean.games.GameType
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
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

    /** Every word of the cache equals PR 1's on-the-fly computation. */
    private suspend fun assertSameAsSources() {
        val onTheFly = words.computed()
        val cached = words.all()
        assertEquals(onTheFly.keys, cached.keys)
        for (key in onTheFly.keys) assertEquals(key, onTheFly.getValue(key), cached.getValue(key))
    }

    @Test
    fun reviewsRoundsAndAnswersKeepTheCacheEqualToTheSources() = runBlocking {
        val (cardWord, _) = synthetic()
        words.rebuildWordState()
        val progress = ProgressRepository(db, clock)
        val notes = NotesSource(context)
        val cards = FlashcardRepository(db, clock, progress, notes, words)
        val games = GameRepository(db, clock, words)
        val quiz = QuizRepository(db, clock, progress, notes, words)

        val card = db.flashcardsDao().all().first { norm(it.koreanWord) == cardWord }
        cards.review(card.id, Rating.Again, 1000) // the word's Again count and its Hard
        assertSameAsSources()

        val session = games.startSession(7)
        games.saveRound(session, 0, GameType.WordMatch, 1, RoundResult(Outcome.LOST, 1, 900, listOf("w:$cardWord")))
        games.saveRound(session, 1, GameType.WordMatch, 1, RoundResult(Outcome.WON, 0, 900, listOf("w:강아지"))) // a word the cache didn't know
        assertSameAsSources()

        val run = quiz.generate(QuizRequest(count = 8, seed = 11))!! // new questions are linked to their word as they are written
        for (q in run.questions) quiz.answer(run.id, q.id, q.optionsJson?.let { org.json.JSONArray(it).getString(0) } ?: "no", 1000)
        assertSameAsSources()
        assertTrue(db.questionWordsDao().all().size > 2)
    }

    @Test
    fun deletingADocumentWithItsCardsAndReprocessingAnotherRebuildTheCache() = runBlocking {
        synthetic()
        words.rebuildWordState()
        val progress = ProgressRepository(db, clock)
        val notes = NotesSource(context)
        val files = DocumentFiles(context)
        val indexer = DocumentIndexer(db, clock, progress, notes, files)
        val queue = ProcessingQueue(db, clock, progress, files, indexer, words) { goldenParsed("Korean_Notes_Example") }
        val repo = DocumentRepository(db, clock, notes, files, indexer, FlashcardRepository(db, clock, progress, notes, words), queue)
        val ready = db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }

        val learnedBefore = words.computed().values.count { it.status.mastered }
        repo.delete(ready[0].id, withCards = true) // its words and their reviews go: the cache must not keep them
        assertSameAsSources()
        assertTrue(words.learned() <= learnedBefore)

        repo.reprocess(ready[1].id)
        queue.runAll()
        assertSameAsSources()
    }

    @Test
    fun creatingDeletingAndRestoringCardsRebuildTheCache() = runBlocking {
        val (cardWord, _) = synthetic()
        words.rebuildWordState()
        val cards = FlashcardRepository(db, clock, ProgressRepository(db, clock), NotesSource(context), words)
        val deck = cards.decksNow().first { it.total > 0 && !it.custom }
        val deleted = cards.delete(deck.id)
        assertSameAsSources()
        cards.undoDelete(deleted)
        assertSameAsSources()
        cards.createCustomDeck("Mine", listOf(cardWord))
        assertSameAsSources()
    }
}
