package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.hangul.jamoDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** FASE_8_PLAN 4.9 and 4.10. Invented notes only. */
class ParticlesAndPairsTest {
    private fun word(k: String, m: String) = GameItem("w:$k", ItemKind.WORD, k, listOf(k), m, k.map { it.toString() }, 1, "d.pdf", 1)
    private val bank = GameBank(listOf(word("학교", "School"), word("책", "Book"), word("물", "Water"), word("집", "House"), word("아이", "Child"), word("가다", "To go"), word("교실", "Classroom")), emptyList())

    private fun spot(sentence: String) = particleSpots(sentence.split(" "), bank)

    @Test
    fun everyPairWithAndWithoutBatchim() {
        assertEquals("를", spot("학교를 가요").single().particle)
        assertEquals("을", spot("책을 읽어요").single().particle)
        assertEquals("는", spot("학교는 커요").single().particle)
        assertEquals("은", spot("책은 커요").single().particle)
        assertEquals("가", spot("학교가 커요").single().particle)
        assertEquals("이", spot("책이 커요").single().particle)
        assertEquals("와", spot("학교와 집").first().particle)
        assertEquals("과", spot("책과 학교").first().particle)
        assertEquals("랑", spot("학교랑 집").first().particle)
        assertEquals("이랑", spot("책이랑 학교").first().particle)
        assertEquals("로", spot("학교로 가요").single().particle)
        assertEquals("으로", spot("집으로 가요").single().particle)
    }

    @Test
    fun rieulTakesRo() {
        assertEquals("로", spot("교실로 가요").single().particle)
        assertEquals("물로 씻어요".let { spot(it).single().particle }, "로")
        assertEquals("으로", PARTICLE_PAIRS.last().wrongAfter("교실"))
    }

    @Test
    fun whatIsNotACandidate() {
        assertTrue(spot("가방을 사요").isEmpty()) // 가방 is not in the bank
        assertTrue(spot("아이 누가 와요").isEmpty()) // 아이 is a word; 누가 is not 누 + 가
        assertTrue(spot("저는 학교을 가요").size == 1) // 저 is a pronoun; 학교을 is already wrong (a typo of the notes)
        assertTrue(spot("빨리 와요").isEmpty())
    }

    @Test
    fun optionsAreTheFixAndWrongFormsOfOtherPairs() {
        val s = spot("학교를 가요").single()
        repeat(50) { seed ->
            val options = particleOptions(s, 2, Random(seed))
            assertEquals(3, options.size)
            assertTrue("를" in options)
            assertTrue("을" !in options) // the wrong form already in the sentence
            assertTrue(options.filter { it != "를" }.all { it in setOf("은", "이", "과", "이랑", "으로") })
        }
        assertEquals("After a vowel: 를. After a consonant: 을.", s.pair.rule)
    }

    @Test
    fun theFixedListHasMinimalPairsWithMeanings() {
        assertEquals(21, FIXED_MINIMAL_GROUPS.size)
        for (group in FIXED_MINIMAL_GROUPS) {
            assertTrue(group.all { it.second.isNotBlank() })
            for (a in group) for (b in group) if (a != b) assertEquals("${a.first}/${b.first}", 1, jamoDistance(a.first, b.first))
        }
    }

    @Test
    fun pairsFromNotesAndWhereTheyDiffer() {
        val notes = GameBank(listOf(word("달", "Moon"), word("딸", "Daughter"), word("물", "Water"), word("말", "Horse")), emptyList())
        val pairs = minimalPairsFromNotes(notes).map { setOf(it.first.korean, it.second.korean) }
        assertTrue(setOf("달", "딸") in pairs && setOf("물", "말") in pairs)
        assertEquals("the first sound", minimalDifference("불", "풀"))
        assertEquals("the vowel", minimalDifference("섬", "솜"))
        assertEquals("the final consonant", minimalDifference("강", "간"))
        assertEquals("the first sound", minimalDifference("도끼", "토끼"))
    }
}
