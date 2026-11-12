package com.dero.juststudykorean.data.teacher

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.NotesFtsEntity
import com.dero.juststudykorean.korean.teacher.Teacher
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Teacher goldens (golden/teacher) with the app's search, notes_fts ([NotesSearch]), instead of the web self-check's
 * store: every answer must have the same text; sources may differ (another search) and are counted, not required.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TeacherFtsGoldenTest {
    @Test
    fun sameTextWithTheFtsSearch() {
        var total = 0; var text = 0; var sources = 0
        val wrong = mutableListOf<String>()
        for (file in File("../../golden/teacher").listFiles { f -> f.extension == "jsonl" }!!.sortedBy { it.name }) {
            val name = file.nameWithoutExtension
            val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), JustStudyDatabase::class.java)
                .setDriver(AndroidSQLiteDriver()).build()
            val chunks = JSONArray(File("../../golden/chunks/$name.json").readText()).let { a -> List(a.length()) { a.getJSONObject(it) } }
            runBlocking {
                val docId = chunks.first().getLong("document_id")
                db.documentsDao().insert(
                    DocumentsEntity(docId, "$name.pdf", "$name.pdf", "sha", 1, "ready", true, 0, 0, 0, "[]", null, 0, 0),
                )
                db.notesFtsDao().insertAll(chunks.mapIndexed { i, c ->
                    NotesFtsEntity(
                        documentId = docId, chromaId = c.getString("chroma_id"), page = c.getLong("page_number"),
                        section = c.optString("section").takeUnless { c.isNull("section") }, topic = c.getString("topic"),
                        contentType = c.getString("content_type"), textHash = "$i", text = c.getString("text"),
                    )
                })
            }
            val teacher = Teacher(NotesSearch(db))
            for (row in file.readLines().filter { it.isNotBlank() }.map(::JSONObject)) {
                total++
                val got = teacher.ask(row.getString("question"))
                val want = row.getJSONArray("sources").let { a -> List(a.length()) { a.getJSONObject(it).getString("chunk") } }
                if (got.answer == row.getString("answer")) {
                    text++
                    if (got.sources.map { it.hit.id } == want) sources++ else wrong += "$name [sources] ${row.getString("check")}: ${got.sources.map { it.hit.id }} vs $want"
                } else wrong += "$name [text] ${row.getString("check")} ${row.getString("question")}\n  want: ${row.getString("answer").replace("\n", "⏎")}\n  got:  ${got.answer.replace("\n", "⏎")}"
            }
            db.close()
        }
        println("teacher with FTS: $total questions, $text same text, $sources same text and sources")
        wrong.filter { "[text]" in it }.take(12).forEach(::println)
        wrong.filter { "[sources]" in it }.take(12).forEach(::println)
        assertEquals("same text", total, text)
    }
}
