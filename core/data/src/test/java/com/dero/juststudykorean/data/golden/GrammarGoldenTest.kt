package com.dero.juststudykorean.data.golden

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.grammar.GrammarFilter
import com.dero.juststudykorean.data.grammar.GrammarRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GrammarGoldenTest : GoldenDb() {
    private val repo by lazy { GrammarRepository(db) }

    @Test
    fun allPoints() = runBlocking {
        assertJson("grammar", golden("grammar"), js(repo.all().map { p ->
            obj("id" to p.id, "kind" to p.kind, "category" to p.category, "pattern" to p.pattern, "meaning" to p.meaning,
                "structure" to p.structure, "examples" to p.examples.map { obj("korean" to it.first, "english" to it.second) },
                "explanation" to p.explanation, "notes" to p.notes,
                "source" to obj("document_id" to p.source.documentId, "filename" to p.source.filename, "page_number" to p.source.pageNumber))
        }))
    }

    @Test
    fun filterAndSearchLikeTheWeb() = runBlocking {
        val all = repo.all()
        assertEquals(all.filter { it.category == "particles" }, repo.points(GrammarFilter.Particles, "").first())
        val q = all.first().meaning.take(4)
        assertEquals(all.filter { q.lowercase() in it.pattern.lowercase() || q.lowercase() in it.meaning.lowercase() }, repo.points(GrammarFilter.All, "  ${q.uppercase()} ").first())
    }
}
