package com.dero.juststudykorean.korean

// Ports of backend/app/quiz/generator.py (widen_answer) and, below, quiz/builders.py and quiz/grammar.py.

private val hangulWord = py("[가-힣]+")
private val asksKorean = py(
    "\\b(?:korean\\s+(?:word|verb|noun|adjective|expression|phrase|term|for)|in\\s+korean|" +
        "(?:what|which)\\s+(?:korean\\s+)?(?:word|verb|expression|phrase)\\s+(?:means|is|for|describes))\\b",
    ignoreCase = true,
)
private val quoted = py("""["'“‘]([^"'”’]{2,80})["'”’]""")
private val latinWords = py("[a-z]+")

/** "Middle, Among" -> "Middle / Among": each meaning in your notes counts on its own. */
private fun asAlternatives(text: String): String =
    text.split(py("[;,/]")).map { it.pyStrip() }.filter { it.isNotEmpty() }.distinct().joinToString(" / ").ifEmpty { text }

private fun plainEn(text: String) = latinWords.findAll(text.uppercase().lowercase().replace("'", "")).joinToString(" ") { it.value }

/** A Korean answer that is not a word of your notes: the sentence or word of your notes the prompt quotes. */
private fun fromNotes(answer: String, pool: List<VocabPair>, prompt: String, text: String): String {
    val examples = pool.filter { it.exampleKo != null && it.exampleEn != null && it.exampleKo.isNotEmpty() && it.exampleEn.isNotEmpty() }
        .map { it.exampleKo!! to it.exampleEn!! }.toMutableList()
    for (line in text.pySplitlines()) {
        if ("=" in line && HANGUL.containsMatchIn(line.split("=", limit = 2)[0])) {
            val (ko, en) = line.split("=", limit = 2).map { it.pyStrip() }
            examples += py("^[\\s\\d.)]*").replaceFirst(ko, "") to en
        }
    }
    for (q in quoted.findAll(prompt).map { it.groupValues[1] }.filterNot { HANGUL.containsMatchIn(it) }) {
        val wanted = plainEn(q)
        for ((ko, en) in examples) {
            if (wanted.isNotEmpty() && plainEn(py("\\(.*?\\)").replace(en, "")) == wanted && ko.pySplit().size > 1) return ko
        }
        val senses = englishSenses(q)
        val words = pool.filter { senses.isNotEmpty() && (senses intersect englishSenses(it.english)).isNotEmpty() }.map { it.korean }
        if (words.isNotEmpty()) return (words + answer).distinct().joinToString(" / ")
    }
    return answer
}

/**
 * A short answer with everything your notes accept: every meaning in English ("To lift / To hold"), every
 * Korean word with that meaning ("곁 / 옆"), or, when the prompt asks for the Korean word but the answer is
 * English, the Korean of your notes; null if your notes do not have it (the question is not used).
 */
fun widenAnswer(answer: String, pool: List<VocabPair>, prompt: String = "", text: String = ""): String? {
    if (HANGUL.containsMatchIn(answer)) {
        val quotedWords = hangulWord.findAll(prompt).map { norm(it.value) }.toSet()
        if (norm(answer) in quotedWords) return null // the answer is the word the prompt asks about
        val target = pool.firstOrNull { norm(it.korean) == norm(answer) } ?: return fromNotes(answer, pool, prompt, text)
        val same = pool.filter { (englishSenses(it.english) intersect englishSenses(target.english)).isNotEmpty() }.map { it.korean }
        return (listOf(answer) + same).distinct().joinToString(" / ")
    }
    val senses = englishSenses(answer)
    // "What does '밑' mean in Korean?" asks for the meaning: it is not changed to Korean.
    if (asksKorean.containsMatchIn(prompt) && !HANGUL.containsMatchIn(prompt)) {
        val koreans = pool.filter { senses.isNotEmpty() && (senses intersect englishSenses(it.english)).isNotEmpty() }.map { it.korean }
        return if (koreans.isNotEmpty()) koreans.distinct().joinToString(" / ") else null
    }
    for (pair in pool) if (senses.isNotEmpty() && englishSenses(pair.english).containsAll(senses)) return asAlternatives(pair.english)
    return answer
}

// ---------------------------------------------------------------------------------------------------------
// quiz/builders.py, quiz/grammar.py (build_grammar) and rag/tutor.py (find_ungrounded_korean), checked against
// golden/builders.json. The randomness is Python's (PyRandom), so a seed gives the web's options and order.

