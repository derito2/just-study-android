package com.dero.juststudykorean.korean

// Port of backend/app/quiz/grammar.py (extract_grammar), checked against golden/grammar/*.json.

private const val WRAPPED_LINE = 70 // a line this long was cut by the PDF: the next one goes on with it

private val G_NUMBERED = py("^\\s*\\d{1,3}[.)]\\s*(?<rest>.+)$")
private val G_BULLET = py("^\\s*[●•○◦▪\\-*]\\s*")
internal val FIELD = py("^\\s*(?<key>[A-Za-z][A-Za-z ]{1,30}?)\\s*:\\s*(?<value>.*)$")
private val G_LATIN = py("[A-Za-z]{2,}") // a word in English (Vㄴ/는대요 is still Korean)
private val TRANSLATION = py("^\\s*[○◦•\\-*]?\\s*Translation\\s*:\\s*(?<value>.+)$", ignoreCase = true)
private val TABLE_HEADER = setOf("particle", "main function", "function", "example", "examples", "meaning")
private val CHOICE = py("^\\s*(?<letter>[A-F])\\)\\s*(?<text>.+?)\\s*$")
private val ENDS_SENTENCE = py("[.!?:)\\]\"”]$") // as Reference.kt: a line that ended is not wrapped
private val NUMBER_PREFIX = py("^\\s*\\d{1,3}[.)]\\s*")

/** A pattern ("-(으)면서") or a particle-table row ("을/를"); [choices]: the options written in your notes. */
data class GrammarPoint(
    val kind: String,
    val pattern: String,
    val meaning: String,
    val forms: List<String> = emptyList(),
    val examples: List<Pair<String, String>> = emptyList(),
    val explanation: String? = null,
    val choices: List<String> = emptyList(),
    val choiceAnswer: String? = null,
) {
    /** The examples with a translation. */
    val translated: List<Pair<String, String>> get() = examples.filter { it.second.isNotEmpty() }
}

private fun body(chunk: Chunk): List<String> {
    var lines = chunk.text.pySplitlines().filter { it.pyStrip().isNotEmpty() }.map { nfc(it).pyStrip() }
    if (!chunk.section.isNullOrEmpty() && lines.isNotEmpty() && lines[0] == nfc(chunk.section).pyStrip()) lines = lines.drop(1)
    return lines
}

private fun pattern(chunk: Chunk, lines: List<String>): GrammarPoint? {
    val pattern = NUMBER_PREFIX.replaceFirst(nfc(chunk.section ?: ""), "").pyStrip()
    var meaning = ""
    val forms = mutableListOf<String>()
    val examples = mutableListOf<Array<String>>()
    val explanation = mutableListOf<String>()
    val choices = linkedMapOf<String, String>()
    var choiceLetter = ""
    var part = ""
    for (raw in lines) {
        val line = NOTE_PREFIX.replaceFirst(raw, "").pyStrip()
        if (line.isEmpty()) continue
        if (part == "choices") {
            val choice = CHOICE.pyMatch(line)
            if (choice != null) { choices.putIfAbsent(choice.groups["letter"]!!.value, choice.groups["text"]!!.value); continue }
        }
        val translation = TRANSLATION.pyMatch(line)
        if (translation != null && examples.isNotEmpty()) {
            examples.last()[1] = translation.groups["value"]!!.value.pyStrip()
            part = "translation"
            continue
        }
        val field = FIELD.pyMatch(line)
        val key = field?.groups?.get("key")?.value?.pyStrip()?.lowercase() ?: ""
        val value = field?.groups?.get("value")?.value?.pyStrip() ?: ""
        if (field != null && key == "anki options") { part = "choices"; continue }
        if (field != null && key == "anki answer") { choiceLetter = value.uppercase(); part = ""; continue }
        if (field != null && key in setOf("meaning", "tense and structure", "structure", "examples", "example")) {
            part = if (key == "meaning") "meaning" else if ("structure" in key) "forms" else "examples"
            if (part == "meaning") meaning = value
            continue
        }
        if (field != null && key in setOf("explanation", "usage")) {
            part = "explanation"
            if (value.isNotEmpty()) explanation += value
            continue
        }
        val pair = if (part in setOf("examples", "translation", "meaning")) splitPair(G_NUMBERED.replaceFirst(line, "\${rest}")) else null
        when {
            part == "forms" -> {
                val text = G_BULLET.replaceFirst(line, "")
                if (text.lowercase().startsWith("particles")) continue
                if (G_BULLET.pyMatch(line) != null || forms.isEmpty()) forms += text else forms[forms.lastIndex] = forms.last() + " " + text
            }
            pair != null -> { examples += arrayOf(pair.first, pair.third); part = "examples" }
            part in setOf("examples", "translation") -> {
                val numbered = G_NUMBERED.pyMatch(line)
                when {
                    numbered != null && HANGUL.containsMatchIn(numbered.groups["rest"]!!.value) -> {
                        examples += arrayOf(numbered.groups["rest"]!!.value.pyStrip(), ""); part = "examples"
                    }
                    examples.isNotEmpty() && part == "translation" -> examples.last()[1] += " $line"
                    // A Korean-only line of its own unless the one before filled the line and did not end (the PDF wrapped it).
                    HANGUL.containsMatchIn(line) && !G_LATIN.containsMatchIn(line) &&
                        (examples.isEmpty() || examples.last()[0].pyLen < WRAPPED_LINE || ENDS_SENTENCE.find(examples.last()[0]) != null) ->
                        examples += arrayOf(line, "") // a Korean-only line of its own: another example
                    // R1-21: English never goes on the Korean side; a wrapped translation goes on with the translation.
                    examples.isNotEmpty() && !HANGUL.containsMatchIn(line) -> examples.last()[1] = "${examples.last()[1]} $line".pyStrip()
                    examples.isNotEmpty() -> examples.last()[0] += " $line"
                }
            }
            part == "explanation" -> explanation += line
            part == "meaning" && HANGUL.containsMatchIn(line) && !G_LATIN.containsMatchIn(line) -> { examples += arrayOf(line, ""); part = "examples" }
            part == "meaning" -> meaning += " $line"
        }
    }
    meaning = meaning.pyStrip()
    if (meaning.isEmpty() || !HANGUL.containsMatchIn(pattern)) return null
    // The options in your notes count only if complete and the right one is this pattern.
    val right = choices[choiceLetter]
    val useChoices = choices.size >= 2 && right != null && norm(right) == norm(pattern)
    return GrammarPoint(
        kind = "pattern",
        pattern = pattern,
        meaning = meaning, // phase 8, 0b: whole; the screen decides how much it shows
        forms = forms.filter { HANGUL.containsMatchIn(it) },
        examples = examples.filter { it[0].pyStrip().isNotEmpty() }.map { it[0].pyStrip() to it[1].pyStrip() },
        explanation = explanation.joinToString(" ").ifEmpty { null },
        choices = if (useChoices) choices.keys.sorted().map { choices.getValue(it) } else emptyList(),
        choiceAnswer = if (useChoices) right else null,
    )
}

