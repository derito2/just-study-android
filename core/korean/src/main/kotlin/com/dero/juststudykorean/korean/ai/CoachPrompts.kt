package com.dero.juststudykorean.korean.ai

import com.dero.juststudykorean.korean.casefold

// Phase 6, task 4 (use 5): the coach's text, written by the model from the facts the rules computed (CoachEngine). The
// proposals (Start quiz, Create deck) stay the rules'. Also use 8 (Dashboard) checks its sentences the same way.

const val COACH_AI_VERSION = "coach.nano.v3"

private val NUMBER = Regex("\\d+(?:[.,]\\d+)?")

fun coachPrompt(facts: String) =
    "You are a friendly Korean study coach. Rewrite the study plan below for the learner in 2 to 4 short English " +
        "sentences, talking to them as \"you\", in natural, simple English, as a teacher would say it (not a list of " +
        "facts). Be warm, never scolding. Do not mention buttons or quote screen text: say \"the quiz below\" or \"the " +
        "deck below\". Use only these facts: do not add any number, Korean " +
        "word or topic that is not in them, and keep the ones you use exactly as written.\n\nFacts:\n$facts"

/**
 * The model's text if it only uses the facts: English, every number and Korean word from [facts], and no topic of
 * [topics] (all your topic names) that the facts don't mention. Else null (the template).
 */
fun validFromFacts(output: String, facts: String, topics: Collection<String>, maxChars: Int = 600): String? {
    val text = output.trim()
    if (text.length !in 10..maxChars || !isEnglish(text, minWords = 4)) return null
    val known = NUMBER.findAll(facts).map { it.value }.toSet()
    if (NUMBER.findAll(text).any { it.value !in known }) return null
    if (!koreanOnlyFrom(text, facts)) return null
    val said = casefold(text)
    val inFacts = casefold(facts)
    if (topics.any { t -> t.length >= 3 && casefold(t) in said && casefold(t) !in inFacts }) return null
    return text
}
