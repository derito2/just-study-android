package com.dero.juststudykorean.korean.teacher

import com.dero.juststudykorean.korean.casefold
import com.dero.juststudykorean.korean.hasForm
import com.dero.juststudykorean.korean.lemmas
import com.dero.juststudykorean.korean.py
import com.dero.juststudykorean.korean.pyMatch
import com.dero.juststudykorean.korean.pySplit
import com.dero.juststudykorean.korean.pySplitlines
import com.dero.juststudykorean.korean.pyStrip

// Port of backend/app/rag/entries.py (has_form and korean_cores are in KoreanForms.kt) and tutor.korean_stems:
// what a question asks for, and the entries of your notes that answer it without the model.

// Endings and particles, longest first: 학교입니다 -> 학교.
private val ENDINGS = listOf(
    "입니다", "입니까", "이에요", "이예요", "에서는", "에서도", "이라고", "예요", "이야", "이다", "에서", "에게", "한테", "으로",
    "부터", "까지", "이랑", "하고", "은", "는", "이", "가", "을", "를", "에", "도", "로", "랑", "요", "야",
)

/** Forms to look for in your notes: the word, without its ending, and cut by 1-2 syllables. */
fun koreanStems(word: String): List<String> {
    val stems = mutableListOf(word)
    ENDINGS.firstOrNull { word.endsWith(it) && word.length > it.length }?.let { stems += word.dropLast(it.length) }
    if (word.length >= 3) stems += word.dropLast(1)
    if (word.length >= 4) stems += word.dropLast(2)
    return stems
}

private const val END = "\\s*[\"'”’]?\\s*[?.!]*\\s*$"
private val WORD_QUESTIONS = listOf(
    py("\\bhow\\s+(?:do|would|can|should)\\s+(?:i|you|we)\\s+say\\s+(.+?)(?:\\s+in\\s+korean)?$END", ignoreCase = true),
    py("\\bwhat(?:\\s+is|'s|\\s+does)\\s+(.+?)\\s+(?:mean|in\\s+korean|in\\s+english)$END", ignoreCase = true),
    py("\\b(?:korean|english)\\s+(?:word\\s+)?for\\s+(.+?)$END", ignoreCase = true),
    py("\\bmeaning\\s+of\\s+(.+?)$END", ignoreCase = true),
    py("\\btranslate\\s+(.+?)(?:\\s+(?:in)?to\\s+(?:korean|english))?$END", ignoreCase = true),
    py("^\\s*(.+?)\\s+in\\s+korean$END", ignoreCase = true),
    py("^\\s*([가-힣]+?)(?:은|는|이|가)?\\s*(?:무슨\\s*뜻|뜻이\\s*뭐|뭐예요|뭐야)"),
    py("^\\s*((?:-|~|\\(으\\))[^?]*?[가-힣)])(?:은|는|이|가)?\\s*(?:무슨\\s*뜻|뜻이\\s*뭐|뭐예요|뭐야)"),
    py("\\bhow\\s+(?:do|can|should)\\s+(?:i|you|we)\\s+use\\s+((?:-|~|\\(으\\)).+?)$END", ignoreCase = true),
)
private val GRAMMAR_WORD = py("^\\s*(?:-|~|\\(으\\)|\\(이\\)|[ㄱ-ㅎ]\\s*[가-힣])")

/** "-네요", "(으)니까", "ㄹ 거야". */
fun isGrammarWord(word: String) = GRAMMAR_WORD.pyMatch(word) != null

private val OPTIONAL = py("\\(([^()]*(?:\\([^()]*\\))?[^()]*)\\)")

/** "더라(고(요))" -> [더라, 더라고, 더라고요]: each parenthesis is optional. */
private fun optional(text: String): List<String> {
    val m = OPTIONAL.find(text) ?: return listOf(text)
    val before = text.substring(0, m.range.first); val inner = m.groupValues[1]; val after = text.substring(m.range.last + 1)
    val out = mutableListOf<String>()
    for (middle in listOf("") + optional(inner)) out += optional(before + middle + after)
    return out.distinct()
}

