package com.dero.juststudykorean.korean.coach

import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.GENERAL_TOPIC
import com.dero.juststudykorean.korean.casefold
import com.dero.juststudykorean.korean.cleanTopic
import com.dero.juststudykorean.korean.extractPairs
import com.dero.juststudykorean.korean.initialConsonant
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.korean.py
import com.dero.juststudykorean.korean.pySplit
import com.dero.juststudykorean.korean.teacher.keywords

// Port of backend/app/agent/practice.py: words of YOUR notes for a quiz or a deck (by your mistakes, a theme, a letter,
// a topic, your latest words or a word type), what a message asks for (intent, planned_tools) — all without the model.
// The themes the web resolved with embeddings or the model are left to phase 6 (by_theme keeps only the rules).

data class Word(val korean: String, val english: String, val topic: String, val chunkId: String)

/** The topic of a chunk: its section, or the document's name for "General" chunks (quiz_service._topic_name). */
fun topicName(chunk: Chunk): String = if (chunk.topic != GENERAL_TOPIC) chunk.topic else cleanTopic(chunk.filename.substringBeforeLast("."))

/** Every word (Korean-English pair) of your notes, once. */
fun noteWords(chunks: List<Chunk>): List<Word> {
    val seen = mutableSetOf<String>()
    return chunks.flatMap { chunk -> extractPairs(chunk).map { chunk to it } }.mapNotNull { (chunk, pair) ->
        val key = norm(pair.korean)
        if (key.isNotEmpty() && seen.add(key)) Word(pair.korean, pair.english, topicName(chunk), chunk.chromaId) else null
    }
}

fun uniqueWords(words: List<Word>): List<Word> = words.distinctBy { norm(it.korean) }

fun byLetter(words: List<Word>, letter: String) = words.filter { initialConsonant(it.korean) == letter }

fun byTopic(words: List<Word>, topic: String) = words.filter { casefold(it.topic) == casefold(topic) }

private val FILLER = "like such as e.g etc stuff things related kind sort".split(" ").toSet()
private val SUFFIXES = listOf("ments", "ment", "ies", "es", "s", "ing", "y")
private val ARTICLES = setOf("a", "an", "the", "to")
private val CUT = py("[,;/(]")

/** "movements" finds "to move" and "movement"; under 4 letters, the whole word ("art" does not find "article"). */
private fun meaningPattern(key: String): Regex {
    val stem = SUFFIXES.firstOrNull { key.endsWith(it) && key.length - it.length >= 4 }?.let { key.dropLast(it.length) } ?: key
    return if (stem.length < 4) py("\\b${Regex.escape(key)}\\b") else py("\\b${Regex.escape(stem)}")
}

/** A word or short expression ("Price tag"), not an example sentence: at most 2 content words. */
private fun isVocabulary(word: Word) = casefold(word.english.split(CUT)[0]).pySplit().count { it !in ARTICLES } <= 2

/**
 * Words of your notes about a theme: those that say it in their meaning, and for a common theme (colors, family…)
 * those its fixed list recognizes. The web then asks the embeddings (phase 6); without them, only these.
 */
fun byTheme(wordsIn: List<Word>, theme: String): List<Word> {
    val words = wordsIn.filter(::isVocabulary)
    val keys = keywords(theme).map(::casefold).filter { it !in FILLER }.ifEmpty { listOf(casefold(theme).trim()) }
    val patterns = keys.map(::meaningPattern)
    val byMeaning = words.filter { w -> patterns.any { it.containsMatchIn(casefold(w.english)) } }
    val category = categoryOf(theme) ?: return byMeaning // shortcut: other themes need the embeddings (phase 6)
    return uniqueWords(byMeaning + words.filter { belongs(category, it.english) })
}

val RECENT_WINDOWS = mapOf("today" to 1L, "week" to 7L) // days

val WORD_TYPES = listOf("adjective", "verb", "noun")

/** By the English meaning: "To be big" = adjective, "To eat" = verb; no 다 = noun; null if the rules can't tell. */
fun wordType(word: Word): String? {
    val meaning = casefold(word.english.split(CUT)[0].trim())
    return when {
        meaning.startsWith("to be ") -> "adjective"
        meaning.startsWith("to ") -> "verb"
        !norm(word.korean).endsWith("다") -> "noun"
        else -> null
    }
}

/** The words of that type the rules are sure of (the web asks the model about the rest: phase 6). */
fun byWordType(words: List<Word>, kind: String) = uniqueWords(words.filter(::isVocabulary).filter { wordType(it) == kind })

private const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

fun isLetter(value: String?): Boolean = value != null && value.trim().length == 1 && value.trim() in CHOSEONG

// ---------- made-to-measure requests in plain language (without the model) ----------

