package com.dero.juststudykorean.korean

// Port of backend/app/quiz/pairs.py (extract_pairs), checked against golden/pairs/*.json.
// Named VocabPair (the web's Pair) so it does not hide kotlin.Pair in this package.

private val SYNONYM_SLASH = py("\\s*/\\s*")

data class VocabPair(
    val korean: String,
    val english: String,
    val romanization: String? = null,
    val exampleKo: String? = null,
    val exampleEn: String? = null,
    val source: String = "rules",
    /** Everything below the entry in the notes: examples (Korean, English or "") and notes. */
    val examples: List<Pair<String, String>> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** Synonyms always as "가장 / 제일" (so any of them is accepted when typing). */
private fun headword(korean: String) = koreanVariants(korean.pyStrip().replace(SYNONYM_SLASH, "/")).joinToString(" / ")

/** Vocabulary and sentence pairs of a chunk. Grammar points are not pairs. */
fun extractPairs(chunk: Chunk): List<VocabPair> {
    val lines = chunk.text.pySplitlines()
    val section = (chunk.section ?: "").pyStrip()
    var body = lines.filter { it.pyStrip().isNotEmpty() }.map { it.pyStrip() }
    if (section.isNotEmpty() && body.isNotEmpty() && nfc(body[0]) == nfc(section)) body = body.drop(1)
    if (isGrammarEntry(chunk.section, body) || isParticleTable(body)) return emptyList()
    if (grammarTitleLine(section)) body = listOf(section) + body // its examples belong to that grammar point
    val pairs = linkedMapOf<String, VocabPair>()
    for (item in parseLines(body)) {
        if (item.grammar) continue
        val ko = headword(item.korean)
        val (exampleKo, exampleEn) = item.example
        pairs.putIfAbsent(norm(ko), VocabPair(ko, item.english, item.romanization, exampleKo, exampleEn, examples = item.examples.toList(), notes = item.notes.toList()))
    }
    return pairs.values.toList()
}