private val PLAIN_LEAD = py("^[\\s~-]*(?:[ㄱ-ㅎ](?=\\s+[가-힣]))?")
private val NOT_KOREAN = py("[^가-힣ㄱ-ㅎ]")

/** Without dash, spaces, punctuation or a loose leading consonant: "ㄹ 거야" -> "거야". */
internal fun plainForm(text: String): String = text.replaceFirst(PLAIN_LEAD, "").replace(NOT_KOREAN, "")

private val PATTERN_ITEMS = py(",|\\s+vs\\.?\\s+")
private val VOWELS = setOf("아", "어", "여", "았", "었", "였")
private val FINALS = setOf("ㄴ", "ㄹ", "ㅁ", "ㅂ", "은", "을", "음", "읍")
private val EU_FINAL = linkedMapOf("ㄴ" to "은", "ㄹ" to "을", "ㅁ" to "음", "ㅂ" to "읍")

private fun composeEu(form: String): String = EU_FINAL.entries.fold(form) { f, (jamo, syllable) -> f.replace("으$jamo", syllable) }

/** Every way of writing a pattern of your notes: "-거든(요)" -> {거든, 거든요}; "-(으)ㄴ/는데(요)" -> {는데, 은데, ㄴ데…}. */
fun patternForms(pattern: String): Set<String> {
    val forms = linkedSetOf<String>()
    for (item in pattern.split(PATTERN_ITEMS)) {
        val parts = item.split("/").map { it.pyStrip() }.filter { it.isNotEmpty() }
        val plains = parts.map { p -> optional(p).map(::plainForm) }
        for (options in plains) forms += options.filter { it.isNotEmpty() }
        for ((i, options) in plains.withIndex()) {
            for (short in options.map(::composeEu)) {
                if (short !in VOWELS && short !in FINALS) continue
                for (other in plains.filterIndexed { j, _ -> j != i }) {
                    for (longer in other) {
                        if (longer.length < 2 || longer[0] !in '가'..'힣') continue
                        if (short in FINALS || longer.take(1) in VOWELS) forms += short + longer.substring(1)
                    }
                }
            }
        }
    }
    return forms.map(::composeEu).toSet()
}

fun sameGrammar(word: String, pattern: String): Boolean {
    val wanted = plainForm(word)
    if (wanted.isEmpty()) return false
    val forms = patternForms(pattern)
    if (wanted in forms || plainForm(pattern) == wanted) return true
    val longer = forms.filter { it.length >= 2 }.toSet()
    if (wanted.removeSuffix("요") in longer || "${wanted}요" in forms) return true
    if (lemmas(wanted, forms.filter { it.endsWith("다") && it.length >= 2 }.toSet()).isNotEmpty()) return true
    for (form in forms) {
        if (!form.endsWith("다") || form.length < 2) continue
        val stem = form.dropLast(1)
        if (stem.endsWith("하")) {
            val base = stem.dropLast(1)
            if (base.isNotEmpty() && wanted.startsWith(base) && wanted.substring(base.length).take(1) in "하했해합") return true
        } else if (stem.length >= 2 && wanted.startsWith(stem) && wanted != stem) return true
    }
    return false
}

private val EXAMPLE_QUESTION = py(
    "\\b(?:give me |show me )?(?:an |some )?example(?:s| sentences?)?\\s+(?:with|of|using|for)\\s+(.+?)$END", ignoreCase = true,
)
private val PRONOUNS = "it this that these those them they he she him her one ones that word this word".split(" ").toSet() +
    setOf("that word", "this word", "these words", "those words", "the first one", "the last one")
private val LEADING = py("^(?:the\\s+word\\s+|the\\s+|an?\\s+|[\"'“‘])+", ignoreCase = true)
private val HANGUL = py("[가-힣]")
private const val MAX_WORDS = 3