data class Intent(val kind: String, val source: String, val value: String?, val count: Int? = null)

private val DECK = py("\\b(decks?|flash ?cards?|cards)\\b", ignoreCase = true)
private val QUIZ = py("\\b(quiz+(?:es|zes)?|test me|questions?)\\b", ignoreCase = true)
private val PRACTICE = py("\\b(words|vocabulary|vocab|practi[cs]e|review|learn|study)\\b", ignoreCase = true)
private val MISTAKES = py("\\b(mistakes?|wrong|errors?|fail(?:ed)?|missed)\\b", ignoreCase = true)
private val LETTER = py(
    "(?:start|begin)(?:s|ing)?\\s+with\\s+(?:the\\s+)?(?:letter\\s+|consonant\\s+)?([ㄱ-ㅎ])|\\b(?:letter|consonant)\\s+([ㄱ-ㅎ])", ignoreCase = true,
)
private val RECENT = py(
    "\\b(latest|lastest|lates|recent(?:ly)?|last|newest|lately)\\b|\\b(?:studied|reviewed|learned|learnt|saw)\\b.*\\b(today|this week|recently|lately)\\b",
    ignoreCase = true,
)
private val TODAY = py("\\btoday\\b", ignoreCase = true)
private val WEEK = py("\\b(this week|last 7 days|past week)\\b", ignoreCase = true)
private val WORD_TYPE = py("\\b(adjectives?|verbs?|nouns?)\\b", ignoreCase = true)
private val PREPOSITION = py("\\b(?:about|on|of|for|with)\\s+", ignoreCase = true)
private val X_WORDS = py("\\b([a-z][a-z\\-]{2,20})\\s+(?:words|vocabulary|vocab)\\b", ignoreCase = true)
private val X_FIRST = py("^(?:a\\s+|an\\s+|some\\s+)?([a-z][a-z\\- ]{2,30}?)\\s+(?:flash ?cards|cards|decks?|quiz+)\\b", ignoreCase = true)
private val X_AFTER_VERB = py("\\b(?:practi[cs]e|learn)\\s+(?:my\\s+|some\\s+|the\\s+)?([a-z][a-z\\- ]{2,30}?)\\s*[.!?]*$", ignoreCase = true)
private val LEADING = py("^(?:(?:my|our|the|some|a|an|new|korean|talking|about)\\s+)+", ignoreCase = true)
private val TRAILING = py(
    "(?:\\s+(?:words|vocabulary|vocab|please|pls|thanks|thank you|flash ?cards|cards|decks?|quiz+|stuff|things|in korean))+$", ignoreCase = true,
)
private val NOT_THEMES = (
    "me it this that these those them more all few hard difficult easy common basic today tomorrow yesterday something anything " +
        "everything what words vocabulary cards questions quiz deck decks flashcards last week weak weakest mistakes progress notes do " +
        "does did i can could would will make give create show have has is are any how where when why please let help want need saved again right now"
    ).split(" ").toSet()
private val NOT_THEME_WORDS = py("\\b(topics?|lessons?|notes|progress|streak)\\b", ignoreCase = true)
private val END_PUNCT = py("[.!?]+$")

private val KO_DECK = py("플래시\\s*카드|카드|덱")
private val KO_QUIZ = py("퀴즈|시험|문제")
private val KO_PRACTICE = py("단어|어휘|연습|공부|복습")
private val KO_MISTAKES = py("틀린|틀렸|실수|오답")
private val KO_RECENT = py("최근|요즘|오늘\\s*(?:공부|복습|본)|이번\\s*주")
private val KO_TYPES = linkedMapOf("형용사" to "adjective", "동사" to "verb", "명사" to "noun")
private val KO_LETTER = py("([ㄱ-ㅎ])\\s*(?:으로|로)\\s*시작")
private val KO_THEME = listOf(
    py("([가-힣]{1,8})\\s*(?:에\\s*(?:대한|관한|대해서?|관해서?)|\\s+관련)"),
    py("([가-힣]{1,8})\\s+(?:단어|어휘|퀴즈|플래시\\s*카드|카드|덱)"),
)
private val KO_NOT_THEMES = "이 그 저 하나 새 새로운 내 제 나의 저의 어려운 틀린 오늘 재미있는 아무 모든 쉬운 한국어 기본 다른 이런 그런".split(" ").toSet()
private val HANGUL_WORD = py("[가-힣]+")
private val LATIN3 = py("[a-z]{3,}", ignoreCase = true)
private const val THEME_STRIP = " .!?-'\""