const val N_DISTRACTORS = 3
private const val MAX_TYPED_WORDS = 4
private const val MAX_TYPED_CHARS = 40
private const val MAX_KOREAN_ANSWER_CHARS = 80
private const val MAX_BLANK_ANSWER_CHARS = 40
private const val MAX_EXAMPLE_CHARS = 160
const val RULES_VERSION = "rules.v1"
const val GRAMMAR_VERSION = "grammar.v1"

private val BLANK = py("_{3,}")
private val FORM = py("^[ㄱ-ㆎ가-힣]{1,4}$")

/** A question built by the rules; [options] null for typed answers. */
data class Candidate(
    val type: String,
    val prompt: String,
    val answer: String,
    val options: List<String>?,
    val explanation: String,
    val chunk: Chunk,
    val promptVersion: String = "",
    val model: String = "",
)

/** A built question, or why it could not be built ("too_few_distractors", "invented_korean"…). */
data class Built(val candidate: Candidate?, val reason: String?) {
    companion object {
        fun ok(c: Candidate) = Built(c, null)
        fun no(reason: String) = Built(null, reason)
    }
}

/** A fill_blank / grammar_selection proposal (from the model in the web; validated here). */
data class GeneratedBlank(val sentence: String, val answer: String, val distractors: List<String> = emptyList(), val explanation: String = "")

// ---- rag/tutor.py
private val tutorHangulWord = py("[\\uac00-\\ud7a3]{2,}")
private val endings = listOf(
    "입니다", "입니까", "이에요", "이예요", "에서는", "에서도", "이라고", "예요", "이야", "이다", "에서", "에게", "한테", "으로",
    "부터", "까지", "이랑", "하고", "은", "는", "이", "가", "을", "를", "에", "도", "로", "랑", "요", "야",
)
private val commonKorean = ("입니다 입니까 이에요 예요 이다 아니에요 아닙니다 있어요 있습니다 없어요 없습니다 " +
    "해요 합니다 하다 이 그 저 은 는 이 가 을 를 에 에서 도 와 과 그리고 하지만 그래서 " +
    "네 아니요 무슨 뜻 뜻이에요 뭐예요 저는 나는 제가 내가 저도 싶어요 싶다 싶습니다 고").pySplit().toSet()

/** Forms to look for in the notes: the word, without its ending, and cut by 1-2 syllables. */
fun koreanStems(word: String): List<String> {
    val stems = mutableListOf(word)
    endings.firstOrNull { word.endsWith(it) && word.pyLen > it.pyLen }?.let { stems += word.dropLast(it.length) }
    if (word.pyLen >= 3) stems += word.dropLast(1)
    if (word.pyLen >= 4) stems += word.dropLast(2)
    return stems
}

/** Hangul words of [answer] that are not in the sources nor in the question (endings and particles allowed). */
fun findUngroundedKorean(answer: String, context: String, question: String): List<String> {
    val haystack = nfc("$context\n$question")
    return tutorHangulWord.findAll(nfc(answer)).map { it.value }.distinct()
        .filter { it !in commonKorean && koreanStems(it).none { stem -> stem in haystack } }.toList()
}

// ---- quiz/builders.py
/** Coarse class to pick similar distractors: verb/adjective ("가다", "To go") or other. */
fun wordClass(input: String): String {
    val text = nfc(input).pyStrip()
    if (text.isNotEmpty() && HANGUL.containsMatchIn(text.takeLast(1))) return if (text.endsWith("다")) "verb" else "other"
    return if (casefold(text).startsWith("to ")) "verb" else "other"
}

internal fun pickDistractors(answer: String, pool: List<String>, rng: PyRandom, sameClass: Boolean = false): List<String> {
    val seen = mutableSetOf(norm(answer))
    var unique = mutableListOf<String>()
    for (item in pool) if (norm(item).isNotEmpty() && seen.add(norm(item))) unique += item
    if (sameClass) {
        // Verbs with verbs (so 밥 does not show up between 마시다 and 오다), if there are enough.
        val similar = unique.filter { wordClass(it) == wordClass(answer) }
        if (similar.size >= N_DISTRACTORS) unique = similar.toMutableList()
    }
    if (unique.size < N_DISTRACTORS) return emptyList()
    return rng.sample(unique, N_DISTRACTORS)
}

