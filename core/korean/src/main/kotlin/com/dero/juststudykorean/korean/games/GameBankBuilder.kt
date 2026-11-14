package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.extractGrammar
import com.dero.juststudykorean.korean.extractPairs
import com.dero.juststudykorean.korean.norm

private const val MAX_WORD_BLOCKS = 6
private val SENTENCE_WORDS = 2..8

/**
 * FASE_8_PLAN 4.1: the chunks of your ready documents (in upload order) → the bank. A word repeated in several
 * documents keeps the first one's meaning and place (as mergedWords) and gathers the examples and notes of all (0b).
 * Sentences: pairs of 2–8 words, and every example with a translation of a word or of a grammar point.
 */
fun buildGameBank(chunks: List<Chunk>): GameBank {
    val ordered = chunks.sortedWith(compareBy({ it.documentId }, { it.pageNumber }, { it.index }))
    val words = LinkedHashMap<String, GameItem>()
    val sentences = LinkedHashMap<String, GameItem>()
    val points = LinkedHashMap<String, GamePoint>()

    fun addSentence(korean: String, meaning: String, chunk: Chunk) {
        val text = korean.trim()
        if (meaning.isBlank() || !usableSentence(text) || text.split(Regex("\\s+")).size !in SENTENCE_WORDS) return
        val key = "s:" + norm(text)
        if (key !in sentences) sentences[key] = GameItem(key, ItemKind.SENTENCE, text, listOf(text), meaning.trim(), syllables(text), chunk.documentId, chunk.filename, chunk.pageNumber)
    }

    for (chunk in ordered) {
        for (pair in extractPairs(chunk)) {
            val variants = pair.korean.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            val isWord = pair.english.isNotBlank() && variants.isNotEmpty() &&
                variants.all { HANGUL_ONLY.matches(it) && it.length in 1..MAX_WORD_BLOCKS }
            if (isWord) {
                val key = "w:" + norm(pair.korean)
                val seen = words[key]
                words[key] = seen?.copy(examples = (seen.examples + pair.examples).distinct(), notes = (seen.notes + pair.notes).distinct())
                    ?: GameItem(key, ItemKind.WORD, pair.korean, variants, pair.english, syllables(variants[0]), chunk.documentId, chunk.filename, chunk.pageNumber, pair.examples.distinct(), pair.notes.distinct())
            } else {
                addSentence(pair.korean, pair.english, chunk)
            }
            for ((ko, en) in pair.examples) addSentence(ko, en, chunk)
        }
        for (point in extractGrammar(chunk)) {
            val key = "g:" + norm(point.pattern)
            if (key !in points) points[key] = GamePoint(key, point.pattern, point.meaning, point.forms, point.explanation, point.examples, chunk.documentId, chunk.filename, chunk.pageNumber)
            for ((ko, en) in point.translated) addSentence(ko, en, chunk)
        }
    }
    return GameBank(words.values.toList(), sentences.values.toList(), points.values.toList())
}
