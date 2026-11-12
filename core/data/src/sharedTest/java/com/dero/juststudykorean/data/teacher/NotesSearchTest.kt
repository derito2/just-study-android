package com.dero.juststudykorean.data.teacher

import android.content.Context
import android.os.Build
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.NotesFtsEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** R-014: roots of Korean words find a fragment that only has them conjugated or with a particle, and a prefix alone does not. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class NotesSearchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // On the phone, the SQLite the app ships (bundled); Robolectric only has the framework one.
    private val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java)
        .setDriver(if (Build.FINGERPRINT == "robolectric") AndroidSQLiteDriver() else BundledSQLiteDriver()).build()
    private val search = NotesSearch(db)

    init {
        runBlocking {
            db.notesFtsDao().insertAll(
                listOf(
                    NotesFtsEntity(documentId = 1, chromaId = "1:1:0", page = 1, section = null, topic = "General", contentType = "general", textHash = "a", text = "1. 학교에서 공부해요 = I study at school"),
                    NotesFtsEntity(documentId = 1, chromaId = "1:1:1", page = 1, section = null, topic = "General", contentType = "general", textHash = "b", text = "2. 학생이에요 = I'm a student"),
                ),
            )
        }
    }

    @After
    fun close() = db.close()

    private fun ids(question: String) = search.retrieve(question, 4).hits.map { it.id }

    @Test
    fun rootsFindConjugatedAndWithParticle() {
        assertEquals(listOf("1:1:0"), ids("What does 학교 mean?"))
        assertEquals(listOf("1:1:0"), ids("What does 공부하다 mean?"))
        assertEquals(listOf("1:1:1"), ids("What does 학생 mean?"))
    }

    @Test
    fun aPrefixAloneIsNotAMatch() {
        assertTrue(ids("What does 학 mean?").isEmpty())
    }

    @Test
    fun lexicalOnlyAndFindKeepTheWebsSubstringRule() {
        assertEquals(listOf("1:1:0"), search.lexicalOnly("How do I say school?")!!.hits.map { it.id })
        assertEquals(2, search.find(null, 10).size)
        assertEquals(listOf("1:1:1"), search.find(listOf("student"), 10).map { it.id })
        assertEquals(2, search.count())
    }

    @Test
    fun quotesInTheQuestionDoNotBreakTheQuery() {
        assertTrue(ids("What does \"학교\" OR \"x\"* mean?").isNotEmpty())
        assertTrue(search.retrieve("\"\" * NEAR AND", 4).hits.isEmpty())
    }
}