/** If every option is English text, they all start with a capital: the form must not tell which one differs. */
internal fun sameCase(answer: String, distractors: List<String>): Pair<String, List<String>> {
    val options = listOf(answer) + distractors
    if (!options.all { it.isNotEmpty() && it[0].code < 128 && it[0].isLetter() }) return answer to distractors
    val capped = options.map { it.take(1).uppercase() + it.drop(1) }
    return capped[0] to capped.drop(1)
}

internal fun shuffled(answer: String, distractors: List<String>, rng: PyRandom): List<String> =
    (listOf(answer) + distractors).toMutableList().also(rng::shuffle)

/** A vocabulary question made only with rules. [pool]: the pairs of the same document. */
fun buildFromPair(kind: String, pair: VocabPair, chunk: Chunk, pool: List<VocabPair>, grounding: String, rng: PyRandom): Built {
    val ko = pair.korean
    val en = pair.english
    // Neither the same word nor another one sharing a meaning: they would be right too.
    val senses = englishSenses(en)
    val heads = ko.split(KOREAN_ALTERNATIVES).map(::norm).toSet()
    val others = pool.filter { p ->
        norm(p.korean) != norm(ko) && (p.korean.split(KOREAN_ALTERNATIVES).map(::norm).toSet() intersect heads).isEmpty() &&
            norm(p.english) != norm(en) && (englishSenses(p.english) intersect senses).isEmpty()
    }
    var distractors = emptyList<String>()
    var answer: String
    val prompt: String
    when (kind) {
        "ko_to_en" -> {
            if (en.pySplit().size > MAX_TYPED_WORDS || en.pyLen > MAX_TYPED_CHARS) return Built.no("answer_too_long")
            prompt = "What does \"$ko\" mean?"; answer = en
        }
        "vocab_recognition" -> {
            prompt = "Choose the meaning of \"$ko\"."; answer = en
            distractors = pickDistractors(answer, others.map { it.english }, rng, true)
            sameCase(answer, distractors).let { answer = it.first; distractors = it.second }
        }
        "en_to_ko" -> {
            prompt = "Which Korean word means \"$en\"?"; answer = ko
            distractors = pickDistractors(answer, others.map { it.korean }, rng, true)
        }
        "write_ko" -> {
            if (ko.pyLen > MAX_KOREAN_ANSWER_CHARS) return Built.no("answer_too_long")
            prompt = "Write in Korean: \"$en\""; answer = ko
        }
        else -> throw IllegalArgumentException("Not a rule-based type: $kind")
    }
    var options: List<String>? = null
    if (kind != "ko_to_en" && kind != "write_ko") {
        if (distractors.isEmpty()) return Built.no("too_few_distractors")
        options = shuffled(answer, distractors, rng)
    }
    // Safety net: all the Korean shown comes from your documents.
    if (findUngroundedKorean((listOf(prompt, answer) + distractors).joinToString(" "), grounding, "").isNotEmpty()) return Built.no("invented_korean")
    var explanation = ""
    if (!pair.exampleKo.isNullOrEmpty()) {
        val example = pair.exampleKo + if (!pair.exampleEn.isNullOrEmpty()) " (${pair.exampleEn})" else ""
        explanation = "Example: ${example.pyTake(MAX_EXAMPLE_CHARS)}"
    }
    if (kind == "write_ko") {
        // Another word of your notes with the same meaning ("Inside": 안 and 속) is right too.
        val also = pool.filter { norm(it.english) == norm(en) && norm(it.korean) != norm(ko) }.map { it.korean }.distinct()
        if (also.isNotEmpty()) {
            answer = (listOf(ko) + also).joinToString(KOREAN_ALTERNATIVES)
            explanation = "$explanation Also accepted: ${also.joinToString(", ")}.".pyStrip()
        }
    }
    return Built.ok(Candidate(kind, prompt, answer, options, explanation, chunk, RULES_VERSION, "rules"))
}

private fun fitBlank(sentence: String, answer: String, sourceNorm: String): String? {
    val left = sentence.replace(" ___", "___")
    val right = sentence.replace("___ ", "___")
    return listOf(sentence, left, right, left.replace("___ ", "___")).distinct()
        .firstOrNull { norm(it.replace("___", answer)) in sourceNorm }
}

/** Lines that explain the rule (not the examples): "은 attaches after...", "은/는". */
private fun ruleText(chunk: Chunk) =
    nfc(chunk.text).pySplitlines().filter { GRAMMAR_WORDS.containsMatchIn(it) || PARTICLE_PAIR.containsMatchIn(it) }.joinToString("\n")

