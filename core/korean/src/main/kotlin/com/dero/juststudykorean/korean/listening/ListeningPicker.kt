package com.dero.juststudykorean.korean.listening

// F4 (FASE_11_PLAN PR 7; PLAN_FUNCIONES_ESTUDIO F4): what each mode of Listening Lab plays.

enum class ListenMode { Dictation, Shadowing, HandsFree }

/** A sentence (or, hands-free, a card's word) of your notes with its meaning. */
data class LabSentence(val key: String, val korean: String, val meaning: String)

private const val MIN_WORDS = 2
private const val MAX_WORDS = 8
/** The recognizer fails more on long sentences (phase 9 PoC, docs/poc/4-speech.md). */
private const val SHADOWING_SYLLABLES = 20

fun syllables(text: String) = text.count { it in '가'..'힣' }

private fun words(text: String) = text.trim().split(Regex("\\s+")).count { syllables(it) > 0 }

/** Dictation: sentences of 2 to 8 words with a translation. */
fun dictation(sentences: List<LabSentence>) = sentences.filter { it.meaning.isNotBlank() && words(it.korean) in MIN_WORDS..MAX_WORDS }

/** Shadowing: short sentences, 2 words and 20 syllables at most. */
fun shadowing(sentences: List<LabSentence>) = sentences.filter { it.meaning.isNotBlank() && words(it.korean) >= MIN_WORDS && syllables(it.korean) <= SHADOWING_SYLLABLES }

/** The sentences with one of [keys] (word_state keys, or a failed dictation's sentence), also conjugated: 마셨어요 has 마시다. */
fun withWords(items: List<LabSentence>, keys: List<String>) = items.filter { s -> keys.any { k -> k.isNotBlank() && com.dero.juststudykorean.korean.hasForm(s.korean, k.replace(" ", "")) } }

/** Why [mode] can't be used now (design/beta.md 4, empty states); null if it can. */
fun unavailable(mode: ListenMode, sentences: List<LabSentence>, dueCards: Int, voiceReady: Boolean): String? = when {
    !voiceReady -> "Needs the phone's Korean voice."
    mode == ListenMode.HandsFree -> if (dueCards == 0) "No cards are due right now." else null
    (if (mode == ListenMode.Dictation) dictation(sentences) else shadowing(sentences)).isEmpty() -> "Add sentences with a translation to your notes."
    else -> null
}