private fun cleanTheme(raw: String): String? {
    var theme = casefold(raw.split(",")[0].trim { it in THEME_STRIP })
    theme = theme.replaceFirst(LEADING, "").replaceFirst(TRAILING, "").trim(' ', '-')
    if (theme.isEmpty() || theme.length > 40 || theme.any { it.isDigit() }) return null
    if (theme.pySplit()[0] in NOT_THEMES || NOT_THEME_WORDS.containsMatchIn(theme)) return null
    return theme
}

private fun englishTheme(text: String): String? {
    PREPOSITION.findAll(text).lastOrNull()?.let { last ->
        cleanTheme(text.substring(last.range.last + 1).replace(END_PUNCT, ""))?.let { return it }
    }
    for (pattern in listOf(X_WORDS, X_FIRST, X_AFTER_VERB)) {
        pattern.find(text)?.let { m -> cleanTheme(m.groupValues[1])?.let { return it } }
    }
    return null
}

private fun koreanIntent(text: String): Intent? {
    val deck = KO_DECK.find(text); val quiz = KO_QUIZ.find(text)
    if (deck == null && quiz == null && !KO_PRACTICE.containsMatchIn(text)) return null
    val kind = if (deck != null && (quiz == null || deck.range.first < quiz.range.first)) "deck" else "quiz"
    if (KO_MISTAKES.containsMatchIn(text)) return Intent(kind, "mistakes", null)
    if (KO_RECENT.containsMatchIn(text)) return Intent(kind, "recent", if ("오늘" in text) "today" else if ("이번" in text) "week" else null)
    KO_TYPES.entries.firstOrNull { it.key in text }?.let { return Intent(kind, "word_type", it.value) }
    KO_LETTER.find(text)?.let { return Intent(kind, "letter", it.groupValues[1]) }
    for (pattern in KO_THEME) {
        val m = pattern.find(text) ?: continue
        if (m.groupValues[1] !in KO_NOT_THEMES) return Intent(kind, "theme", m.groupValues[1])
    }
    return null
}

private val COUNT = py("\\b(\\d{1,2})\\s+questions?\\b", ignoreCase = true)

/** "3 questions" -> 3 (1 to 10); null without a number. */
private fun count(text: String): Int? = COUNT.find(text)?.groupValues?.get(1)?.toInt()?.coerceIn(1, 10)

/** {kind, source, value} if the message clearly asks for a made-to-measure quiz or deck (English or Korean); else null. */
fun intent(message: String, isTopic: (String) -> Boolean = { false }): Intent? {
    val text = message.pySplit().joinToString(" ")
    if (HANGUL_WORD.containsMatchIn(text) && !LATIN3.containsMatchIn(text)) return koreanIntent(text)
    val deck = DECK.find(text); val quiz = QUIZ.find(text)
    if (deck == null && quiz == null && !PRACTICE.containsMatchIn(text)) return null
    val kind = if (deck != null && (quiz == null || deck.range.first < quiz.range.first)) "deck" else "quiz"
    val count = count(text)
    if (RECENT.containsMatchIn(text)) {
        return Intent(kind, "recent", if (TODAY.containsMatchIn(text)) "today" else if (WEEK.containsMatchIn(text)) "week" else null, count)
    }
    val found = WORD_TYPE.find(text)
    val rest = (englishTheme(text) ?: "").replace(WORD_TYPE, "").replace("korean", "").trim(' ', '-')
    if (found != null && rest.isEmpty()) return Intent(kind, "word_type", casefold(found.groupValues[1]).trimEnd('s'), count)
    PREPOSITION.find(text)?.let { first ->
        val named = text.substring(first.range.last + 1).replace(END_PUNCT, "").split(",")[0].trim()
        for (name in listOf(named, named.replaceFirst(LEADING, ""))) if (isTopic(name)) return Intent(kind, "topic", name, count)
    }
    if (MISTAKES.containsMatchIn(text)) return Intent(kind, "mistakes", null, count)
    LETTER.find(text)?.let { m -> return Intent(kind, "letter", m.groupValues[1].ifEmpty { m.groupValues[2] }, count) }
    val theme = englishTheme(text) ?: return null
    return Intent(kind, if (isTopic(theme)) "topic" else "theme", theme, count)
}

private val NOTES_ABOUT = py("\\b(?:notes?|pdfs?|documents?)\\b.*?\\b(?:about|on|for)\\s+(?:the\\s+)?(?:topic\\s+)?(.+?)\\s*[?.!]*$", ignoreCase = true)

/** "What do my notes say about Anki words 26–50?" -> "Anki words 26–50". */
fun notesAbout(message: String): String? = NOTES_ABOUT.find(message.pySplit().joinToString(" "))?.groupValues?.get(1)?.trim(' ', ',')

// ---------- the theme ready to search ----------

private val TO_PREFIX = py("^to\\s+")