private fun String.countOf(sub: String): Int {
    var n = 0
    var i = indexOf(sub)
    while (i >= 0) { n++; i = indexOf(sub, i + sub.length) }
    return n
}

/** Validates a fill_blank / grammar_selection proposal. */
fun buildBlank(kind: String, gb: GeneratedBlank, chunk: Chunk, grounding: String, rng: PyRandom): Built {
    var sentence = BLANK.replace(nfc(gb.sentence).pyStrip(), "___")
    val answer = nfc(gb.answer).pyStrip()
    // The model sometimes copies the whole sentence: the blank goes where the answer is.
    if ("___" !in sentence && answer.isNotEmpty() && sentence.countOf(answer) == 1) sentence = sentence.replace(answer, "___")
    if (sentence.countOf("___") != 1) return Built.no("bad_blank")
    if (sentence.pyLen !in 5..200 || answer.pyLen !in 1..MAX_BLANK_ANSWER_CHARS) return Built.no("bad_length")
    if ("_" in answer) return Built.no("bad_blank")
    // Filled with the answer, the sentence must be in the source as it is.
    sentence = fitBlank(sentence, answer, norm(chunk.text)) ?: return Built.no("sentence_not_in_source")
    if (leaksAnswer(sentence, answer)) return Built.no("answer_in_question")
    val seen = mutableSetOf(norm(answer))
    var distractors = mutableListOf<String>()
    for (raw in gb.distractors) {
        val d = nfc(raw).pyStrip()
        if (d.isNotEmpty() && d.pyLen <= MAX_BLANK_ANSWER_CHARS && "_" !in d && seen.add(norm(d))) distractors += d
    }
    if (distractors.size < N_DISTRACTORS) return Built.no("too_few_distractors")
    distractors = distractors.take(N_DISTRACTORS).toMutableList()
    val explanation = gb.explanation.pyStrip().pyTake(300)
    if (kind == "grammar_selection") {
        if (!HANGUL.containsMatchIn(sentence)) return Built.no("not_korean")
        if (!(listOf(answer) + distractors).all { FORM.pyMatch(it) != null }) return Built.no("not_a_grammar_form")
        if (!(listOf(answer) + distractors).all { it in ruleText(chunk) }) return Built.no("option_not_in_rule")
        if (explanation.isEmpty()) return Built.no("no_explanation")
    }
    // Ambiguous: another option also gives a sentence that is in your documents.
    val haystack = norm(grounding)
    if (distractors.any { norm(sentence.replace("___", it)) in haystack }) return Built.no("ambiguous_option")
    if (findUngroundedKorean((listOf(sentence, answer) + distractors).joinToString(" "), grounding, "").isNotEmpty()) return Built.no("invented_korean")
    val label = if (kind == "fill_blank") "Fill in the blank: " else "Choose the correct form: "
    return Built.ok(Candidate(kind, label + sentence, answer, shuffled(answer, distractors, rng), explanation, chunk))
}

// ---- quiz/grammar.py (questions)
/** (question type, variants): grammar_selection picks the pattern; multiple_choice the meaning or the sentence. */
val GRAMMAR_VARIANTS = linkedMapOf("grammar_selection" to listOf("pick_form"), "multiple_choice" to listOf("pick_meaning", "pick_sentence"))
private val questionWords = listOf("what ", "which ", "how ", "when ", "why ", "who ", "where ")
private val commonWords = ("what which where when ending grammar express expressing something someone other person " +
    "people about would could should their there these those thing things using used speaker " +
    "listener sentence sentences action actions after before because").pySplit().toSet()

private fun isQuestion(text: String) = questionWords.any { casefold(text).startsWith(it) } || text.pyRstrip().endsWith("?")

private fun keyWords(text: String) = latinWords.findAll(casefold(text)).map { it.value }.filter { it.pyLen >= 5 && it !in commonWords }.toSet()

/** Options shaped like the answer: same style (question or not) and the closest lengths, fewest shared words first. */
private fun alike(answer: String, options: List<String>, keep: Int = 6): List<String> {
    val sameStyle = options.filter { isQuestion(it) == isQuestion(answer) }
    val key = keyWords(answer)
    val shared = { o: String -> (key intersect keyWords(o)).size }
    val ordered = sameStyle.sortedWith(compareBy<String>({ shared(it) > 0 }, { Math.abs(it.pyLen - answer.pyLen) }))
    val clean = ordered.filter { shared(it) == 0 }
    return (if (clean.size >= 3) clean else ordered).take(keep)
}

