package com.dero.juststudykorean.korean.ai

import com.dero.juststudykorean.korean.hasForm
import com.dero.juststudykorean.korean.teacher.patternForms

// Phase 6, task 6: Grammar (use 7: a simpler explanation and a practice sentence).

const val SIMPLER_VERSION = "grammar.simpler.v2"
const val PRACTICE_VERSION = "grammar.practice.v2"

private val HANGUL_WORD = Regex("[가-힣]+")

/** 7a: in plain English for a beginner; Korean only from the point itself. */
fun simplerPrompt(point: String) =
    "Explain this Korean grammar point from the learner's notes to a beginner, more simply, in 2 or 3 short English " +
        "sentences. Keep the rule as the point gives it and every use it lists (if it has two uses, explain both). " +
        "Quote Korean only from the point below; write no other Korean.\n\nGrammar point:\n$point"

fun validSimpler(output: String, point: String): String? =
    output.trim().takeIf { it.length in 10..500 && isEnglish(it, minWords = 5) && koreanOnlyFrom(it, point) }

/** 7b: ONE short sentence with the pattern and only [words] of your notes; no translation (R1-18: it added verbs). */
fun practicePrompt(pattern: String, words: List<String>) =
    "Write ONE short Korean sentence for a beginner that uses the grammar pattern $pattern and only these Korean words " +
        "(you may conjugate them): ${words.joinToString(", ")}. Answer in exactly this format and nothing else:\nKOREAN: <sentence>"

/**
 * The practice sentence if every Korean word is in [notes] (as written, or a conjugation of one of its words) and it
 * uses the pattern; else null (the examples of your notes are shown instead). A translation, if any, is ignored.
 */
fun validPractice(output: String, pattern: String, notes: String): String? {
    val ko = output.lines().firstOrNull { it.trim().startsWith("KOREAN:", true) }?.substringAfter(":")?.trim() ?: return null
    if (ko.isEmpty() || ko.length > 80 || HANGUL_WORD.findAll(ko).none()) return null
    if (!koreanOnlyFrom(ko, "$pattern\n$notes")) return null
    if (patternForms(pattern).filter { it.length >= 1 && HANGUL_WORD.containsMatchIn(it) }.none { hasForm(ko, it) }) return null
    return ko
}
