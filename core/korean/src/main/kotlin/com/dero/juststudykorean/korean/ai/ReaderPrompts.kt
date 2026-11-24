package com.dero.juststudykorean.korean.ai

// Phase 11 (FASE_11_PLAN PR 3, B5; PLAN_FUNCIONES_ESTUDIO F8): the Reader's "Explain this sentence".

const val SENTENCE_EXPLAIN_VERSION = "reader.sentence.v1"
private const val MAX_SENTENCES = 3
private val SENTENCE_END = Regex("[.!?](?=\\s|$)")

/** The sentence, its translation from your notes (if any) and the glosses found for its words ("word = meaning"). */
fun sentenceExplainPrompt(sentence: String, translation: String?, glosses: List<String>) = buildString {
    append("Explain in English the grammar and word order of this Korean sentence for a learner, in at most 3 short sentences. ")
    append("Quote Korean only from the sentence itself; write no other Korean.\n\nSentence: ").append(sentence)
    if (translation != null) append("\nTranslation: ").append(translation)
    if (glosses.isNotEmpty()) append("\nWords from the learner's notes:\n").append(glosses.joinToString("\n"))
}

/** English, Korean only from [sentence], at most 3 sentences and 600 characters; else null (the glosses are shown instead). */
fun validSentenceExplain(output: String, sentence: String): String? = output.trim().takeIf {
    it.length in 10..600 && isEnglish(it, minWords = 5) && koreanOnlyFrom(it, sentence) && SENTENCE_END.findAll(it).count() <= MAX_SENTENCES
}
