package com.dero.juststudykorean.korean.grammar

import com.dero.juststudykorean.korean.grammar.Ending.*
import com.dero.juststudykorean.korean.grammar.WordKind.Adjective
import com.dero.juststudykorean.korean.grammar.WordKind.Verb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F3 (FASE_11_PLAN PR 5): the reference of the conjugator is this table (there is no web golden). Per ending: a regular
 * verb with a final consonant, one without, one in ㄹ, one of 하다 and an irregular of the closed list, where it applies.
 * Irregular cases cite their source: 한국어기초사전 (krdict.korean.go.kr), 활용 of each headword.
 */
class ConjugatorTest {
    private fun check(ending: Ending, kind: WordKind, vararg cases: Pair<String, String>) {
        for ((verb, want) in cases) assertEquals("$verb + ${ending.label}", want, conjugate(verb, ending, kind).firstOrNull())
    }

    // Irregular verbs used below and their krdict 활용 (the source of each irregular case):
    // 듣다 [들어, 들으니] ㄷ불규칙 · 걷다 [걸어, 걸으니] ㄷ불규칙 · 돕다 [도와, 도우니] ㅂ불규칙 · 춥다 [추워, 추우니] ㅂ불규칙 ·
    // 짓다 [지어, 지으니] ㅅ불규칙 · 낫다 [나아, 나으니] ㅅ불규칙 · 모르다 [몰라, 모르니] 르불규칙 · 빠르다 [빨라, 빠르니] 르불규칙 ·
    // 그렇다 [그래, 그러니] ㅎ불규칙 · 빨갛다 [빨개, 빨가니] ㅎ불규칙.

    @Test
    fun theAeoFormAndItsContractions() {
        check(PoliteAeo, Verb, "먹다" to "먹어요", "가다" to "가요", "살다" to "살아요", "공부하다" to "공부해요", "보다" to "봐요",
            "주다" to "줘요", "마시다" to "마셔요", "되다" to "돼요", "쓰다" to "써요", "오다" to "와요", "서다" to "서요", "켜다" to "켜요")
        check(PoliteAeo, Adjective, "작다" to "작아요", "바쁘다" to "바빠요", "크다" to "커요", "좋다" to "좋아요")
        check(PoliteAeo, Verb, "듣다" to "들어요", "걷다" to "걸어요", "돕다" to "도와요", "짓다" to "지어요", "모르다" to "몰라요")
        check(PoliteAeo, Adjective, "춥다" to "추워요", "낫다" to "나아요", "빠르다" to "빨라요", "그렇다" to "그래요", "빨갛다" to "빨개요")
        assertTrue("보아요" in conjugate("보다", PoliteAeo, Verb)) // the uncontracted form is right too
    }

    @Test
    fun regularVerbsThatLookIrregular() { // krdict: 입다 [입어], 받다 [받아], 웃다 [웃어], 좋다 [좋아], 따르다 [따라] are regular
        check(SoBecause, Verb, "입다" to "입어서", "받다" to "받아서", "웃다" to "웃어서", "따르다" to "따라서")
        check(SoBecause, Adjective, "좋다" to "좋아서")
    }

    @Test
    fun aVerbWhoseEndingIsAmbiguousIsNotAsked() {
        assertEquals(emptySet<String>(), conjugate("묻다", SoBecause, Verb)) // 묻다 "ask" is ㄷ-irregular, "bury" regular
        assertEquals(emptySet<String>(), conjugate("굽다", SoBecause, Verb)) // "roast" ㅂ-irregular, "bend" regular
        assertEquals(emptySet<String>(), conjugate("이다", PoliteAeo, Adjective)) // 이다/아니다 are out
        assertEquals(emptySet<String>(), conjugate("가다", Promise, null)) // a verb-only ending needs a sure verb
        assertEquals(emptySet<String>(), conjugate("작다", Promise, Adjective))
        assertEquals(emptySet<String>(), conjugate("먹다", Please, Verb)) // its honorific is 드세요: more than one right answer
    }

