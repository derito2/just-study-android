package com.dero.juststudykorean.korean.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F3 (FASE_11_PLAN PR 5): the drills of a point, the same for the same seed; nothing that can't be checked. */
class DrillsTest {
    private val verbs = listOf(
        LabVerb("먹다", "To eat", WordKind.Verb), LabVerb("가다", "To go", WordKind.Verb), LabVerb("살다", "To live", WordKind.Verb),
        LabVerb("공부하다", "To study", WordKind.Verb), LabVerb("듣다", "To listen", WordKind.Verb), LabVerb("돕다", "To help", WordKind.Verb),
        LabVerb("묻다", "To ask", WordKind.Verb), // ambiguous: never drilled
        LabVerb("춥다", "To be cold", WordKind.Adjective), LabVerb("보다", "To see / to be seen", null),
    )
    private val examples = listOf("배가 고파서 밥을 먹어서 기뻐요." to "", "학교에 가서 공부해요." to "I go to school and study.")

    private fun all(level: Int, seed: Long = 7) = drills("아서어서", Ending.SoBecause, verbs, examples, count = 10, level = level, seed = seed)

    @Test
    fun theSameSeedGivesTheSameDrills() {
        for (level in 1..3) assertEquals(all(level), all(level))
        assertTrue((1L..10L).map { all(2, it) }.toSet().size > 1) // and another seed, others
    }

    @Test
    fun everyAnswerCanBeChecked() {
        for (level in 1..3) for (d in all(level)) {
            assertTrue(d.verb != "묻다")
            when (d) {
                is Drill.Produce -> assertTrue(d.answers.isNotEmpty())
                is Drill.FillExample -> assertTrue(d.answers.isNotEmpty() && d.answers.any { (d.before + it + d.after) in examples.map { e -> e.first } })
                is Drill.Choose -> {
                    assertEquals(1, d.options.count { it == d.answer })
                    assertEquals(d.options.size, d.options.toSet().size)
                    assertTrue(d.options.size >= 2)
                }
            }
        }
    }

    @Test
    fun eachLevelAsksItsWay() {
        assertTrue(all(1).all { it is Drill.Choose && irregularOf(it.verb) == Irregular.None }) // 1: choose, regular verbs
        assertTrue(all(2).all { it is Drill.Produce })
        assertTrue(all(3).any { it is Drill.FillExample }) // 3: your examples, and the irregulars
        assertTrue(all(3).filterIsInstance<Drill.Produce>().any { irregularOf(it.verb) != Irregular.None })
    }

    @Test
    fun aVerbOnlyEndingSkipsWordsOfUnsureType() {
        val d = drills("ㄹ게요", Ending.Promise, verbs, emptyList(), count = 20, level = 2, seed = 1)
        assertTrue(d.none { it.verb == "보다" || it.verb == "춥다" })
    }

    @Test
    fun explainSaysWhichRuleWasUsed() {
        assertEquals("가다 + -아서/어서 → 가서: 가 + 아/어 contracts → 가.", explain("가다", Ending.SoBecause, WordKind.Verb))
        assertTrue(explain("듣다", Ending.SoBecause, WordKind.Verb)!!.contains("ㄷ-irregular"))
        assertTrue(explain("살다", Ending.Because, WordKind.Verb)!!.contains("drops before"))
        assertEquals(null, explain("묻다", Ending.SoBecause, WordKind.Verb))
    }
}
