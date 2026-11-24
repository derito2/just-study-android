package com.dero.juststudykorean.korean.reader

import com.dero.juststudykorean.korean.Golden
import com.dero.juststudykorean.korean.aeoForms
import com.dero.juststudykorean.korean.objects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8 (FASE_11_PLAN PR 8): what tapping a word of the Reader finds in your notes, in Lookup's order. */
class LookupTest {
    private fun entry(korean: String, english: String) = Gloss.Entry(korean, english, "notes.pdf", emptyList())

    private val notes = ReaderNotes(
        words = listOf(entry("학교", "School"), entry("먹다", "To eat"), entry("아이", "Child")).associateBy { it.korean },
        verbs = setOf("먹다"),
        grammar = listOf("-거든(요)" to "Because (giving a reason)", "-(으)ㄹ게요" to "I will"),
        sentences = listOf("비행기가 늦었어요. = The plane was late.", "학교에 가요. = I go to school."),
    )

    @Test
    fun theTappableWordsOfALineAreItsHangulWords() {
        val line = "저는 학교에서 공부해요. (I study)"
        val tokens = tokens(line)
        assertEquals(listOf("저는", "학교에서", "공부해요"), tokens.map { it.text })
        tokens.forEach { assertEquals(it.text, line.substring(it.start, it.end)) }
        assertTrue(tokens("Only English here.").isEmpty())
    }

    @Test
    fun anExactWordIsItsEntry() {
        assertEquals(notes.words["학교"], lookup("학교", notes))
        assertEquals(notes.words["아이"], lookup("아이", notes)) // ends in 이, but the word itself comes first
    }

    @Test
    fun aParticleIsTakenOff() {
        val g = lookup("학교에서", notes) as Gloss.Inflected
        assertEquals("학교", g.lemma)
        assertEquals("에서", g.particle)
        assertEquals(notes.words["학교"], g.entry)
    }

    @Test
    fun aConjugatedVerbGoesToItsDictionaryForm() {
        val g = lookup("먹었어요", notes) as Gloss.Inflected
        assertEquals("먹다", g.lemma)
        assertNull(g.particle)
        assertEquals(notes.words["먹다"], g.entry)
    }

    @Test
    fun aGrammarPointThatEndsTheWord() {
        assertEquals(Gloss.Grammar("-거든(요)", "Because (giving a reason)"), lookup("바쁘거든요", notes))
        assertEquals(Gloss.Grammar("-(으)ㄹ게요", "I will"), lookup("연락할게요", notes))
    }

    @Test
    fun notInYourNotesShowsTheSentencesWhereItIs() {
        assertEquals(Gloss.NotFound("비행기가", listOf("비행기가 늦었어요. = The plane was late.")), lookup("비행기가", notes))
        assertEquals(Gloss.NotFound("우산", emptyList()), lookup("우산", notes))
    }

    /** The 6 golden documents: every one-word entry is found as itself, with a particle, and every verb conjugated. */
    @Test
    fun theGoldenDocuments() {
        for (file in Golden.files("extracted")) {
            val json = org.json.JSONObject(file.readText())
            val words = json.getJSONArray("words").objects().map { entry(it.getString("korean"), it.getString("english")) }
            val grammar = json.getJSONArray("grammar").objects().map { it.getString("pattern") to it.getString("meaning") }
            val mine = ReaderNotes(words.associateBy { it.korean }, emptySet(), grammar, emptyList())
            for (w in words.filter { ' ' !in it.korean && tokens(it.korean).size == 1 }) {
                assertEquals(file.name, w, lookup(w.korean, mine))
                val withParticle = lookup(w.korean + "에서", mine)
                assertTrue("${file.name} ${w.korean}에서 → $withParticle", withParticle is Gloss.Inflected && withParticle.particle == "에서")
                if (w.korean.endsWith("다")) for (form in aeoForms(w.korean)) {
                    val g = lookup(form + "요", mine)
                    assertTrue("${file.name} ${form}요 → $g", g is Gloss.Inflected && g.lemma == w.korean || g is Gloss.Entry)
                }
            }
            // Each grammar pattern (particles are taken off before, not looked up) is found by tapping a word of its examples.
            for (point in json.getJSONArray("grammar").objects().filter { it.getString("kind") == "pattern" }) {
                val wanted = Gloss.Grammar(point.getString("pattern"), point.getString("meaning"))
                val words = point.getJSONArray("examples").objects().flatMap { tokens(it.getString("korean")) }
                assertTrue("${file.name} ${wanted.pattern}", words.any { lookup(it.text, mine) == wanted })
            }
        }
    }
}