    @Test
    fun pastFormalAndTheConsonantEndings() {
        check(PastPolite, Verb, "먹다" to "먹었어요", "가다" to "갔어요", "살다" to "살았어요", "공부하다" to "공부했어요", "듣다" to "들었어요", "돕다" to "도왔어요")
        check(Formal, Verb, "먹다" to "먹습니다", "가다" to "갑니다", "살다" to "삽니다", "공부하다" to "공부합니다", "듣다" to "듣습니다")
        check(Formal, Adjective, "길다" to "깁니다", "그렇다" to "그렇습니다")
        check(And, Verb, "먹다" to "먹고", "가다" to "가고", "살다" to "살고", "공부하다" to "공부하고", "듣다" to "듣고")
        check(But, Verb, "먹다" to "먹지만", "가다" to "가지만", "살다" to "살지만", "공부하다" to "공부하지만", "돕다" to "돕지만")
        check(Exclaim, Verb, "먹다" to "먹네요", "가다" to "가네요", "살다" to "사네요", "공부하다" to "공부하네요", "듣다" to "듣네요")
        check(Exclaim, Adjective, "그렇다" to "그렇네요")
        assertTrue("그러네요" in conjugate("그렇다", Exclaim, Adjective)) // both are standard (국립국어원, 2015)
        check(Recall, Verb, "먹다" to "먹더라고요", "가다" to "가더라고요", "살다" to "살더라고요", "공부하다" to "공부하더라고요", "듣다" to "듣더라고요")
        check(YouKnow, Verb, "먹다" to "먹잖아요", "가다" to "가잖아요", "살다" to "살잖아요", "공부하다" to "공부하잖아요", "짓다" to "짓잖아요")
        check(Reason, Verb, "먹다" to "먹거든요", "가다" to "가거든요", "살다" to "살거든요", "공부하다" to "공부하거든요", "모르다" to "모르거든요")
        check(Confirm, Verb, "먹다" to "먹지요", "가다" to "가지요", "살다" to "살지요", "공부하다" to "공부하지요", "듣다" to "듣지요")
        assertTrue("먹죠" in conjugate("먹다", Confirm, Verb))
        check(Want, Verb, "먹다" to "먹고 싶어요", "가다" to "가고 싶어요", "살다" to "살고 싶어요", "공부하다" to "공부하고 싶어요", "듣다" to "듣고 싶어요")
        check(Progressive, Verb, "먹다" to "먹고 있어요", "가다" to "가고 있어요", "살다" to "살고 있어요", "공부하다" to "공부하고 있어요", "듣다" to "듣고 있어요")
        check(Decide, Verb, "먹다" to "먹기로 하다", "가다" to "가기로 하다", "살다" to "살기로 하다", "공부하다" to "공부하기로 하다", "듣다" to "듣기로 하다")
        check(Dont, Verb, "먹다" to "먹지 마세요", "가다" to "가지 마세요", "살다" to "살지 마세요", "공부하다" to "공부하지 마세요", "듣다" to "듣지 마세요")
    }

    @Test
    fun theAeoEndings() {
        check(SoBecause, Verb, "먹다" to "먹어서", "가다" to "가서", "살다" to "살아서", "공부하다" to "공부해서", "듣다" to "들어서", "짓다" to "지어서")
        check(SoBecause, Adjective, "춥다" to "추워서", "그렇다" to "그래서")
        check(Try, Verb, "먹다" to "먹어 보다", "가다" to "가 보다", "살다" to "살아 보다", "공부하다" to "공부해 보다", "듣다" to "들어 보다")
        check(Must, Verb, "먹다" to "먹어야 하다", "가다" to "가야 하다", "살다" to "살아야 하다", "공부하다" to "공부해야 하다", "돕다" to "도와야 하다")
        check(Must, Adjective, "춥다" to "추워야 하다")
        check(AndThen, Verb, "먹다" to "먹어가지고", "가다" to "가가지고", "살다" to "살아가지고", "공부하다" to "공부해가지고", "듣다" to "들어가지고")
    }

