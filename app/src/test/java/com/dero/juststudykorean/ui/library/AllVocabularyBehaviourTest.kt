package com.dero.juststudykorean.ui.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.documents.DocumentRepository
import com.dero.juststudykorean.data.documents.VocabularyWord
import com.dero.juststudykorean.data.processing.ProcessingQueue
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** All vocabulary (R-012), with the real repositories on the golden base. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AllVocabularyBehaviourTest {
    private val app = GoldenApp()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    @After
    fun close() { scope.cancel(); app.close() }

    private fun repository(): DocumentRepository {
        val files = DocumentFiles(app.context)
        val indexer = DocumentIndexer(app.db, app.clock, app.progress, app.notes, files)
        val queue = ProcessingQueue(app.db, app.clock, app.progress, files, indexer) { error("no PDF is read here") }
        return DocumentRepository(app.db, app.clock, app.notes, files, indexer, app.flashcards, queue)
    }

    @Test
    fun typingFiltersInMemoryWithTheRepositorysRule() {
        val repo = repository()
        val all = runBlocking { repo.vocabulary() }
        assertTrue("the golden base has words", all.size > 3)
        val queries = listOf(all[0].korean.take(1), all[1].english.take(3), "zzzz-none")
        val expected: List<List<VocabularyWord>> = runBlocking { queries.map { repo.vocabulary(it) } }

        val vm = AllVocabularyViewModel(repo)
        scope.launch { vm.words.collect {} }
        idleUntil { vm.words.value.words != null }
        app.db.close() // from here the repository cannot answer: every list below comes from the one load
        queries.forEachIndexed { i, q ->
            vm.setQuery(q)
            idleUntil { vm.words.value.query == q }
            assertEquals(expected[i], vm.words.value.words)
        }
    }
}