private fun firstMeaning(english: String): String =
    casefold(english.split(CUT)[0].trim()).replaceFirst(TO_PREFIX, "").ifEmpty { casefold(english) }

/** `typed` is `word` with ONE keyboard slip: a letter missing, one more, or two neighbours swapped. */
private fun oneSlip(typed: String, word: String): Boolean {
    if (typed == word || typed[0] != word[0]) return false
    if (typed.length == word.length) {
        val diff = word.indices.filter { typed[it] != word[it] }
        return diff.size == 2 && diff[1] == diff[0] + 1 && typed[diff[0]] == word[diff[1]] && typed[diff[1]] == word[diff[0]]
    }
    val (short, long) = listOf(typed, word).sortedBy { it.length }
    if (long.length - short.length != 1) return false
    return long.indices.any { long.removeRange(it, it + 1) == short }
}

private fun typoFix(token: String, vocab: Set<String>): String {
    val matches = vocab.filter { it.length >= 4 && oneSlip(token, it) }.sorted()
    return if (matches.size == 1) matches[0] else token
}

private val LOWER_WORDS = py("[a-z]+")
private val TOKENS = py("[A-Za-z]+|[^A-Za-z]+")
private val SPACES2 = py("\\s{2,}")

/** The theme ready to search: Korean -> the meaning of that word of your notes; English without filler and typos. */
fun resolveTheme(theme: String, words: List<Word>): String {
    val original = theme.pySplit().joinToString(" ").trim(' ', '.', ',', '!', '?')
    if (HANGUL_WORD.matches(original.replace(" ", ""))) {
        // shortcut: a Korean theme that is not a word of your notes needs the embeddings (closest_name, phase 6).
        return words.firstOrNull { norm(it.korean) == norm(original) }?.let { firstMeaning(it.english) } ?: original
    }
    val vocab = words.flatMap { w -> LOWER_WORDS.findAll(casefold(w.english)).map { it.value } }.toSet() + THEME_NAMES.flatMap { it.split(" ") }
    val out = StringBuilder()
    for (m in TOKENS.findAll(original)) {
        val token = m.value
        var low = casefold(token)
        val alpha = token.all { it.isLetter() }
        if (alpha && low in FILLER) continue
        if (alpha && low.length >= 4 && low !in vocab && !token[0].isUpperCase()) low = typoFix(low, vocab)
        out.append(low)
    }
    return out.toString().replace(SPACES2, " ").trim()
}

// ---------- common questions: the code already knows which tool they need ----------

private val NOTES_SAY = py(
    "\\b(?:what do(?:es)? my notes say about|what do my notes have on|explain from my notes|look up in my notes)\\s+(.+?)\\s*[?.!]*$", ignoreCase = true,
)
private val PLAN = py("\\b(plan|schedule|routine)\\b", ignoreCase = true)
private val DUE = py("\\b(flash ?cards?|cards?|decks?)\\b.*\\b(due|review|pending|left)\\b|\\b(due|pending)\\b.*\\b(flash ?cards?|cards?)\\b", ignoreCase = true)
private val PROGRESS = py(
    "\\b(how am i doing|how'?s my progress|how is my progress|my (progress|streak|accuracy|stats)|how (much|long) (have|did) i stud)", ignoreCase = true,
)
private val WEAK = py(
    "\\b(weak(est)?|worst|struggl\\w*|find hard|hardest|what should i (study|focus on|learn|review|practi[cs]e)|focus on today|" +
        "(should|do) i review|review this week|teach me something)\\b|(뭐|무엇을|뭘)\\s*(공부|복습)",
    ignoreCase = true,
)

/** "I only have 20 minutes, what should I do?": a plan with that time. */
val TIME_BUDGET = py("\\b\\d+\\s*(minutes?|mins?|hours?)\\b", ignoreCase = true)

/** The tools a common question needs, without asking the model which one; empty: the model decides. */
fun plannedTools(message: String): List<Pair<String, Map<String, Any?>>> {
    val text = message.pySplit().joinToString(" ")
    NOTES_SAY.find(text)?.let { return listOf("search_knowledge_base" to mapOf("query" to it.groupValues[1].trim(' ', '"', '\'').take(200))) }
    return when {
        PLAN.containsMatchIn(text) || TIME_BUDGET.containsMatchIn(text) -> listOf("get_weak_topics" to emptyMap(), "get_due_flashcards" to emptyMap())
        DUE.containsMatchIn(text) -> listOf("get_due_flashcards" to emptyMap())
        PROGRESS.containsMatchIn(text) -> listOf("get_recent_progress" to emptyMap())
        WEAK.containsMatchIn(text) -> listOf("get_weak_topics" to emptyMap())
        else -> emptyList()
    }
}