/** (chunk, point, variant) for a question type, in the web's order. */
fun grammarItems(kind: String, chunks: List<Chunk>): List<Triple<Chunk, GrammarPoint, String>> = chunks.flatMap { chunk ->
    extractGrammar(chunk).flatMap { point ->
        GRAMMAR_VARIANTS[kind].orEmpty().filterNot { it == "pick_sentence" && point.translated.isEmpty() }.map { Triple(chunk, point, it) }
    }
}

/** A grammar question made only with your notes. [pool]: the points of the same document. */
fun buildGrammar(kind: String, variant: String, point: GrammarPoint, chunk: Chunk, pool: List<GrammarPoint>, grounding: String, rng: PyRandom): Built {
    val same = pool.filter { it.kind == point.kind && norm(it.pattern) != norm(point.pattern) }
    val particle = point.kind == "particle"
    if (variant == "pick_form" && point.choices.isNotEmpty() && point.choiceAnswer != null) return fromYourChoices(kind, point, chunk, grounding, rng)
    val prompt: String
    var answer: String
    val poolOptions: List<String>
    when (variant) {
        "pick_form" -> {
            prompt = if (particle) "Which particle is used for \"${point.meaning}\"?" else "Which grammar pattern means \"${point.meaning}\"?"
            answer = point.pattern
            poolOptions = same.map { it.pattern }
        }
        "pick_meaning" -> {
            prompt = if (particle) "What is the main function of the particle \"${point.pattern}\"?" else "What does \"${point.pattern}\" mean?"
            answer = point.meaning
            poolOptions = alike(answer, same.map { it.meaning }.filter { norm(it) != norm(point.meaning) })
        }
        "pick_sentence" -> {
            val (korean, english) = point.translated[0]
            prompt = "Which sentence means \"$english\"?"
            answer = korean
            poolOptions = pool.flatMap { p -> p.translated.map { it.first } }.filter { norm(it) != norm(korean) }
        }
        else -> throw IllegalArgumentException("Unknown grammar variant: $variant")
    }
    var distractors = pickDistractors(answer, poolOptions, rng)
    if (distractors.size < N_DISTRACTORS) return Built.no("too_few_distractors")
    sameCase(answer, distractors).let { answer = it.first; distractors = it.second }
    if (leaksAnswer(prompt, answer)) return Built.no("answer_in_question")
    // Safety net: the Korean shown comes from your documents; the pool's patterns count too ("에게/한" + "테").
    val known = grounding + "\n" + pool.joinToString("\n") { it.pattern }
    if (findUngroundedKorean((listOf(prompt, answer) + distractors).joinToString(" "), known, "").isNotEmpty()) return Built.no("invented_korean")
    var explanation = "Your notes: ${point.pattern} — \"${point.meaning}\"."
    if (point.examples.isNotEmpty()) {
        val (korean, english) = point.translated.ifEmpty { point.examples }[0]
        explanation += " Example: $korean" + if (english.isNotEmpty()) " ($english)" else ""
    }
    return Built.ok(Candidate(kind, prompt, answer, shuffled(answer, distractors, rng), explanation.pyTake(300), chunk, GRAMMAR_VERSION, "rules"))
}

/** A question with the options you wrote (Anki): the question and the options as they are. */
private fun fromYourChoices(kind: String, point: GrammarPoint, chunk: Chunk, grounding: String, rng: PyRandom): Built {
    val answer = point.choiceAnswer ?: ""
    val distractors = point.choices.filter { norm(it) != norm(answer) }
    val meaning = point.meaning
    val prompt = if (questionWords.any { casefold(meaning).startsWith(it) }) meaning else "Which grammar pattern means \"$meaning\"?"
    if (distractors.isEmpty()) return Built.no("too_few_distractors")
    if (leaksAnswer(prompt, answer)) return Built.no("answer_in_question")
    if (findUngroundedKorean((listOf(prompt, answer) + distractors).joinToString(" "), grounding, "").isNotEmpty()) return Built.no("invented_korean")
    var explanation = "Your notes: ${point.pattern}."
    if (point.explanation != null) explanation += " ${point.explanation}"
    return Built.ok(Candidate(kind, prompt, answer, shuffled(answer, distractors, rng), explanation.pyTake(300), chunk, "$GRAMMAR_VERSION+anki", "rules"))
}
