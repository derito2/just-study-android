package com.dero.juststudykorean.korean.grammar

import kotlin.random.Random

// F3 (FASE_11_PLAN PR 5): Grammar Lab's exercises for one point, by level; deterministic with a seed (as Games).

/** A verb or adjective of your notes; [kind] null when its type is not sure (then only endings for both are drilled). */
data class LabVerb(val verb: String, val meaning: String, val kind: WordKind?)

sealed interface Drill {
    val pointKey: String
    val verb: String
    val ending: Ending

    /** "가다 + -아서 → ?", written. */
    data class Produce(override val pointKey: String, override val verb: String, override val ending: Ending, val answers: Set<String>) : Drill

    /** The right form among plausible wrong ones. */
    data class Choose(override val pointKey: String, override val verb: String, override val ending: Ending, val options: List<String>, val answer: String) : Drill

    /** One of YOUR examples of the point with the form gapped; [translation] null if it has none. */
    data class FillExample(
        override val pointKey: String, override val verb: String, override val ending: Ending,
        val before: String, val after: String, val answers: Set<String>, val translation: String?,
    ) : Drill
}

/** Level 1: Choose with regular verbs; 2: Produce; 3: your examples, then Produce with the irregulars first. */
fun drills(pointKey: String, ending: Ending, verbs: List<LabVerb>, examples: List<Pair<String, String>>, count: Int, level: Int, seed: Long): List<Drill> {
    val rng = Random(seed)
    val usable = verbs.distinctBy { it.verb }.mapNotNull { v -> conjugate(v.verb, ending, v.kind).takeIf { it.isNotEmpty() }?.let { v to it } }.shuffled(rng)
    val (regular, irregular) = usable.partition { irregularOf(it.first.verb) == Irregular.None }
    fun produce(p: Pair<LabVerb, Set<String>>) = Drill.Produce(pointKey, p.first.verb, ending, p.second)
    fun choose(p: Pair<LabVerb, Set<String>>): Drill? {
        val wrong = commonMistakes(p.first.verb, ending, p.first.kind).take(3)
        return if (wrong.isEmpty()) null else Drill.Choose(pointKey, p.first.verb, ending, (wrong + p.second.first()).shuffled(rng), p.second.first())
    }
    val fills = examples.flatMap { (ko, en) ->
        usable.mapNotNull { (v, forms) ->
            forms.firstOrNull { it in ko }?.let { f -> val at = ko.indexOf(f); Drill.FillExample(pointKey, v.verb, ending, ko.take(at), ko.drop(at + f.length), forms, en.trim().ifEmpty { null }) }
        }
    }
    val out = when (level) {
        1 -> regular.mapNotNull(::choose)
        2 -> usable.map(::produce)
        else -> fills + irregular.map(::produce) + regular.map(::produce)
    }
    return out.take(count)
}
