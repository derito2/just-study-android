package com.dero.juststudykorean.data.quiz

import com.dero.juststudykorean.korean.Candidate
import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.GrammarPoint
import com.dero.juststudykorean.korean.PyRandom
import com.dero.juststudykorean.korean.VocabPair
import com.dero.juststudykorean.korean.buildBlankFromExample
import com.dero.juststudykorean.korean.buildFromPair
import com.dero.juststudykorean.korean.buildGrammar
import com.dero.juststudykorean.korean.extractGrammar
import com.dero.juststudykorean.korean.extractPairs
import com.dero.juststudykorean.korean.grammarItems
import com.dero.juststudykorean.korean.norm

// Port of the rules part of backend/app/quiz/generator.py (pick_chunks, by_turns and _Run). The phone has no
// model in phase 3: the steps that would call it end at once, as when the web's model is off.

val RULE_TYPES = setOf("ko_to_en", "en_to_ko", "vocab_recognition", "write_ko")
val CHOICE_TYPES = setOf("multiple_choice", "fill_blank", "en_to_ko", "vocab_recognition", "grammar_selection")
private val FREE_TYPES = listOf("multiple_choice", "short_answer")
private const val MIN_SOURCE_CHARS = 12
private val hangulWord = Regex("[가-힣]+")
private val vocabWord = Regex("[가-힣]+(?:\\s*/\\s*[가-힣]+)*")

/** One of each document in turns (already shuffled inside each one). */
internal fun <T> byTurns(items: List<T>, key: (T) -> Any): List<T> {
    val groups = LinkedHashMap<Any, ArrayDeque<T>>()
    for (item in items) groups.getOrPut(key(item)) { ArrayDeque() } += item
    val mixed = mutableListOf<T>()
    while (groups.values.any { it.isNotEmpty() }) for (q in groups.values) q.removeFirstOrNull()?.let { mixed += it }
    return mixed
}

/** Chunks with useful material, filtered and shuffled (reproducible with the seed). */
internal fun pickChunks(chunks: List<Chunk>, rng: PyRandom, contentType: String?): List<Chunk> {
    val usable = chunks.filter { it.text.trim().codePointCount(0, it.text.trim().length) >= MIN_SOURCE_CHARS && (contentType == null || it.contentType == contentType) }.toMutableList()
    rng.shuffle(usable)
    return byTurns(usable) { it.documentId }
}

