package com.dero.juststudykorean.data.progress

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.flashcards.FlashcardRepository_Factory
import com.dero.juststudykorean.data.games.GameRepository
import com.dero.juststudykorean.data.games.GameRepository_Factory
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.processing.PdfReader
import com.dero.juststudykorean.data.processing.ProcessingQueue
import com.dero.juststudykorean.data.processing.ProcessingQueue_Factory
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.quiz.QuizRepository_Factory
import dagger.internal.Provider
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * H-03 B: the writers take `WordProgress? = null` only so tests can leave it out. In the app, Hilt builds them with the
 * factories below, which must hand them the real WordProgress: this fails if a writer stops asking for it or gets null.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WordProgressWiringTest : GoldenDb() {
    private val temp = TempPrefs()
    private val words by lazy { WordProgress(db, clock, temp.prefs) }

    @After
    fun closePrefs() = temp.close()

    private fun <T : Any> p(value: T) = Provider { value }

    private fun wordsOf(writer: Any): Any? = writer.javaClass.getDeclaredField("words").apply { isAccessible = true }.get(writer)

    @Test
    fun theInjectedConstructorsAskForWordProgress() {
        for (c in listOf(FlashcardRepository::class.java, GameRepository::class.java, QuizRepository::class.java, ProcessingQueue::class.java)) {
            val inject = c.declaredConstructors.single { it.isAnnotationPresent(Inject::class.java) }
            assertTrue("${c.simpleName}: Hilt's constructor has no WordProgress", WordProgress::class.java in inject.parameterTypes)
        }
    }

    @Test
    fun hiltsFactoriesHandTheRealWordProgressToEveryWriter() {
        val progress = ProgressRepository(db, clock)
        val notes = NotesSource(context)
        val files = DocumentFiles(context)
        val indexer = DocumentIndexer(db, clock, progress, notes, files)
        val reader = PdfReader { error("no PDFs here") }
        val writers = listOf(
            FlashcardRepository_Factory.create(p(db), p(clock), p(progress), p(notes), p(words)).get(),
            GameRepository_Factory.create(p(db), p(clock), p(words)).get(),
            QuizRepository_Factory.create(p(db), p(clock), p(progress), p(notes), p(words)).get(),
            ProcessingQueue_Factory.create(p(db), p(clock), p(progress), p(files), p(indexer), p(words), p(reader)).get(),
        )
        for (w in writers) assertSame(w.javaClass.simpleName, words, wordsOf(w))
    }
}