private val LABEL = py("^\\s*(?:[-•*]\\s*)?(example|examples|ex|e\\.g\\.|sentence|translation)\\s*\\d*\\s*[:.)]\\s*", ignoreCase = true)
private val ENTRY = py("^[\\s\\d.)•*-]*([^\\n=]{1,40}?)\\s*=\\s*(.+?)\\s*$")
private val PHRASE = py("^\\s*\\d+[.)]\\s*[^=\\n]*[\\uac00-\\ud7a3][^=\\n]*$")
private val NUMBERING = py("^\\s*\\d+[.)]\\s*")
private const val MAX_MENTIONS = 3

fun hasHangul(text: String) = HANGUL.containsMatchIn(text)

fun asksExample(question: String) = EXAMPLE_QUESTION.containsMatchIn(question.pyStrip())

private val MAKE_SENTENCE = py(
    "\\b(?:make|write|build|create)\\s+(?:me\\s+)?(?:a|an|one|some)?\\s*(?:short\\s+)?sentences?\\s+(?:with|using|of|for)\\s+(.+?)$END",
    ignoreCase = true,
)

fun asksSentence(question: String) = MAKE_SENTENCE.containsMatchIn(question.pyStrip())

private const val QUOTES = " \"'“”‘’"

/** What the sentence has to use: "-고 싶다 and 물". */
fun sentenceTarget(question: String): String? {
    for (pattern in listOf(EXAMPLE_QUESTION, MAKE_SENTENCE)) {
        pattern.find(question.pyStrip())?.let { return it.groupValues[1].trim { c -> c in QUOTES } }
    }
    return null
}

private const val PARTICLE_TAIL = "(?:을|를|이|가|은|는|도|에|에서|만|과|와|이랑|랑|하고|의|로|으로)?"

/** The line uses that word or that grammar (a one-syllable word only with a particle after it: 물을, not 물건). */
fun uses(line: String, core: String, grammar: Boolean, aeo: Set<String> = emptySet()): Boolean {
    if (grammar) return core in line || hasForm(line, core, aeo)
    val tail = if (core.length == 1) PARTICLE_TAIL else "[가-힣]*"
    return py("(?<![가-힣])${Regex.escape(core)}$tail(?![가-힣])").containsMatchIn(line)
}

private val SAY_PHRASE = py(
    "\\bhow\\s+(?:do|can|would)\\s+(?:i|you)\\s+say\\s+[\"'“‘]?(.+?)[\"'”’]?\\s*(?:in\\s+korean)?\\s*[?.!]*\\s*$", ignoreCase = true,
)

/** The English phrase of "How do I say …?" (up to 12 words), or null. */
fun askedPhrase(question: String): String? {
    val m = SAY_PHRASE.find(question.pyStrip()) ?: return null
    val phrase = m.groupValues[1].trim { it in QUOTES }
    if (phrase.isEmpty() || HANGUL.containsMatchIn(phrase) || phrase.pySplit().size > 12) return null
    return phrase
}

private val COMPARE = listOf(
    py("\\bdifference\\s+between\\s+(.+?)\\s+and\\s+(.+?)$END", ignoreCase = true),
    py("\\bcompare\\s+(.+?)\\s+(?:and|with|to)\\s+(.+?)$END", ignoreCase = true),
    py("^\\s*(?:what about\\s+)?(.+?)\\s+(?:vs\\.?|versus)\\s+(.+?)$END", ignoreCase = true),
)

/** The two words or rules you want to compare, or null. */
fun comparedWords(question: String): List<String>? {
    for (pattern in COMPARE) {
        val m = pattern.find(question.pyStrip()) ?: continue
        val words = m.groupValues.drop(1).map { it.replaceFirst(LEADING, "").trim { c -> c in QUOTES } }
        if (words.all { it.isNotEmpty() && it.pySplit().size <= MAX_WORDS }) return words // else: the next pattern
    }
    return null
}

