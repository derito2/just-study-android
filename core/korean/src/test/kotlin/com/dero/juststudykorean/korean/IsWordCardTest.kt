package com.dero.juststudykorean.korean

import org.junit.Assert.assertEquals
import org.junit.Test

class IsWordCardTest {
    @Test
    fun likeTheWeb() {
        val cases = mapOf("도서관" to true, "학교에 가다" to true, "-거든요" to false, "~네요" to false, "(으)니까" to false,
            "ㄹ 거야" to false, "library" to false, "" to false, "아주 아주 아주 많이" to false)
        cases.forEach { (front, expected) -> assertEquals(front, expected, isWordCard(front, hasStructure = false)) }
        assertEquals(false, isWordCard("도서관", hasStructure = true))
    }
}
