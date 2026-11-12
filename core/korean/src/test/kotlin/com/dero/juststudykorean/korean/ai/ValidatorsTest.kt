package com.dero.juststudykorean.korean.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    private val entry = "Pattern: -(으)ㄹ게요\n커피 = Coffee\n먹다 = To eat\n-아/어 보다 = To try"

    /** The PoC 2 cases: base forms count, new words don't. */
    @Test
    fun koreanFromTheInput() {
        for (w in listOf("커피를", "커피", "먹어요", "먹었어요", "봤어요", "게요")) assertTrue(w, fromInput(w, entry))
        for (w in listOf("우유를", "학교", "갔어요")) assertFalse(w, fromInput(w, entry))
        assertEquals(listOf("학교"), inventedKorean("Use 커피를 like this, not 학교.", entry))
        assertTrue(koreanOnlyFrom("You can say 커피를 먹어요.", entry))
    }

    @Test
    fun citations() {
        assertTrue(citationsValid("It means near [1], see also [2].", setOf(1, 2)))
        assertFalse(citationsValid("It means near [3].", setOf(1, 2)))
        assertEquals(listOf(2, 1), citations("a [2] b [1] c [2]"))
    }

    @Test
    fun english() {
        assertTrue(isEnglish("This ending means you promise to do something: 할게요."))
        assertFalse(isEnglish("이것은 약속을 나타내는 표현입니다 정말"))
        assertFalse(isEnglish("It means 約束 in Japanese."))
        assertFalse(isEnglish("Yes."))
        assertTrue(hasHangul("커피")); assertFalse(hasHangul("coffee"))
    }
}