/** The word you ask about, or null if the question is not about one word. */
fun askedWord(question: String): String? {
    for (pattern in WORD_QUESTIONS + EXAMPLE_QUESTION) {
        val m = pattern.find(question.pyStrip()) ?: continue
        val word = m.groupValues[1].replaceFirst(LEADING, "").trim { it in QUOTES }
        if (casefold(word) in PRONOUNS) return null
        val limit = if (isGrammarWord(word)) 8 else MAX_WORDS
        return if (word.isNotEmpty() && word.pySplit().size <= limit) word else null
    }
    return null
}

/** Forms to look for: "to go" -> also "go"; "schools" -> "school"; 학교는 -> 학교. */
fun variants(word: String): List<String> {
    val out = mutableListOf(word)
    if (HANGUL.containsMatchIn(word)) out += koreanStems(word).take(2) else {
        if (word.lowercase().startsWith("to ")) out += word.substring(3)
        if (word.lowercase().endsWith("s") && word.length > 3) out += word.dropLast(1)
    }
    return out.distinct()
}

private fun mentions(line: String, words: List<String>) = words.any { w ->
    if (HANGUL.containsMatchIn(w)) w in line else py("\\b${Regex.escape(w)}\\b", ignoreCase = true).containsMatchIn(line)
}

data class Mention(
    val hit: Hit,
    val entry: String?, // the entry it is an example of ("근처 = Nearby")
    val korean: String?,
    val english: String?,
    val phrase: String? = null, // the numbered sentence you study, as it is in your notes
    val section: String? = null,
)

/** Where the word appears if it ONLY appears in examples; null if an entry defines it, or a line is not an example. */
fun exampleMentions(hits: List<Hit>, words: List<String>): List<Mention>? {
    if (hits.any { defines(it.text, words) }) return null
    val found = mutableListOf<Mention>()
    val seen = mutableSetOf<Pair<String, String?>>()
    for (hit in hits) {
        val lines = hit.text.pySplitlines()
        for ((i, line) in lines.withIndex()) {
            if (!mentions(line, words)) continue
            val label = LABEL.pyMatch(line) ?: return null
            var korean: String? = null; var english: String? = null; var phrase: String? = null
            if (label.groupValues[1].lowercase() == "translation") {
                english = line.substring(label.range.last + 1).pyStrip()
                val before = if (i > 0) lines[i - 1] else ""
                val prev = LABEL.pyMatch(before)
                if (prev != null && prev.groupValues[1].lowercase() != "translation") korean = before.substring(prev.range.last + 1).pyStrip()
                else if (PHRASE.pyMatch(before) != null) phrase = before.pyStrip()
            } else {
                korean = line.substring(label.range.last + 1).pyStrip()
                val after = if (i + 1 < lines.size) lines[i + 1] else ""
                val next = LABEL.pyMatch(after)
                if (next != null && next.groupValues[1].lowercase() == "translation") english = after.substring(next.range.last + 1).pyStrip()
            }
            var entry: String? = null
            for (back in lines.subList(0, i).asReversed()) {
                if (LABEL.pyMatch(back) != null) continue
                val m = ENTRY.pyMatch(back) ?: continue
                entry = "${m.groupValues[1].pyStrip()} = ${m.groupValues[2].pyStrip()}"
                break
            }
            val mention = if (phrase != null) {
                val section = (hit.chunk.section ?: "").replaceFirst(NUMBERING, "").pyStrip()
                Mention(hit, null, null, english, phrase, section.ifEmpty { null })
            } else Mention(hit, entry, korean, english)
            if (seen.add(hit.id to (phrase ?: korean ?: english))) found += mention
        }
    }
    return found.take(MAX_MENTIONS).ifEmpty { null }
}

