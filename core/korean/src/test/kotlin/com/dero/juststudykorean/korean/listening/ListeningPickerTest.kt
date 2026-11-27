package com.dero.juststudykorean.korean.listening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F4 (FASE_11_PLAN PR 7): which sentences each mode uses, and why a mode can't be used. */
class ListeningPickerTest {
    private fun s(korean: String, meaning: String = "x") = LabSentence("s:$korean", korean, meaning)
    private val all = listOf(
        s("가요."), s("학교에 가요."), s("저는 매일 아침 학교에 버스를 타고 가요."), s("오늘은 날씨가 정말 좋아서 친구들과 함께 공원에 가서 오랫동안 산책했어요."),
        s("밥을 먹어요.", meaning = ""),
    )

    @Test
    fun dictationTakesTwoToEightWordsWithATranslation() {
        assertEquals(listOf("학교에 가요.", "저는 매일 아침 학교에 버스를 타고 가요."), dictation(all).map { it.korean })
    }

    @Test
    fun shadowingTakesShortSentencesOfTwentySyllablesAtMost() {
        assertEquals(listOf("학교에 가요.", "저는 매일 아침 학교에 버스를 타고 가요."), shadowing(all).map { it.korean })
        assertEquals(16, syllables("저는 매일 아침 학교에 버스를 타고 가요."))
    }

    @Test
    fun aModeWithoutContentOrVoiceSaysWhy() {
        assertEquals("Needs the phone's Korean voice.", unavailable(ListenMode.Dictation, all, 3, voiceReady = false))
        assertEquals("Add sentences with a translation to your notes.", unavailable(ListenMode.Shadowing, emptyList(), 3, voiceReady = true))
        assertEquals("No cards are due right now.", unavailable(ListenMode.HandsFree, all, 0, voiceReady = true))
        assertNull(unavailable(ListenMode.Dictation, all, 0, voiceReady = true))
    }

    /** My Mistakes' Practice this (FASE_11_PLAN PR 9): only the sentences with those words, also conjugated. */
    @Test
    fun withWordsKeepsTheSentencesThatHaveThem() {
        val all = listOf(LabSentence("s:1", "학교에 가요", "I go to school"), LabSentence("s:2", "물을 마셨어요", "I drank water"), LabSentence("s:3", "비가 와요", "It rains"))
        org.junit.Assert.assertEquals(listOf("s:1", "s:2"), withWords(all, listOf("학교", "마시다")).map { it.key })
        org.junit.Assert.assertTrue(withWords(all, listOf("우산")).isEmpty())
    }
}
