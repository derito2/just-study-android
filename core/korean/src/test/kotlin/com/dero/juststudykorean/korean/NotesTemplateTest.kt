package com.dero.juststudykorean.korean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 8, task 0b: the template of docs/NOTES_TEMPLATE.md (tools/fixtures/notes_template.txt, invented notes; the PDF
 * of the same text is tools/fixtures/Notes_template.pdf) is read whole: nothing is dropped by a limit.
 */
class NotesTemplateTest {
    private val text = File("../../tools/fixtures/notes_template.txt").readText()
    private val written = text.lines()
    private val chunks = chunkDocument(1, "Notes_template.pdf", text.split("\u000c").mapIndexed { i, page -> ExtractedPage(i + 1, null, page.trim()) })
    private val longKorean = written.first { it.startsWith("우리 동네") && " = " !in it }

    private fun word(korean: String): VocabPair {
        val parts = chunks.flatMap(::extractPairs).filter { it.korean == korean }
        assertTrue("$korean is read", parts.isNotEmpty())
        // A long entry may be split in several chunks (each repeats its head): together they have everything.
        return parts.first().copy(examples = parts.flatMap { it.examples }.distinct(), notes = parts.flatMap { it.notes }.distinct())
    }

    @Test
    fun aWordKeepsAllItsExamplesAndNotes() {
        val apple = word("사과")
        assertEquals(written.count { it.startsWith("사과를 ") }, apple.examples.size)
        assertEquals(40, apple.examples.size)
        assertEquals(2, apple.notes.size)
    }

    @Test
    fun aLongMeaningIsKeptWhole() {
        val meaning = written.first { it.startsWith("2. 다락 = ") }.substringAfter(" = ")
        assertTrue(meaning.length > 120)
        assertEquals(meaning, word("다락").english)
    }

    @Test
    fun aGrammarPointKeepsItsWholeExplanationStructureAndExamples() {
        val point = chunks.flatMap(::extractGrammar).single { it.pattern == "-아서/어서" }
        val meaning = written.first { it.startsWith("Meaning: ") }.substringAfter("Meaning: ")
        assertTrue(meaning.length > 160)
        assertEquals(meaning, point.meaning)
        assertEquals(3, point.forms.size)
        assertEquals(6, point.examples.size)
        assertEquals(90, longKorean.length)
        assertEquals(2, point.examples.count { it.first == longKorean }) // with and without a translation, never glued to the next
        val explanation = written.filter { it.startsWith("Explanation line ") }
        assertEquals(30, explanation.size)
        assertTrue(explanation.all { it in (point.explanation ?: "") })
    }

    @Test
    fun theGrammarScreenKeepsTheSame() {
        val point = chunks.flatMap(::referencePoints).single { it.pattern == "-아서/어서" }
        assertEquals(30, point.explanation.size)
        assertEquals(3, point.structure.size)
        assertEquals(6, point.examples.size)
        assertEquals(2, point.examples.count { it[0] == longKorean })
    }
}