/** The answer, with [n] of each example's source. */
fun noEntryText(word: String, mentions: List<Mention>, numbers: Map<String, Int>): String {
    val phrases = mentions.filter { it.phrase != null }
    val head = when {
        phrases.size == mentions.size -> "Your notes don't have a vocabulary entry for \"$word\", but it appears in " +
            if (mentions.size == 1) "a phrase you study:" else "phrases you study:"
        phrases.isNotEmpty() -> "Your notes don't have a vocabulary entry for \"$word\". It appears in:"
        else -> "Your notes don't have an entry for \"$word\". It only appears in " + if (mentions.size == 1) "an example:" else "examples:"
    }
    val lines = mutableListOf(head)
    for (m in mentions) {
        if (m.phrase != null) {
            var line = "• ${m.phrase}" + (m.english?.takeIf { it.isNotEmpty() }?.let { " — $it" } ?: "")
            line += " [${numbers.getValue(m.hit.id)}]"
            if (!m.section.isNullOrEmpty()) line += " (from: ${m.section})"
            lines += line
            continue
        }
        val where = m.entry?.takeIf { it.isNotEmpty() } ?: "Your notes"
        val sentence = listOfNotNull(m.korean, m.english).filter { it.isNotEmpty() }.joinToString(" — ")
        lines += "• $where [${numbers.getValue(m.hit.id)}]: $sentence"
    }
    return lines.joinToString("\n")
}

private const val MAX_ENTRIES = 3
private const val MAX_DETAILS = 8
private val BULLET_ENTRY = py("^\\s*[-•*]\\s+[^=]*=")
private val NEW_ENTRY = py("^\\s*(\\d{1,3}[.)]\\s|#)")
private val SENSES = py("[;,/]")
private val PAREN = py("\\([^)]*\\)")

/** The line is the entry OF that word: its Korean is the word, or one of its meanings IS the word. */
private fun definesLine(line: String, words: List<String>): Boolean {
    val m = ENTRY.pyMatch(line) ?: return false
    if (!defines(line, words)) return false
    val korean = m.groupValues[1].pyStrip(); val meaning = m.groupValues[2]
    val wanted = words.map(::casefold).toSet()
    val headwords = korean.replaceFirst(NUMBERING, "").split(" (")[0].split("/")
    if (headwords.any { casefold(it.pyStrip()) in wanted }) return true
    for (raw in meaning.replace(PAREN, "").split(SENSES)) {
        val sense = casefold(raw.pyStrip())
        if (sense in wanted || (sense.startsWith("to ") && sense.substring(3) in wanted)) return true
    }
    return false
}

data class Entry(val hit: Hit, val line: String, val details: List<String>)

/** The entries of your notes that DEFINE the word, with their example, translation and note lines. */
fun definingEntries(hits: List<Hit>, words: List<String>): List<Entry> {
    val found = mutableListOf<Entry>()
    val seen = mutableSetOf<String>()
    for (hit in hits) {
        val lines = hit.text.pySplitlines().map { it.pyStrip() }.filter { it.isNotEmpty() }
        for ((i, line) in lines.withIndex()) {
            if (!definesLine(line, words) || line in seen) continue
            seen += line
            val details = mutableListOf<String>()
            for (next in lines.subList(i + 1, lines.size)) {
                if (NEW_ENTRY.pyMatch(next) != null || BULLET_ENTRY.pyMatch(next) != null || details.size == MAX_DETAILS) break
                details += next
            }
            found += Entry(hit, line, details)
            if (found.size == MAX_ENTRIES) return found
        }
    }
    return found
}

/** Each entry with its [n] and its examples, as written. */
fun entryText(found: List<Entry>, numbers: Map<String, Int>): String =
    found.joinToString("\n\n") { e -> (listOf("${e.line} [${numbers.getValue(e.hit.id)}]") + e.details).joinToString("\n") }

/** "Give me an example with it", "What does that word mean?": asks about a word the conversation says (a pronoun). */
fun asksAboutIt(question: String): Boolean {
    for (pattern in WORD_QUESTIONS + EXAMPLE_QUESTION) {
        val m = pattern.find(question.pyStrip()) ?: continue
        return casefold(m.groupValues[1].replaceFirst(LEADING, "").trim { it in QUOTES }) in PRONOUNS
    }
    return false
}
