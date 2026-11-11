package com.dero.juststudykorean.korean

// Port of backend/app/grammar/reference.py: every grammar point of your notes, whole and as written (the Grammar
// screen and grammar_points). The same detection as the quiz, keeping all the content. Pure functions.

data class ReferencePoint(
    val kind: String, // pattern | particle
    val pattern: String,
    var meaning: String,
    val pageNumber: Int,
    val structure: MutableList<String> = mutableListOf(),
    val examples: MutableList<MutableList<String>> = mutableListOf(), // [korean, translation]
    val explanation: MutableList<String> = mutableListOf(),
    val notes: MutableList<String> = mutableListOf(),
    var category: String = "others",
)

private val NUMBERED = py("^\\s*\\d{1,3}[.)]\\s*(?<rest>.+)$")
private val NUMBER_PREFIX = py("^\\s*\\d{1,3}[.)]\\s*")
private val REF_BULLET = py("^\\s*[●•○◦▪\\-*]\\s*")
private val REF_FIELD = py("^\\s*[●•○◦▪\\-*]?\\s*(?<key>[A-Za-z][A-Za-z ]{1,30}?)\\s*:\\s*(?<value>.*)$")
private val CHOICE = py("^\\s*[A-F]\\)\\s*")
private val SENTENCE_END = py("[.!?:)\\]\"”]$")
private val TWO_LATIN = py("[A-Za-z]{2,}")

private val MEANING_KEYS = setOf("meaning")
private val STRUCTURE_KEYS = setOf("tense and structure", "structure", "forms", "form")
private val EXAMPLE_KEYS = setOf("examples", "example")
private val EXPLANATION_KEYS = setOf("explanation", "usage", "usage notes")

/** A line this long filled the page width: if it didn't end the sentence, the next one continues it. */
private const val WRAPPED_LINE = 70

private fun lines(chunk: Chunk): List<String> {
    val lines = chunk.text.pySplitlines().filter { it.pyStrip().isNotEmpty() }.map { nfc(it).pyStrip() }
    return if (chunk.section != null && lines.isNotEmpty() && lines[0] == nfc(chunk.section).pyStrip()) lines.drop(1) else lines
}

private fun wrap(paragraphs: MutableList<String>, line: String, previous: String) {
    if (paragraphs.isNotEmpty() && previous.pyLen >= WRAPPED_LINE && SENTENCE_END.find(previous) == null) {
        paragraphs[paragraphs.size - 1] += " $line"
    } else {
        paragraphs += line
    }
}

