package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.GameBank
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.RUNNER_FORMS
import com.dero.juststudykorean.korean.games.RUNNER_LEVEL_1
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.RunnerQuestion
import com.dero.juststudykorean.korean.grammar.Ending
import com.dero.juststudykorean.korean.grammar.commonMistakes
import com.dero.juststudykorean.korean.grammar.conjugate
import com.dero.juststudykorean.korean.grammar.endingOf
import com.dero.juststudykorean.korean.grammar.wordKindOf
import kotlin.random.Random

private val LEVEL_2 = RUNNER_LEVEL_1 + listOf(Ending.And, Ending.SoBecause, Ending.If, Ending.Because, Ending.Future, Ending.Want, Ending.Progressive, Ending.Please)

/** Level 1: the three polite endings; 2: the common ones; 3: all. From level 2 on, also the endings of your grammar points. */
internal fun runnerEndings(level: Int, bank: GameBank): List<Ending> = when (level) {
    1 -> RUNNER_LEVEL_1
    2 -> LEVEL_2
    else -> Ending.entries
}.let { base -> if (level == 1) base else (base + bank.points.mapNotNull { endingOf(it.pattern) }).distinct() }

/** The forms of one verb the runner can ask: only verbs and endings `conjugate` knows (F3), with [wrong] wrong forms. */
private fun questions(item: GameItem, endings: List<Ending>, wrong: Int, random: Random): List<RunnerQuestion> {
    val kind = wordKindOf(item.korean, item.meaning)
    return endings.mapNotNull { e ->
        val right = conjugate(item.korean, e, kind)
        // The usual mistakes first; then the same verb with another ending (wrong here, and it looks right).
        val others = endings.filter { it != e }.shuffled(random).flatMap { conjugate(item.korean, it, kind) }
        val bad = (commonMistakes(item.korean, e, kind) + others).filter { it !in right }.distinct()
        if (right.isEmpty() || bad.size < wrong) null
        else RunnerQuestion(item, e, (bad.take(wrong) + right.first()).shuffled(random), right.first())
    }.shuffled(random)
}

/** Verbs of your notes (a word ending in 다) with at least one form to ask. */
internal fun runnerVerbs(bank: GameBank): List<GameItem> = bank.words.filter { it.korean.endsWith("다") && conjugate(it.korean, Ending.PoliteAeo, wordKindOf(it.korean, it.meaning)).isNotEmpty() }

/**
 * F7 (FASE_11_PLAN PR 11): 10 forms of your verbs, one verb after another in the order the chooser prefers (so the same
 * verb never comes twice in a row while others are left). 3 options at level 1, 4 after; they fall in 6, 4.5 or 3.5 s.
 */
val ConjugationRunnerRounds = RoundFactory { ctx ->
    val endings = runnerEndings(ctx.level, ctx.bank)
    val wrong = level(ctx, 2, 3, 3)
    val byVerb = ctx.pick(runnerVerbs(ctx.bank), Int.MAX_VALUE).map { questions(it, endings, wrong, ctx.random) }.filter { it.isNotEmpty() }
    if (byVerb.size < 3) return@RoundFactory null
    val out = mutableListOf<RunnerQuestion>()
    var i = 0
    while (out.size < RUNNER_FORMS && byVerb.any { i < it.size }) {
        for (forms in byVerb) if (i < forms.size && out.size < RUNNER_FORMS) out += forms[i]
        i++
    }
    if (out.size < RUNNER_FORMS) null else RoundSpec.ConjugationRunner(ctx.level, out, level(ctx, 6000, 4500, 3500))
}