    @Test
    fun theEuEndingsWithRieulAndTheIrregulars() {
        check(If, Verb, "먹다" to "먹으면", "가다" to "가면", "살다" to "살면", "공부하다" to "공부하면", "듣다" to "들으면", "돕다" to "도우면", "짓다" to "지으면")
        check(If, Adjective, "춥다" to "추우면", "그렇다" to "그러면")
        check(Because, Verb, "먹다" to "먹으니까", "가다" to "가니까", "살다" to "사니까", "공부하다" to "공부하니까", "듣다" to "들으니까")
        check(Future, Verb, "먹다" to "먹을 거예요", "가다" to "갈 거예요", "살다" to "살 거예요", "공부하다" to "공부할 거예요", "듣다" to "들을 거예요")
        check(Future, Adjective, "춥다" to "추울 거예요", "그렇다" to "그럴 거예요")
        check(LetUs, Verb, "먹다" to "먹읍시다", "가다" to "갑시다", "살다" to "삽시다", "공부하다" to "공부합시다", "듣다" to "들읍시다")
        check(Please, Verb, "읽다" to "읽으세요", "가다" to "가세요", "살다" to "사세요", "공부하다" to "공부하세요", "듣다" to "들으세요")
        check(Can, Verb, "먹다" to "먹을 수 있어요", "가다" to "갈 수 있어요", "살다" to "살 수 있어요", "공부하다" to "공부할 수 있어요", "돕다" to "도울 수 있어요")
        check(Promise, Verb, "먹다" to "먹을게요", "가다" to "갈게요", "살다" to "살게요", "공부하다" to "공부할게요", "듣다" to "들을게요")
        check(Whether, Verb, "먹다" to "먹을지", "가다" to "갈지", "살다" to "살지", "공부하다" to "공부할지", "짓다" to "지을지")
        check(TheMore, Verb, "먹다" to "먹을수록", "가다" to "갈수록", "살다" to "살수록", "공부하다" to "공부할수록", "듣다" to "들을수록")
        check(TheMore, Adjective, "춥다" to "추울수록")
        check(WorthIt, Verb, "먹다" to "먹을 만하다", "가다" to "갈 만하다", "살다" to "살 만하다", "공부하다" to "공부할 만하다", "듣다" to "들을 만하다")
        check(Almost, Verb, "먹다" to "먹을 뻔했다", "가다" to "갈 뻔했다", "살다" to "살 뻔했다", "공부하다" to "공부할 뻔했다", "걷다" to "걸을 뻔했다")
        check(FearThat, Verb, "먹다" to "먹을까 봐", "가다" to "갈까 봐", "살다" to "살까 봐", "공부하다" to "공부할까 봐", "듣다" to "들을까 봐")
        check(While, Verb, "먹다" to "먹으면서", "가다" to "가면서", "살다" to "살면서", "공부하다" to "공부하면서", "듣다" to "들으면서")
    }

    @Test
    fun theEndingsThatDependOnTheWordType() { // present only, and only with a sure type
        check(Hearsay, Verb, "먹다" to "먹는대요", "가다" to "간대요", "살다" to "산대요", "공부하다" to "공부한대요", "듣다" to "듣는대요")
        check(Hearsay, Adjective, "작다" to "작대요", "바쁘다" to "바쁘대요", "길다" to "길대요", "피곤하다" to "피곤하대요", "춥다" to "춥대요")
        check(Realize, Verb, "먹다" to "먹는구나", "가다" to "가는구나", "살다" to "사는구나", "공부하다" to "공부하는구나", "듣다" to "듣는구나")
        check(Realize, Adjective, "작다" to "작구나", "바쁘다" to "바쁘구나", "길다" to "길구나", "피곤하다" to "피곤하구나", "그렇다" to "그렇구나")
        check(Background, Verb, "먹다" to "먹는데요", "가다" to "가는데요", "살다" to "사는데요", "공부하다" to "공부하는데요", "듣다" to "듣는데요")
        check(Background, Adjective, "작다" to "작은데요", "바쁘다" to "바쁜데요", "길다" to "긴데요", "피곤하다" to "피곤한데요", "춥다" to "추운데요", "그렇다" to "그런데요")
        check(Seems, Verb, "먹다" to "먹는 것 같다", "가다" to "가는 것 같다", "살다" to "사는 것 같다", "공부하다" to "공부하는 것 같다", "듣다" to "듣는 것 같다")
        check(Seems, Adjective, "작다" to "작은 것 같다", "바쁘다" to "바쁜 것 같다", "길다" to "긴 것 같다", "피곤하다" to "피곤한 것 같다", "춥다" to "추운 것 같다")
        check(During, Verb, "먹다" to "먹는 동안", "가다" to "가는 동안", "살다" to "사는 동안", "공부하다" to "공부하는 동안", "듣다" to "듣는 동안")
        for (e in listOf(Hearsay, Realize, Background, Seems)) assertEquals(emptySet<String>(), conjugate("먹다", e, null))
    }

    @Test
    fun mistakesArePlausibleAndNeverRight() {
        for (e in Ending.entries) for (v in listOf("먹다", "가다", "살다", "듣다", "돕다")) {
            val right = conjugate(v, e, Verb)
            val wrong = commonMistakes(v, e, Verb)
            assertTrue("$v ${e.label}: $wrong", wrong.none { it in right })
        }
        assertTrue("듣어서" in commonMistakes("듣다", SoBecause, Verb)) // the irregular done as a regular
        assertTrue("가아서" in commonMistakes("가다", SoBecause, Verb)) // no contraction
        assertTrue("살니까" in commonMistakes("살다", Because, Verb)) // ㄹ that should drop
    }

    @Test
    fun theWordTypeOfYourNotes() {
        assertEquals(Verb, wordKindOf("가다", "To go"))
        assertEquals(Adjective, wordKindOf("작다", "To be small"))
        assertEquals(null, wordKindOf("가다", "To go / to be gone")) // mixed senses: not sure
        assertEquals(null, wordKindOf("있다", "To be, to exist"))
    }
}
