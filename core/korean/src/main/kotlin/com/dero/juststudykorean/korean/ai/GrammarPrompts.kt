package com.dero.juststudykorean.korean.ai

import com.dero.juststudykorean.korean.hasForm
import com.dero.juststudykorean.korean.teacher.patternForms

// Phase 6, task 6: Grammar (use 7: a simpler explanation and a practice sentence) and Dashboard (use 8).

const val SIMPLER_VERSION = "grammar.simpler.v1"
const val PRACTICE_VERSION = "grammar.practice.v1"
const val WEEK_VERSION = "dashboard.week.v1"

private val HANGUL_WORD = Regex("[가-힣]+")

/** 7a: in plain English for a beginner; Korean only from the point itself. */
fun simplerPrompt(point: String) =
    "Explain this Korean grammar point from the learner's notes to a beginner, more simply, in 2 or 3 short English " +
        "sentences. Quote Korean only from the point below; write no other Korean.\n\nGrammar point:\n$point"

fun validSimpler(output: String, point: String): String? =
    output.trim().takeIf { it.length in 10..500 && isEnglish(it, minWords = 5) && koreanOnlyFrom(it, point) }

/** 7b: ONE short sentence with the pattern and only [words] of your notes, then its translation. */
fun practicePrompt(pattern: String, words: List<String>) =
    "Write ONE short Korean sentence for a beginner that uses the grammar pattern $pattern and only these Korean words " +
        "(you may conjugate them): ${words.joinToString(", ")}. Then give its English translation. Answer in exactly this " +
        "format and nothing else:\nKOREAN: <sentence>\nENGLISH: <translation>"

/**
 * The practice sentence if every Korean word is in [notes] (as written, or a conjugation of one of its words) and it
 * uses the pattern; else null (the examples of your notes are shown instead).
 */
fun validPractice(output: String, pattern: String, notes: String): Pair<String, String>? {
    val ko = output.lines().firstOrNull { it.trim().startsWith("KOREAN:", true) }?.substringAfter(":")?.trim() ?: return null
    val en = output.lines().firstOrNull { it.trim().startsWith("ENGLISH:", true) }?.substringAfter(":")?.trim() ?: return null
    if (ko.isEmpty() || ko.length > 80 || HANGUL_WORD.findAll(ko).none() || !isEnglish(en, minWords = 2) || hasHangul(en)) return null
    if (!koreanOnlyFrom(ko, "$pattern\n$notes")) return null
    if (patternForms(pattern).filter { it.length >= 1 && HANGUL_WORD.containsMatchIn(it) }.none { hasForm(ko, it) }) return null
    return ko to en
}

/** 8: the week's facts (the Dashboard's own figures) and its sentence without the model. */
fun weekFacts(words: Int, accuracy: Int?, answers: Int, weakest: String?): String = buildString {
    append("Words learned this week: $words\nQuiz answers this week: $answers")
    if (accuracy != null) append("\nQuiz accuracy this week: $accuracy%")
    if (weakest != null) append("\nWeakest topic: $weakest")
}

fun weekTemplate(words: Int, accuracy: Int?, weakest: String?): String = buildString {
    append("This week you learned $words ${if (words == 1) "word" else "words"}")
    if (accuracy != null) append(" and got $accuracy% of your quiz answers right")
    append(".")
    if (weakest != null) append(" $weakest needs the most work.")
}

fun weekPrompt(facts: String) =
    "Write one or two short, encouraging English sentences for a Korean learner about their week, using only these " +
        "facts. Do not add any number, Korean word or topic that is not in them.\n\nFacts:\n$facts"
