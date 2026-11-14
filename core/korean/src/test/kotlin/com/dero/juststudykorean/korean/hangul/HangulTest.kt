package com.dero.juststudykorean.korean.hangul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/** FASE_8_PLAN 3.1. */
class HangulTest {
    @Test
    fun everySyllableGoesAndComesBack() {
        for (code in 0xAC00..0xD7A3) {
            val c = code.toChar()
            val b = decompose(c)!!
            assertEquals("$c", c, compose(b.lead, b.vowel, b.tail))
        }
        assertNull(decompose('a'))
        assertNull(decompose('ㄱ'))
    }

    @Test
    fun theSpecificationExamples() {
        assertEquals(listOf('ㅁ', 'ㅏ', 'ㄹ'), jamoOf('말'))
        assertEquals(listOf("말", "하", "다"), blocks("말하다"))
        assertEquals(2, blocks("하다").size)
        assertEquals(listOf('ㄷ', 'ㅏ', 'ㄹ', 'ㄱ'), jamoOf('닭'))
        assertEquals(listOf('ㅇ', 'ㅘ'), jamoOf('와'))
        assertEquals(listOf('ㅇ', 'ㅗ', 'ㅏ'), jamoOf('와', splitVowel = true))
        assertEquals(Block('ㄷ', 'ㅏ', "ㄹㄱ"), decompose('닭'))
    }

    @Test
    fun compoundFinalsAndVowels() {
        val tails = mapOf('ㄳ' to "ㄱㅅ", 'ㄵ' to "ㄴㅈ", 'ㄶ' to "ㄴㅎ", 'ㄺ' to "ㄹㄱ", 'ㄻ' to "ㄹㅁ", 'ㄼ' to "ㄹㅂ", 'ㄽ' to "ㄹㅅ", 'ㄾ' to "ㄹㅌ", 'ㄿ' to "ㄹㅍ", 'ㅀ' to "ㄹㅎ", 'ㅄ' to "ㅂㅅ")
        for ((t, parts) in tails) {
            assertEquals(t, joinTails(parts[0], parts[1]))
            assertEquals(parts, decompose(compose('ㄱ', 'ㅏ', parts)!!)!!.tail)
        }
        val vowels = mapOf('ㅘ' to "ㅗㅏ", 'ㅙ' to "ㅗㅐ", 'ㅚ' to "ㅗㅣ", 'ㅝ' to "ㅜㅓ", 'ㅞ' to "ㅜㅔ", 'ㅟ' to "ㅜㅣ", 'ㅢ' to "ㅡㅣ")
        for ((v, parts) in vowels) assertEquals(v, joinVowels(parts[0], parts[1]))
        assertNull(joinVowels('ㅏ', 'ㅏ'))
        assertNull(joinTails('ㄱ', 'ㄱ'))
        assertNull(compose('ㄱ', 'ㅏ', "ㄸ"))
        assertNull(compose('ㅏ', 'ㅏ'))
    }

    @Test
    fun nfdTextAndOtherCharacters() {
        val nfd = Normalizer.normalize("한국어", Normalizer.Form.NFD)
        assertEquals(listOf("한", "국", "어"), blocks(nfd))
        assertEquals(listOf("나", "는", " ", "O", "K", "!"), blocks("나는 OK!"))
    }

    @Test
    fun shapesBatchimAndDistance() {
        assertEquals(VowelShape.Vertical, shapeOf('ㅏ'))
        assertEquals(VowelShape.Horizontal, shapeOf('ㅗ'))
        assertEquals(VowelShape.Mixed, shapeOf('ㅘ'))
        assertTrue(hasBatchim('학'))
        assertFalse(hasBatchim('교'))
        assertTrue(endsInRieul('말'))
        assertFalse(endsInRieul('닭'))
        assertEquals(1, jamoDistance("불", "풀"))
        assertEquals(1, jamoDistance("섬", "솜"))
        assertEquals(1, jamoDistance("강", "간"))
        assertEquals(1, jamoDistance("자다", "차다"))
        assertEquals(0, jamoDistance("학교", "학교"))
        assertEquals(3, jamoDistance("달", "탈다")) // ㄷ→ㅌ, +ㄷ, +ㅏ
    }
}
