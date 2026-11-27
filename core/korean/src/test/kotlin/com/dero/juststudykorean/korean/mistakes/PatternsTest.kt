package com.dero.juststudykorean.korean.mistakes

import com.dero.juststudykorean.korean.mistakes.PatternKind.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** F5 (FASE_11_PLAN PR 9): what kind of mistake a pair (expected, given) is, by rules. */
class PatternsTest {
    private fun m(expected: String, given: String?, at: Long = 0, source: String = "Quiz") = Mistake(source, expected, given, null, null, at)

    private fun check(kind: PatternKind, vararg pairs: Pair<String, String?>) {
        for ((expected, given) in pairs) assertEquals("$expected ← $given", kind, classify(m(expected, given)))
    }

    /** golden/db/quiz_mistakes.json: questions 17, 9, 5, 35 and 31 of the golden base, with the answers given there. */
    @Test fun theGoldenQuizMistakes() {
        check(Meaning, "To be delicious" to "To leave (food)", "Bag" to "nope", "Did you eat?" to "nope")
        check(WrongWord, "거울" to "구름", "빠르다" to "파랗다")
    }

    @Test fun particleByTheBatchim() = check(ParticleBatchim, "책을 읽어요" to "책를 읽어요", "저는" to "저은", "물이" to "물가", "집으로" to "집로")
    @Test fun vowelHarmony() = check(VowelHarmony, "먹어서" to "먹아서", "좋아요" to "좋어요", "살았어요" to "살었어요")
    @Test fun contraction() = check(Contraction, "가서" to "가아서", "서요" to "서어요")
    @Test fun irregularDoneAsRegular() = check(Irregular, "들어요" to "듣어요", "도와요" to "돕아요", "지어요" to "짓어요", "걸으면" to "걷으면")
    @Test fun tense() = check(Tense, "먹었어요" to "먹어요", "가요" to "갔어요")
    @Test fun batchim() = check(Batchim, "밤" to "밥", "산" to "상")
    @Test fun vowel() = check(Vowel, "섬" to "솜", "개" to "게")
    @Test fun spacing() = check(Spacing, "학교에 가요" to "학교에가요")
    @Test fun withoutWhatYouGaveItIsTheWordItself() = check(WrongWord, "학교" to null)

    @Test
    fun patternsGroupByKindMostFrequentFirst() {
        val all = listOf(
            m("먹어서", "먹아서", at = 5), m("좋아요", "좋어요", at = 9), m("거울", "구름", at = 7),
            m("책을", "책를", at = 1), // before the period
        )
        val ps = patterns(all, since = 2)
        assertEquals(listOf(VowelHarmony, WrongWord), ps.map { it.kind })
        assertEquals(2, ps[0].count)
        assertEquals(9L, ps[0].lastAt)
        assertEquals("좋아요", ps[0].examples.first().expected) // newest first
        assertEquals("을/를 after a consonant", patterns(all, since = 0).first { it.kind == ParticleBatchim }.label)
    }
}
