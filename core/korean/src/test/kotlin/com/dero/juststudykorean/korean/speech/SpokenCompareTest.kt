package com.dero.juststudykorean.korean.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/** FASE_9_PLAN 4: comparing what was heard with your notes, with no verdict. Invented examples. */
class SpokenCompareTest {
    @Test
    fun sameWithOrWithoutSpacesAndSigns() {
        val c = compareSpoken("저는 학생이에요.", listOf("저는학생이에요"))
        assertTrue(c.matches)
        assertTrue(compareSpoken("저는 학생이에요", listOf("저는 학생이에요?!")).matches)
        assertTrue(compareSpoken("괜찮아요~", listOf("“괜찮아요…”")).matches)
        assertEquals(emptyList<Int>(), c.heardMarks)
    }

    @Test
    fun anyAlternativeCounts() {
        val c = compareSpoken("가장 / 제일", listOf("제일"))
        assertTrue(c.matches)
        assertEquals("제일", c.notes)
    }

    @Test
    fun nfdIsTheSameText() {
        val nfd = Normalizer.normalize("학교", Normalizer.Form.NFD)
        assertTrue(compareSpoken("학교", listOf(nfd)).matches)
        assertTrue(compareSpoken(nfd, listOf("학교")).matches)
    }

    @Test
    fun aDifferentParticleUnderlinesOneSyllable() {
        val c = compareSpoken("학교에", listOf("학교를"))
        assertFalse(c.matches)
        assertEquals(listOf(2), c.heardMarks)
        assertEquals(listOf(2), c.notesMarks)
    }

    @Test
    fun marksSkipSpacesInTheShownText() {
        val c = compareSpoken("저는 학교에 가요", listOf("저는 학교를 가요"))
        assertFalse(c.matches)
        assertEquals(listOf(5), c.heardMarks) // "저는 학교를": index 5 is 를
        assertEquals(listOf(5), c.notesMarks)
    }

    @Test
    fun aMissingSyllableIsMarkedInYourNotes() {
        val c = compareSpoken("도서관", listOf("도관"))
        assertEquals(listOf(1), c.notesMarks)
        assertEquals(emptyList<Int>(), c.heardMarks)
    }

    @Test
    fun digitsAndLatinNeverFail() {
        val c = compareSpoken("세 개", listOf("3개"))
        assertFalse(c.matches)
        assertEquals("3개", c.heard)
        assertFalse(compareSpoken("커피", listOf("coffee")).matches)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noCandidatesIsNoSpeechAndNotCompared() {
        compareSpoken("학교", emptyList())
    }
}
