package com.dero.juststudykorean.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** design/polish.md P-04: no `.pdf`; when it does not fit, an ellipsis in the middle that keeps the last 6 characters. */
class DisplayNameTest {
    @Test fun theExtensionIsNeverShown() {
        assertEquals("Anki_vocabulary_2", displayName("Anki_vocabulary_2.pdf", 40))
        assertEquals("Notes", displayName("Notes.PDF", 40))
    }

    @Test fun aNameThatFitsStaysAsItIs() {
        assertEquals("Lesson 5 notes", displayName("Lesson 5 notes.pdf", 14))
        assertEquals("Lesson 5 notes", displayName("Lesson 5 notes", 14))
    }

    @Test fun aLongNameKeepsItsHeadAndItsLastSixCharacters() {
        val shown = displayName("Korean_Notes_Example_test.pdf", 18)
        assertEquals("Korean_Note…e_test", shown)
        assertEquals(18, shown.length)
    }

    @Test fun neverLongerThanAskedAndKeepsTheEnd() {
        for (max in 8..30) {
            val shown = displayName("Anki_vocabulary_1_with_a_very_long_tail_2026.pdf", max)
            assertTrue("$max -> $shown", shown.length <= max)
            assertTrue(shown.endsWith("l_2026"))
        }
    }

    @Test fun doesNotCutInTheMiddleOfAnEmoji() {
        val shown = displayName("📚📚📚📚📚📚📚📚📚📚notes_end.pdf", 12)
        assertEquals("📚📚📚📚📚…es_end", shown)
        assertEquals(shown, shown.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)) // a lone surrogate would not survive this
    }

    @Test fun koreanNamesCountByCharacter() {
        assertEquals("한국어…완전판_정리", displayName("한국어_노트_학습자료_완전판_정리.pdf", 10))
    }

    @Test fun aNameThatIsOnlyTheExtensionStays() {
        assertEquals(".pdf", displayName(".pdf", 10))
    }
}