/** One quiz request: work queues per type, in turns. */
internal class RuleRun(
    chunks: List<Chunk>,
    pool: List<Chunk>,
    private val count: Int,
    private val types: List<String>,
    private val pairFilter: ((VocabPair) -> Boolean)?,
) {
    val candidates = mutableListOf<Candidate>()
    private val groups = types.map { if (it in FREE_TYPES) "free" else it }.distinct().toMutableList()
    private val poolPairs = HashMap<Long, MutableList<VocabPair>>()
    private val poolGrammar = HashMap<Long, MutableList<GrammarPoint>>()
    private val grounding: Map<Long, String>
    private val pairItems: List<Pair<Chunk, VocabPair>>
    private val usedPoints = mutableSetOf<Pair<Long, String>>()
    private val usedWords = mutableSetOf<String>()
    private val queues = LinkedHashMap<String, MutableList<Pair<Chunk, VocabPair>>>()
    private val grammarQueues = LinkedHashMap<String, MutableList<Triple<Chunk, GrammarPoint, String>>>()
    /** R-017: (chunk, point, example) for fill_blank by rules; only when fill_blank is asked for. */
    private val blankQueue = mutableListOf<Triple<Chunk, GrammarPoint, String>>()

    init {
        val texts = LinkedHashMap<Long, MutableList<String>>()
        for (c in pool) {
            texts.getOrPut(c.documentId) { mutableListOf() } += c.text
            poolPairs.getOrPut(c.documentId) { mutableListOf() } += extractPairs(c)
            poolGrammar.getOrPut(c.documentId) { mutableListOf() } += extractGrammar(c)
        }
        for (c in chunks) texts.getOrPut(c.documentId) { mutableListOf() }.let { if (c.text !in it) it += c.text }
        grounding = texts.mapValues { it.value.joinToString("\n") }
        pairItems = chunks.flatMap { c -> extractPairs(c).map { c to it } }
        for (g in groups) if (g in RULE_TYPES) queues[g] = pairItems.filter { pairFilter?.invoke(it.second) ?: true }.toMutableList()
        if ("grammar_selection" in groups) grammarQueues["grammar_selection"] = grammarItems("grammar_selection", chunks).toMutableList()
        if ("fill_blank" in groups) for (c in chunks) for (p in extractGrammar(c)) for ((ko, _) in p.examples) blankQueue += Triple(c, p, ko)
        val mc = if ("multiple_choice" in types) grammarItems("multiple_choice", chunks) else emptyList()
        if (mc.isNotEmpty()) {
            groups.add(groups.indexOf("free").takeIf { it >= 0 } ?: groups.size, "grammar_mc")
            grammarQueues["grammar_mc"] = mc.toMutableList()
        }
    }

    fun go(seen: MutableSet<Pair<String, String>>, rng: PyRandom) {
        for (g in groups) if (g in RULE_TYPES) {
            rng.shuffle(queues.getValue(g))
            queues[g] = byTurns(queues.getValue(g)) { it.first.documentId }.toMutableList()
        }
        for (items in grammarQueues.values) rng.shuffle(items)
        if (blankQueue.isNotEmpty()) rng.shuffle(blankQueue)
        val exhausted = mutableSetOf<String>()
        var turn = 0
        while (candidates.size < count && exhausted.size < groups.size) {
            val group = groups[turn % groups.size]
            turn++
            if (group in exhausted) continue
            if (!step(group, seen, rng)) exhausted += group
        }
        if ("multiple_choice" in types) {
            // Multiple choice the model does not write: meaning questions with your words fill it in.
            val onlyFree = types.all { it in FREE_TYPES }
            val share = if (onlyFree) count / types.size else 0
            val mc = candidates.count { it.type == "multiple_choice" }
            val room = count - candidates.size
            val dropped = mutableListOf<Candidate>()
            repeat(maxOf(0, share - mc - room)) {
                val drop = candidates.lastOrNull { it.type != "multiple_choice" } ?: return@repeat
                candidates.remove(drop)
                usedWords -= wordsIn(drop)
                dropped += drop
            }
            if (candidates.size < count) vocabMc(seen, rng)
            for (back in dropped.asReversed()) {
                val words = wordsIn(back)
                if (candidates.size < count && (words intersect usedWords).isEmpty()) { candidates += back; usedWords += words }
            }
        }
    }

    private fun vocabMc(seen: MutableSet<Pair<String, String>>, rng: PyRandom) {
        val items = pairItems.toMutableList()
        rng.shuffle(items)
        val ordered = byTurns(items) { it.first.documentId }.sortedBy { norm(it.second.korean) in usedWords }
        for ((chunk, pair) in ordered) {
            if (candidates.size >= count) return
            if (norm(pair.korean) in usedWords || !vocabWord.matches(pair.korean.trim())) continue
            val built = buildFromPair("vocab_recognition", pair, chunk, poolPairs.getValue(chunk.documentId), grounding.getValue(chunk.documentId), rng)
            val candidate = built.candidate?.copy(type = "multiple_choice") ?: continue
            val before = candidates.size
            accept(candidate, seen)
            if (candidates.size > before) usedWords += norm(pair.korean)
        }
    }

    private fun accept(candidate: Candidate?, seen: MutableSet<Pair<String, String>>) {
        if (candidate == null) return
        val key = norm(candidate.prompt) to norm(candidate.answer)
        if (key in seen) return
        val words = wordsIn(candidate)
        if ((words intersect usedWords).isNotEmpty()) return // the same word of your notes, once per quiz
        if (candidates.any { norm(it.answer) == norm(candidate.answer) && it.type == candidate.type }) return
        seen += key
        candidates += candidate
        usedWords += words
    }

    /** The words of your notes a question asks about (by their Korean in the prompt or the answer). */
    private fun wordsIn(candidate: Candidate): Set<String> {
        val known = poolPairs[candidate.chunk.documentId].orEmpty().flatMap { p -> p.korean.split("/").map(::norm) }.filter { it.isNotEmpty() }.toSet()
        val said = hangulWord.findAll("${candidate.prompt} ${candidate.answer}").map { norm(it.value) }.toSet()
        val inside = known.filter { k -> k.codePointCount(0, k.length) >= 2 && said.any { it.startsWith(k) } }
        return (said intersect known) + inside
    }

    private fun step(group: String, seen: MutableSet<Pair<String, String>>, rng: PyRandom): Boolean = when {
        group in RULE_TYPES -> ruleStep(group, seen, rng)
        group == "grammar_mc" -> grammarStep("multiple_choice", group, seen, rng)
        group == "grammar_selection" && !grammarQueues[group].isNullOrEmpty() -> grammarStep(group, group, seen, rng)
        group == "fill_blank" -> blankStep(seen, rng)
        else -> false // written by the model: none on the phone yet
    }

    private fun ruleStep(group: String, seen: MutableSet<Pair<String, String>>, rng: PyRandom): Boolean {
        val queue = queues.getValue(group)
        if (queue.isEmpty()) return false
        val index = queue.indexOfFirst { norm(it.second.korean) !in usedWords }.takeIf { it >= 0 } ?: 0
        val (chunk, pair) = queue.removeAt(index)
        val built = buildFromPair(group, pair, chunk, poolPairs.getValue(chunk.documentId), grounding.getValue(chunk.documentId), rng)
        val before = candidates.size
        accept(built.candidate, seen)
        if (candidates.size > before) usedWords += norm(pair.korean)
        return true
    }

    /** R-017: a gap in an example of your notes; one per grammar point and quiz. */
    private fun blankStep(seen: MutableSet<Pair<String, String>>, rng: PyRandom): Boolean {
        while (blankQueue.isNotEmpty() && (blankQueue[0].first.documentId to norm(blankQueue[0].second.pattern)) in usedPoints) blankQueue.removeAt(0)
        if (blankQueue.isEmpty()) return false
        val (chunk, point, example) = blankQueue.removeAt(0)
        val built = buildBlankFromExample(point, example, poolGrammar.getValue(chunk.documentId), chunk, grounding.getValue(chunk.documentId), rng)
        if (built.candidate != null) usedPoints += chunk.documentId to norm(point.pattern)
        accept(built.candidate, seen)
        return true
    }

    private fun grammarStep(qtype: String, group: String, seen: MutableSet<Pair<String, String>>, rng: PyRandom): Boolean {
        val queue = grammarQueues[group] ?: return false
        while (queue.isNotEmpty() && (queue[0].first.documentId to norm(queue[0].second.pattern)) in usedPoints) queue.removeAt(0)
        if (queue.isEmpty()) return false
        val (chunk, point, variant) = queue.removeAt(0)
        val built = buildGrammar(qtype, variant, point, chunk, poolGrammar.getValue(chunk.documentId), grounding.getValue(chunk.documentId), rng)
        if (built.candidate != null) usedPoints += chunk.documentId to norm(point.pattern)
        accept(built.candidate, seen)
        return true
    }
}
