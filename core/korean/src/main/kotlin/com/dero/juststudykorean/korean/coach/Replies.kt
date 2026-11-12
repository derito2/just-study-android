package com.dero.juststudykorean.korean.coach

import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.englishSenses
import com.dero.juststudykorean.korean.extractGrammar
import com.dero.juststudykorean.korean.extractPairs
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.korean.py
import com.dero.juststudykorean.korean.pySplit
import com.dero.juststudykorean.korean.teacher.hasHangul
import com.dero.juststudykorean.korean.teacher.variants

// Port of the deterministic texts of backend/app/agent/loop.py: what the code writes when the coach answers without
// the model (_forced_reply, _proposal, drop_missing_buttons, word_meaning, grammar_topic, _fallback).

/** The quiz types of a topic quiz (quiz.types.DEFAULT_TYPES), in the web's order. */
val DEFAULT_TYPES = listOf("ko_to_en", "write_ko", "vocab_recognition", "en_to_ko", "fill_blank", "multiple_choice", "grammar_selection")
val GRAMMAR_QUIZ_TYPES = listOf("grammar_selection", "multiple_choice")

/** A tool the coach ran, for "How I decided". */
data class Step(val tool: String, val arguments: Map<String, Any?>, val ok: Boolean, val summary: String)

/** The "Start quiz" card: a topic, or the words of a made-to-measure quiz. */
data class QuizProposal(val topic: String?, val title: String?, val words: List<String>?, val count: Int, val types: List<String>)

/** The "Create deck" card: a NEW deck with these words of your notes. */
data class DeckProposal(val name: String, val words: List<String>)

data class CoachAnswer(val text: String, val steps: List<Step>, val quiz: QuizProposal?, val deck: DeckProposal?, val fallback: Boolean)

private val NUMBERING = py("^\\s*\\d{1,3}[.)]\\s+")

/** A topic's name to read: without the PDF's numbering ("3. (으)ㄹ 만하다"). */
fun shown(topic: String): String = topic.replaceFirst(NUMBERING, "").ifEmpty { topic }

fun proposal(topic: String) = QuizProposal(topic, if (shown(topic) != topic) shown(topic) else null, null, 5, DEFAULT_TYPES)

/** Quick buttons for made-to-measure material: the tool directly and a reply the code writes. */
val FORCED: Map<String, Map<String, Any?>> = mapOf(
    "quiz on my mistakes" to linkedMapOf("kind" to "quiz", "source" to "mistakes"),
    "deck from my mistakes" to linkedMapOf("kind" to "deck", "source" to "mistakes"),
)

private val SENTENCE = py("(?<=[.!?])\\s+")
private val MENTIONS_DECK = py("\\bcreate (?:the |a )?deck\\b", ignoreCase = true)
private val MENTIONS_QUIZ = py("\\bstart (?:the |a )?quiz\\b", ignoreCase = true)
private val MENTIONS_BUTTON = py("\\bbuttons?\\b", ignoreCase = true)

/** Drops the sentences that send you to press a button that won't be there (for the model's reply, phase 6). */
fun dropMissingButtons(text: String, hasQuiz: Boolean, hasDeck: Boolean): String = text.trim().split(SENTENCE).filterNot { s ->
    (MENTIONS_DECK.containsMatchIn(s) && !hasDeck) || (MENTIONS_QUIZ.containsMatchIn(s) && !hasQuiz) ||
        (MENTIONS_BUTTON.containsMatchIn(s) && !(hasQuiz || hasDeck))
}.joinToString(" ")

/** The result of make_practice: the words chosen, or why there are none. */
data class Practice(val kind: String, val title: String, val words: List<Word>, val error: String? = null, val theme: String? = null) {
    val ok get() = error == null
}

/** The reply the code writes for made-to-measure material (no model). */
fun forcedReply(arguments: Map<String, Any?>, output: Practice): String {
    val source = arguments["source"] as String
    val value = arguments["value"] as String?
    if (output.ok) {
        val words = output.words
        val sample = words.take(5).joinToString(", ") { it.korean }
        val more = if (words.size > 5) "…" else ""
        val what = if (output.kind == "quiz") "a quiz" else "a new flashcard deck"
        val button = if (output.kind == "quiz") "Start quiz" else "Create deck"
        val noun = if (words.size == 1) "word" else "words"
        val about = if (source == "mistakes") "$noun you keep getting wrong" else "$noun from your notes (${output.title})"
        val tail = if (output.kind != "quiz") " Your other decks stay exactly as they are." else ""
        return "Here is $what with ${words.size} $about: $sample$more. Press $button.$tail"
    }
    return when {
        source == "mistakes" -> "You have no recent mistakes to practise. Take a quiz or review some flashcards first, then come back."
        source == "recent" -> "You haven't reviewed any flashcards${mapOf("today" to " today", "week" to " this week")[value ?: ""] ?: ""} yet. " +
            "Open a deck in Flashcards, then ask me again."
        source == "word_type" -> "Your notes don't have any ${value}s I can find."
        source == "letter" && !isLetter(value) -> "Give me one Korean consonant, such as ㄱ, ㄴ or ㅎ."
        else -> {
            val what = if (source == "letter") "that start with $value" else "about ${output.theme ?: value}"
            "Your notes don't have words $what. I only use words from your own notes: upload a PDF with that vocabulary in the Library, then ask me again."
        }
    }
}

