package com.dero.juststudykorean.korean.teacher

import com.dero.juststudykorean.korean.ReferencePoint
import com.dero.juststudykorean.korean.aeoForms
import com.dero.juststudykorean.korean.lemmas
import com.dero.juststudykorean.korean.py
import com.dero.juststudykorean.korean.pyMatch
import com.dero.juststudykorean.korean.pySplitlines
import com.dero.juststudykorean.korean.pyStrip
import com.dero.juststudykorean.korean.pyTake
import com.dero.juststudykorean.korean.referencePoints

// Port of backend/app/rag/tutor.py: NotesIndex and build_index, is_sentence, the grammar points of a fragment
// (_points, grammar_lines, grammar_point) and the sources of an answer (select_sources, format_sources).

internal val RULE_BITS = py("[ㄱ-ㅎㅏ-ㅣ]|[가-힣ㄱ-ㅎ]/[가-힣ㄱ-ㅎ]|^\\s*[VAN](?=[\\s가-힣])|->|→|\\+")

// Explanation lines of your notes, not examples ("Explanation: Attach -기 전에…").
internal val LABELED = py(
    "^\\s*(?:[-•*●○▪■◦]\\s*)?(?:explanation|meaning|structure|notes?|usage|form|forms|question|answer|tip|rule|conjugation|" +
        "interrogative|imperative|declarative|propositive|present|past|future|polite|casual|formal|informal|negative|positive)s?\\b[^:=]{0,20}:",
    ignoreCase = true,
)
private val TITLE = py("^\\s*(?:#+\\s*)?(?:[●○▪■◦•*]\\s*)?(?:\\d{1,3}[.)]\\s*)?(?:[-~](?=\\s*[가-힣ㄱ-ㅎ(])|\\((?:으|이)\\))")
private val TWO_LATIN = py("[A-Za-z]{2,}")
private val LATIN_START = py("^\\W*[A-Za-z]")
private val LEAD_NUMBER = py("^[\\s\\d.)]*")
private val CONJUGATED_ONE = py("[가-힣]{2,}요")

// Two or more Korean words in a row: a sentence. In an example it must be in your notes as written.
internal val KOREAN_PHRASE = py("[가-힣]+(?: [가-힣]+)+")

// "415. 지치다 = To be exhausted", "바쁘다 = To be busy", "모르다 / 몰라요 = ...".
private val VERB_ENTRY = py("^[\\s\\d.)•*-]*([가-힣][가-힣\\s/,;]{0,30}?)\\s*(?:\\(|=)")
private val VERB_PIECES = py("[/,;]")
private val DICTIONARY_VERB = py("[가-힣]+다")

/** An example sentence, not a rule: "제가 할 거예요 = I will do it" yes; "V 는 거야 Present" no. */
fun isSentence(line: String): Boolean {
    val korean = line.split("=", limit = 2)[0]
    if (TITLE.pyMatch(korean) != null || LABELED.pyMatch(korean) != null) return false
    if (TWO_LATIN.findAll(korean).count() >= 2 || LATIN_START.pyMatch(korean) != null) return false
    if (RULE_BITS.containsMatchIn(korean)) return false
    val one = korean.replaceFirst(LEAD_NUMBER, "").trim(' ', '.', '?', '!')
    if ("=" in line && CONJUGATED_ONE.matches(one)) return true
    return KOREAN_PHRASE.containsMatchIn(korean)
}

/** The grammar points of a fragment found by a search (with its meaning from your notes). */
fun points(hit: Hit): List<ReferencePoint> =
    referencePoints(hit.chunk.copy(index = 0, section = hit.chunk.section?.ifEmpty { null }, text = hit.text))