private fun patternPoint(chunk: Chunk, lines: List<String>): ReferencePoint? {
    val pattern = NUMBER_PREFIX.replaceFirst(nfc(chunk.section ?: ""), "").pyStrip()
    if (!HANGUL.containsMatchIn(pattern)) return null
    val meaning = mutableListOf<String>()
    val point = ReferencePoint("pattern", pattern, "", chunk.pageNumber)
    var part = ""
    var previous = ""
    for (line in lines) {
        val before = previous
        previous = line
        val fld = REF_FIELD.pyMatch(line)
        val key = fld?.groups?.get("key")?.value?.pyStrip()?.lowercase() ?: ""
        val value = fld?.groups?.get("value")?.value?.pyStrip() ?: ""
        when {
            key == "note" -> { point.notes += value; continue }
            key == "anki options" -> { part = "choices"; continue }
            key == "anki answer" -> { part = ""; continue }
        }
        if (part == "choices" && CHOICE.pyMatch(line) != null) continue
        if (key == "translation" && point.examples.isNotEmpty() && part in setOf("examples", "")) {
            point.examples.last()[1] = value
            part = "examples"
            continue
        }
        if (key in MEANING_KEYS) { part = "meaning"; if (value.isNotEmpty()) meaning += value; continue }
        if (key in STRUCTURE_KEYS) { part = "structure"; if (value.isNotEmpty()) point.structure += value; continue }
        if (key in EXAMPLE_KEYS) { part = "examples"; if (value.isNotEmpty()) point.examples += mutableListOf(value, ""); continue }
        if (key in EXPLANATION_KEYS) { part = "explanation"; if (value.isNotEmpty()) point.explanation += value; continue }
        val pair = if (part != "choices") splitPair(NUMBERED.replace(line) { it.groups["rest"]!!.value }) else null
        if (pair != null && part in setOf("meaning", "examples", "")) {
            // Simple format: "먹는 것 같아요 = I think that I'm eating", with or without "Examples:".
            point.examples += mutableListOf(pair.first, pair.third)
            part = "examples"
            continue
        }
        if (part == "meaning" && HANGUL.containsMatchIn(line) && TWO_LATIN.find(line) == null) {
            point.examples += mutableListOf(line, "") // "비싸네요" under Meaning
            part = "examples"
            continue
        }
        when (part) {
            "meaning" -> wrap(meaning, line, before)
            "structure" -> if (REF_BULLET.pyMatch(line) != null || point.structure.isEmpty()) point.structure += REF_BULLET.replaceFirst(line, "")
            else point.structure[point.structure.size - 1] += " $line"
            "examples" -> {
                val numbered = NUMBERED.pyMatch(line)
                if (numbered != null && HANGUL.containsMatchIn(numbered.groups["rest"]!!.value)) {
                    point.examples += mutableListOf(numbered.groups["rest"]!!.value.pyStrip(), "")
                } else if (point.examples.isNotEmpty() && before.pyLen >= WRAPPED_LINE && SENTENCE_END.find(before) == null) {
                    val last = point.examples.last() // the PDF split the previous line: this continues it
                    val i = if (last[1].isNotEmpty()) 1 else 0
                    last[i] = last[i] + " " + line
                } else if (HANGUL.containsMatchIn(line)) {
                    point.examples += mutableListOf(line, "")
                } else if (point.examples.isNotEmpty()) {
                    point.examples.last()[1] = (point.examples.last()[1] + " " + line).pyStrip()
                }
            }
            "explanation" -> wrap(point.explanation, line, before)
        } // outside any part (before "Meaning:"): not the point's content
    }
    point.meaning = meaning.joinToString(" ").pyStrip()
    return point.takeIf { it.meaning.isNotEmpty() }
}

/** The grammar points of a chunk, whole, with their category. */
fun referencePoints(chunk: Chunk): List<ReferencePoint> {
    val lines = lines(chunk)
    val points = mutableListOf<ReferencePoint>()
    if (isGrammarEntry(chunk.section, lines)) {
        patternPoint(chunk, lines)?.let { points += it }
    } else {
        // Particle table: the same rows the quiz uses (particle, function, example).
        for (p in extractGrammar(chunk)) {
            val examples = p.examples.map { mutableListOf(it.first, it.second) }.toMutableList()
            if (p.kind == "pattern") { // "-지만 = But" on one line, with its examples below
                points += ReferencePoint("pattern", p.pattern, p.meaning, chunk.pageNumber, examples = examples,
                    explanation = listOfNotNull(p.explanation?.takeIf { it.isNotEmpty() }).toMutableList())
            } else if (p.kind == "particle") {
                points += ReferencePoint("particle", p.pattern, p.meaning, chunk.pageNumber, examples = examples)
            }
        }
    }
    for (point in points) point.category = categoryOf(point)
    return points
}

private val PARTICLE_WORDS = py("\\b(particle|marker)s?\\b", ignoreCase = true)
private val ENDING_WORDS = py("\\bendings?\\b", ignoreCase = true)
private val ENDING_FORM = py("(요\\)?|죠)\\s*$")
private val TENSE_WORDS = py("\\b(past|present|future|tenses?|progressive|ongoing|continuous)\\b", ignoreCase = true)
private val TENSE_FORM = py("았|었|겠|[ㄹ을] 거|고 있|[아어] 있")

/** By rules, the first that holds: particles, endings (meaning), tenses, endings (form), others. */
fun categoryOf(point: ReferencePoint): String = when {
    point.kind == "particle" || PARTICLE_WORDS.containsMatchIn(point.meaning) -> "particles"
    ENDING_WORDS.containsMatchIn(point.meaning) -> "endings"
    TENSE_WORDS.containsMatchIn(point.meaning) || TENSE_FORM.containsMatchIn(point.pattern) -> "tenses"
    ENDING_FORM.containsMatchIn(point.pattern) -> "endings"
    else -> "others"
}
