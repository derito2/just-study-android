package com.dero.juststudykorean.korean

import com.dero.juststudykorean.korean.teacher.MemoryStore
import com.dero.juststudykorean.korean.teacher.NOT_IN_NOTES
import com.dero.juststudykorean.korean.teacher.Teacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One case per responder the goldens don't reach (they cover entry, conjugated, grammar, phrase and check). */
class ResponderTest {
    private fun chunk(i: Int, section: String, text: String) = Chunk(9, "notes.pdf", 1, i, section, section, "vocabulary", "mixed", text)

    private val teacher = Teacher(
        MemoryStore(
            listOf(
                chunk(0, "Vocabulary", "Vocabulary\n1. 근처 = Nearby\nExample: 학교가 집 근처에 있어요.\nTranslation: The school is near my house.\n2. 안 = Inside\n3. 걷다 = To walk"),
                chunk(1, "1. -(으)ㄹ게요", "1. -(으)ㄹ게요\nMeaning: I will (a promise)\n제가 먼저 연락할게요. = I'll contact you first.\nExplanation: Only with I/we."),
                chunk(2, "Sentences", "Sentences\n1. 피곤하니까 집에 갈래요 = Since I am tired, I want to go home.\n2. 비가 오니까 우산을 챙기세요 = Because it's raining, take an umbrella."),
            ),
        ),
    )

    @Test
    fun compareShowsEachEntry() {
        val a = teacher.ask("Difference between 근처 and 안?")
        assertEquals("compare.v1", a.promptVersion)
        assertTrue(a.answer, a.answer.startsWith("This is what your notes say about each one:") && "1. 근처 = Nearby [1]" in a.answer && "2. 안 = Inside [1]" in a.answer)
    }

    @Test
    fun wordOnlyInAnExampleIsNoEntry() {
        val a = teacher.ask("What does 학교 mean?")
        assertEquals("no_entry", a.status)
        assertTrue(a.answer, a.answer.startsWith("Your notes don't have an entry for \"학교\". It only appears in an example:"))
    }

    @Test
    fun koreanWordWithoutEntryShowsItsSentences() {
        val a = teacher.ask("What does 우산 mean?")
        assertEquals("entry.v1", a.promptVersion)
        assertTrue(a.answer, a.answer.startsWith("Your notes don't have an entry for “우산”. It appears in these sentences:"))
    }

    @Test
    fun conjugatedWordLeadsToItsVerb() {
        val a = teacher.ask("What does 걸어요 mean?")
        assertTrue(a.answer, a.answer.startsWith("“걸어요” is a conjugated form of 걷다 in your notes:"))
    }

    @Test
    fun grammarWithoutPointShowsYourSentences() {
        val a = teacher.ask("What does -니까 mean?")
        assertEquals("examples.v1", a.promptVersion)
        assertTrue(a.answer, a.answer.startsWith("Your notes don't have a grammar point for -니까.\nSentences in your notes that use it:"))
    }

    @Test
    fun exampleOfAPointUsesItsExamples() {
        val a = teacher.ask("Example with -(으)ㄹ게요")
        assertEquals("grammar.v1", a.promptVersion)
        assertTrue(a.answer, a.answer.startsWith("Examples of -(으)ㄹ게요 in your notes [1]:\n• 제가 먼저 연락할게요."))
    }

    @Test
    fun nothingCloseIsNotInYourNotes() {
        val a = teacher.ask("Tell me about quantum physics")
        assertEquals("no_context", a.status)
        assertEquals(NOT_IN_NOTES, a.answer)
        assertTrue(a.sources.isEmpty())
    }

    @Test
    fun emptyAndHugeQuestionsDoNotBreak() {
        for (q in listOf("", "?", "가".repeat(2000), "a ".repeat(1000))) assertTrue(teacher.ask(q).answer.isNotEmpty())
    }
}
