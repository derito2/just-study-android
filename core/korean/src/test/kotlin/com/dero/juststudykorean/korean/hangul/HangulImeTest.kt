package com.dero.juststudykorean.korean.hangul

import org.junit.Assert.assertEquals
import org.junit.Test

/** FASE_8_PLAN 3.2: the 두벌식 automaton. In the cases, ⌫ is delete and a space is the space key. */
class HangulImeTest {
    private fun type(keys: String): String = HangulIme().apply {
        for (k in keys) when (k) { '⌫' -> delete(); ' ' -> space(); else -> input(k) }
    }.text

    private val cases = listOf(
        "ㅎㅏㄴㄱㅜㄱㅇㅓ" to "한국어",
        "ㅎㅏㄱㄱㅛㅇㅔ ㄱㅏㅇㅛ" to "학교에 가요",
        "ㄷㅏㄹㄱ" to "닭",
        "ㄷㅏㄹㄱㅣ" to "달기",
        "ㄷㅏㄹㄱㅇㅣ" to "닭이",
        "ㄷㅏㄹㄱㅣ⌫⌫" to "달", // the correction: 기 goes jamo by jamo; 달 was already closed
        "ㄲㅗㅊ" to "꽃",
        "ㅇㅓㅂㅅㅇㅓㅇㅛ" to "없어요",
        "ㅇㅗㅐ" to "왜",
        "ㅇㅡㅣㅅㅏ" to "의사",
        "ㅆㅏㅇ" to "쌍",
        "ㄱㅏㄴㅏ" to "가나",
        "ㄱㅏㄴㅏ⌫" to "가ㄴ",
        "ㅁㅏㄹ" to "말",
        "ㅁㅏㄹㅎㅏㄷㅏ" to "말하다",
        "ㄱㅏ" to "가",
        "ㄱ" to "ㄱ",
        "ㅏ" to "ㅏ",
        "ㄱㄴ" to "ㄱㄴ",
        "ㅏㅏ" to "ㅏㅏ",
        "ㅗㅏ" to "ㅘ",
        "ㄱㅗㅏ" to "과",
        "ㄱㅗㅏ⌫" to "고",
        "ㄱㅏㅏ" to "가ㅏ",
        "ㄱㅏㄸ" to "가ㄸ",
        "ㄱㅏㄸㅏ" to "가따",
        "ㄱㅏㅃㅏ" to "가빠",
        "ㄱㅏㄱㅅ" to "갃",
        "ㄱㅏㄱㅅ⌫" to "각",
        "ㄱㅏㄱㅅㅏ" to "각사",
        "ㄱㅏㄱㄱ" to "각ㄱ",
        "ㅇㅏㄴㅈㅏ" to "안자",
        "ㅇㅏㄴㅈㅇㅏ" to "앉아",
        "ㅇㅣㄹㄱㅇㅓ" to "읽어",
        "ㅂㅏㅂ ㅁㅓㄱㅇㅓ" to "밥 먹어",
        "ㅂㅏㅂ ⌫" to "밥",
        "ㅂㅏㅂ ⌫⌫" to "",
        "ㅎㅏㄴ⌫" to "하",
        "ㅎㅏㄴ⌫⌫" to "ㅎ",
        "ㅎㅏㄴ⌫⌫⌫" to "",
        "ㅎㅏㄴ⌫⌫⌫⌫" to "",
        "ㅎㅏ ㄱㅜㄱ⌫⌫⌫ " to "하  ",
        "ㅇㅘ" to "와",
        "ㄷㅏㄺㅇㅣ" to "닭이",
        "ㄱㅏㄴㅏㄷㅏ" to "가나다",
        "ㅅㅏㄹㅁ" to "삶",
        "ㅅㅏㄹㅁㅇㅣ" to "삶이",
        "ㅅㅏㄹㅁㅣ" to "살미",
    )

    @Test
    fun everySequenceGivesItsText() {
        assert(cases.size >= 40)
        for ((keys, text) in cases) assertEquals(keys, text, type(keys))
    }

    @Test
    fun deleteUndoesStepByStepFromEveryState() {
        val ime = HangulIme()
        "ㄷㅏㄹㄱ".forEach(ime::input)
        val steps = mutableListOf(ime.text)
        repeat(5) { ime.delete(); steps += ime.text }
        assertEquals(listOf("닭", "달", "다", "ㄷ", "", ""), steps)
    }
}