/** "What does 맛있다 mean?" in the coach: the entry of your notes, without the model. */
fun wordMeaning(chunks: List<Chunk>, word: String, examples: Boolean = false): String {
    val wanted = variants(word)
    val korean = wanted.filter(::hasHangul).map(::norm).toSet()
    val english = wanted.filterNot(::hasHangul).flatMap(::englishSenses).toSet()
    val found = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (chunk in chunks) {
        for (pair in extractPairs(chunk)) {
            val heads = pair.korean.split(" / ").map(::norm).toSet()
            if (heads.none { it in korean } && !(english.isNotEmpty() && englishSenses(pair.english).any { it in english })) continue
            if (!seen.add(norm(pair.korean))) continue
            var line = "${pair.korean} = ${pair.english}"
            if (examples && pair.examples.isNotEmpty()) {
                line += ". Examples: " + pair.examples.take(3).joinToString("; ") { (ko, en) -> if (en.isNotEmpty()) "$ko = $en" else ko }
            } else if (!pair.exampleKo.isNullOrEmpty()) {
                line += ". Example: " + pair.exampleKo + (pair.exampleEn?.takeIf { it.isNotEmpty() }?.let { " = $it" } ?: "")
            }
            found += "$line (${chunk.filename}, p. ${chunk.pageNumber})"
        }
        for (point in extractGrammar(chunk)) {
            if (norm(point.pattern) in korean && seen.add(norm(point.pattern))) found += "${point.pattern} = ${point.meaning} (${chunk.filename})"
        }
        if (found.size >= 3) break
    }
    if (found.isNotEmpty()) return "From your notes:\n" + found.joinToString("\n")
    // Not an entry, but maybe in a sentence of your notes: better that than "not there".
    val tokens = norm(word).pySplit().filter { it.length >= 3 || hasHangul(it) }
    val uses = mutableListOf<String>()
    for (chunk in chunks) for (pair in extractPairs(chunk)) {
        for ((ko, en) in listOf(pair.korean to pair.english) + pair.examples) {
            val text = norm("$ko $en")
            if (tokens.isNotEmpty() && tokens.all { it in text } && uses.size < 3) {
                val line = if (en.isNotEmpty()) "$ko = $en" else ko
                if (line !in uses) uses += line
            }
        }
    }
    if (uses.isNotEmpty()) return "“$word” isn't a word entry in your notes, but they use it here:\n" + uses.joinToString("\n")
    return "“$word” isn't in your notes. Add it to a PDF in the Library, or ask Teacher to explain it."
}

/** "Quiz me on -네요", "Practice -(으)ㄴ/는데(요)": a grammar point of your notes. */
val GRAMMAR_QUIZ = py(
    "\\b(?:quiz|test|practi[cs]e|drill)\\b.*?\\b(?:on|about|with)?\\s*((?:-|~|\\(으\\))\\s*[\\(가-힣ㄱ-ㆎ][^?.!]*?)\\s*[?.!]*$", ignoreCase = true,
)

/** (topic, pattern as written) of the grammar point of your notes you ask for, or null. */
fun grammarTopic(chunks: List<Chunk>, pattern: String): Pair<String, String>? {
    val wanted = norm(pattern)
    for (exact in listOf(true, false)) for (chunk in chunks) for (point in extractGrammar(chunk)) {
        if (point.kind != "pattern") continue
        val have = norm(point.pattern)
        if (have == wanted || (!exact && wanted.isNotEmpty() && wanted in have)) return topicName(chunk) to point.pattern
    }
    return null
}

private val MINUTES = py("(\\d+)\\s*(?:minutes?|mins?)\\b", ignoreCase = true)

/** A recommended topic, as the fallback plan reads it. */
data class PlanTopic(val name: String, val priority: String, val reasons: List<String>)

/** The deterministic plan: the first recommended topic and a quiz on it; with "N minutes", the time split. */
fun fallbackPlan(recs: List<PlanTopic>, message: String, due: Int, new: Int): String {
    if (recs.isEmpty()) {
        return "I have nothing to work with: you haven't given me any data. Upload and process a document, then take a quiz or create flashcards."
    }
    val top = recs[0]
    val lines = mutableListOf("Stop avoiding it: study ${shown(top.name)} next (${top.priority} priority: ${top.reasons.joinToString("; ")}).")
    if (recs.size > 1) lines += "After that, these need work too: ${recs.drop(1).joinToString(", ") { shown(it.name) }}."
    val minutes = MINUTES.findAll(message).maxOfOrNull { it.groupValues[1].toInt() } ?: 0
    if (minutes >= 4) {
        val quizMin = if (due > 0 || new > 0) minutes / 2 else minutes
        var split = "$quizMin minutes on that quiz"
        if (due > 0) split += " and ${minutes - quizMin} minutes on your $due due ${if (due == 1) "card" else "cards"}"
        else if (new > 0) split += " and ${minutes - quizMin} minutes learning new flashcards (you have $new you haven't studied yet)"
        lines += "With $minutes minutes: $split."
    }
    lines += "Start the quiz with the button. Now, not later."
    return lines.joinToString(" ")
}