private fun particles(lines: List<String>): List<GrammarPoint> {
    val rows = mutableListOf<MutableList<String>>() // [particle, texts...]
    for (line in lines) {
        if (line.lowercase() in TABLE_HEADER) continue
        if (rows.isNotEmpty() && FIELD.pyMatch(line) != null && !HANGUL.containsMatchIn(line.split(":", limit = 2)[0])) break // "Study tip: …"
        if (PARTICLE_CELL.pyMatch(line) != null) {
            if (rows.isNotEmpty() && rows.last().size == 1 && line.pyLen <= 2) rows.last()[0] += line // "에게/한" + "테"
            else rows += mutableListOf(line)
        } else if (rows.isNotEmpty()) rows.last() += line
    }
    val points = mutableListOf<GrammarPoint>()
    for (row in rows) {
        val joined = row.drop(1).joinToString(" ")
        if (" — " !in joined) continue
        val (left, english) = joined.split(" — ", limit = 2)
        val first = HANGUL.find(left) ?: continue
        val function = left.substring(0, first.range.first).pyStrip()
        val korean = left.substring(first.range.first).pyStrip()
        if (function.isNotEmpty() && korean.isNotEmpty()) {
            points += GrammarPoint("particle", row[0], function, examples = listOf(korean to english.pyStrip()))
        }
    }
    return points
}

/** Grammar written on one line, like vocabulary: "-지만 = But" (examples below). */
private fun oneLiners(lines: List<String>): List<GrammarPoint> = parseLines(lines).filter { it.grammar }.map {
    GrammarPoint("pattern", it.korean, it.english, examples = it.examples.toList(), explanation = it.notes.joinToString(" ").ifEmpty { null })
}

private val grammarCache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Chunk, List<GrammarPoint>>()) // as extractPairs'

/** Grammar points of a chunk. */
fun extractGrammar(chunk: Chunk): List<GrammarPoint> = grammarCache.getOrPut(chunk) { readGrammar(chunk) }

private fun readGrammar(chunk: Chunk): List<GrammarPoint> {
    var lines = body(chunk)
    if (isGrammarEntry(chunk.section, lines)) return listOfNotNull(pattern(chunk, lines))
    if (isParticleTable(lines)) return particles(lines)
    if (grammarTitleLine(chunk.section)) lines = listOf(chunk.section!!) + lines // "3. -(으)ㄴ 후에 = …" as title
    return oneLiners(lines)
}
