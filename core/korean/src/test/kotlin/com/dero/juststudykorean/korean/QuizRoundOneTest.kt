package com.dero.juststudykorean.korean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA round 1, R1-09 (design/quiz.md A.4): no option stands out by its dash. Invented notes only. */
class QuizRoundOneTest {
    private val chunk = Chunk(1, "grammar.pdf", 1, 0, "Grammar", "Grammar", "grammar", "ko-en", "")
    private val pool = listOf(
        GrammarPoint("pattern", "-거든(요)", "because (a reason)"),
        GrammarPoint("pattern", "ㄹ수록", "the more"),
        GrammarPoint("pattern", "--지(요), -죠", "right?"),
        GrammarPoint("pattern", "~아서/어서", "so, and then"),
    )
    private val grounding = pool.joinToString("\n") { "${it.pattern} = ${it.meaning}" }

    @Test
    fun everyGrammarOptionHasOneDashWhenTheAnswerHasOne() {
        val built = buildGrammar("grammar_selection", "pick_form", pool[0], chunk, pool, grounding, PyRandom(3)).candidate!!
        assertEquals("-거든(요)", built.answer)
        assertTrue(built.options.toString(), built.options!!.all { it.startsWith("-") && !it.startsWith("--") && !it.startsWith("-~") })
        assertTrue(built.options.toString(), "-ㄹ수록" in built.options!! && "-지(요), -죠" in built.options!!)
    }

    @Test
    fun noOptionHasADashWhenTheAnswerHasNone() {
        val built = buildGrammar("grammar_selection", "pick_form", pool[1], chunk, pool, grounding, PyRandom(3)).candidate!!
        assertEquals("ㄹ수록", built.answer)
        assertTrue(built.options.toString(), built.options!!.none { it.startsWith("-") || it.startsWith("~") })
    }
}
