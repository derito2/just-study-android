package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.Golden
import com.dero.juststudykorean.korean.games.rounds.ConjugationRunnerRounds
import com.dero.juststudykorean.korean.grammar.Applies
import com.dero.juststudykorean.korean.grammar.Ending
import com.dero.juststudykorean.korean.grammar.conjugate
import com.dero.juststudykorean.korean.grammar.wordKindOf
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.korean.objects
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * FASE_11_PLAN PR 11 (F7): a word of the golden base whose type is not sure (wordKindOf null) never gets, in Conjugation
 * Runner, an ending of one type only (Applies.Verb) or one that changes with the type (Applies.TypeDependent). Each unsure
 * word plays with three sure verbs, so the round robin always reaches it; 3 levels × 5 seeds, with every golden grammar point.
 */
class RunnerWordTypeTest {
    private fun item(k: String, m: String) = GameItem("w:${norm(k)}", ItemKind.WORD, k, k.split(" / "), m, k.split(" / ")[0].map { it.toString() }, 1, "golden.pdf", 1)

    private val words: List<Pair<String, String>> =
        (Golden.arr("db/library_vocabulary.json").objects().map { it.getString("korean") to it.getString("english") } +
            Golden.files("pairs").flatMap { f -> JSONArray(f.readText()).objects().flatMap { c -> c.getJSONArray("pairs").objects() } }
                .map { it.getString("korean") to it.getString("english") }).distinct()

    private val points: List<GamePoint> = Golden.files("grammar").flatMap { f -> JSONArray(f.readText()).objects().flatMap { c -> c.getJSONArray("points").objects() } }
        .mapIndexed { i, p -> GamePoint("g:$i", p.getString("pattern"), "", emptyList(), null, emptyList(), 1, "golden.pdf", 1) }

    private val unsure = words.filter { (k, m) -> k.endsWith("다") && wordKindOf(k, m) == null }
    private val sure = words.filter { (k, m) -> wordKindOf(k, m) != null && conjugate(k, Ending.PoliteAeo, wordKindOf(k, m)).isNotEmpty() }.take(3)
    private val oneTypeOrDependent = Ending.entries.filter { it.applies != Applies.Both }

    @Test
    fun unsureWordsNeverGetAnEndingOfOneTypeOrThatDependsOnIt() {
        assertTrue("the golden base has unsure words: $unsure", unsure.isNotEmpty())
        assertTrue("three sure verbs to fill the round", sure.size == 3)
        var reached = 0
        for ((k, m) in unsure) {
            val bank = GameBank((sure + (k to m)).map { (a, b) -> item(a, b) }, emptyList(), points)
            for (level in 1..3) for (seed in 0 until 5) {
                val spec = ConjugationRunnerRounds.build(RoundContext(bank, level, Random(seed))) as RoundSpec.ConjugationRunner? ?: continue
                for (q in spec.questions.filter { it.item.korean == k }) {
                    reached++
                    if (q.ending in oneTypeOrDependent) fail("$k ($m), level $level, seed $seed: ${q.ending} (${q.ending.applies})")
                }
            }
        }
        assertTrue("the unsure words were asked at all", reached > 0)
        println("unsure words: ${unsure.map { it.first }}; their questions checked: $reached")
    }
}