/** A grammar point of your notes, as written, for an answer (cites [n]). */
fun grammarLines(point: ReferencePoint, n: Int): List<String> {
    val lines = mutableListOf("${point.pattern} = ${point.meaning} [$n]")
    if (point.structure.isNotEmpty()) {
        lines += "Structure:"
        lines += point.structure.map { "• $it" }
    }
    val examples = point.examples.filter { !RULE_BITS.containsMatchIn(it[0]) }
    val forms = point.examples.filter { RULE_BITS.containsMatchIn(it[0]) }.map { it[0] }
    if (examples.isNotEmpty()) {
        lines += "Examples:"
        lines += examples.map { (ko, en) -> if (en.isNotEmpty()) "• $ko = $en" else "• $ko" }
    }
    if (forms.isNotEmpty()) {
        lines += "Forms:"
        lines += forms.map { "• $it" }
    }
    if (point.explanation.isNotEmpty()) lines += "Explanation: " + point.explanation.joinToString(" ")
    lines += point.notes
    return lines
}

/** (fragment, point) of the grammar point you ask about, written as you like: "-거든요" finds "-거든(요)". */
fun grammarPoint(hits: List<Hit>, word: String): Pair<Hit, ReferencePoint>? {
    for (hit in hits) for (point in points(hit)) if (sameGrammar(word, point.pattern)) return hit to point
    return null
}

const val MAX_EXAMPLES = 4
const val MAX_EXAMPLE_LINE = 140

/** Everything in your notes, so as not to depend on what the search brings: grammar points, sentences and your verbs. */
class NotesIndex(
    val hits: List<Hit> = emptyList(),
    val points: List<Pair<Hit, ReferencePoint>> = emptyList(),
    val sentences: List<Pair<String, Hit>> = emptyList(),
    val verbs: Set<String> = emptySet(),
    val aeo: Set<String> = emptySet(),
) {
    private val lemmaCache = HashMap<String, List<String>>()

    /** Which verbs of your notes a word comes from (cached: asked many times). */
    fun lemmasOf(word: String): List<String> = lemmaCache.getOrPut(word) {
        lemmas(word, verbs) + if (word in verbs) listOf(word) else emptyList()
    }

    fun sharesVerb(korean: String, mine: Set<String>): Boolean =
        mine.isNotEmpty() && HANGUL_WORDS.findAll(korean).any { w -> lemmasOf(w.value).any { it in mine } }

    companion object {
        private val HANGUL_WORDS = py("[가-힣]+")
    }
}

fun buildIndex(hits: List<Hit>): NotesIndex {
    val points = mutableListOf<Pair<Hit, ReferencePoint>>()
    val sentences = mutableListOf<Pair<String, Hit>>()
    val verbs = linkedSetOf<String>()
    val seen = mutableSetOf<String>()
    for (hit in hits) {
        points += points(hit).map { hit to it }
        for (raw in hit.text.pySplitlines()) {
            val line = raw.pyStrip()
            if (line !in seen && line.length <= MAX_EXAMPLE_LINE && isSentence(line)) {
                seen += line
                sentences += line to hit
            }
            val m = VERB_ENTRY.pyMatch(line) ?: continue
            for (piece in m.groupValues[1].split(VERB_PIECES).map { it.pyStrip() }) {
                if (DICTIONARY_VERB.matches(piece) && !piece.endsWith("니다")) verbs += piece
            }
        }
    }
    return NotesIndex(hits.toList(), points, sentences, verbs, verbs.flatMap(::aeoForms).toSet())
}

data class Source(val n: Int, val hit: Hit, var cited: Boolean = false)

/** Fragments within the threshold (or with a word of the question) that fit the context budget, in the search's order. */
fun selectSources(hits: List<Hit>, maxDistance: Double, maxChars: Int): List<Source> {
    val sources = mutableListOf<Source>()
    var used = 0
    for (found in hits) {
        if (found.distance > maxDistance && !found.lexical) continue
        var hit = found
        if (sources.isEmpty()) hit = hit.copy(text = hit.text.pyTake(maxChars))
        else if (used + hit.text.length > maxChars) continue
        sources += Source(sources.size + 1, hit)
        used += hit.text.length
    }
    return sources
}

/** The sources as the model's context: "[1] notes.pdf, page 2 (vocabulary)" and the text (phase 6). */
fun formatSources(sources: List<Source>): String = sources.joinToString("\n\n") { s ->
    val c = s.hit.chunk
    var header = "[${s.n}] ${c.filename}, page ${c.pageNumber}"
    if (c.contentType.isNotEmpty()) header += " (${c.contentType})"
    "$header\n${s.hit.text}"
}
