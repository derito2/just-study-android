package com.dero.juststudykorean.korean

import java.text.Normalizer

// Port of backend/app/quiz/notes.py: how notes are read ("207. 가장 / 제일 = The most / best", examples and
// notes below). Pure functions, no model. Checked through golden/pairs and golden/grammar.

private const val H = "ㄱ-ㆎ가-힣"
private val NOTE_HANGUL = py("[$H]")
internal val NUMBER = py("^\\s*\\d{1,3}[.)]\\s*")
internal val BULLET = py("^\\s*[-•*–—·▪●○◦]\\s+")
internal val NOTE_PREFIX = py("^\\s*note\\s*:\\s*", ignoreCase = true)
private val LABEL = py("^\\s*(?<key>meaning|example|examples|ex|translation)\\s*\\d*\\s*[:.)]\\s*(?<value>.*)$", ignoreCase = true)
internal val SEP = py("\\s*(?:=|→|->|=>|—|–)\\s*|:\\s+|\\s+-\\s+")
private val ROMAN = py("\\s*\\((?<rom>[A-Za-z][A-Za-z' .-]*)\\)\\s*$")
private val PAREN_PAIR = py("(?<ko>[$H][$H]*(?: [$H]+)?)\\s*\\((?<en>[A-Za-z][^()]*)\\)")
internal val GRAMMAR_HEAD = py("^(?:-|~|\\(으\\)|\\(이\\)|\\(스\\)|\\(느\\))\\s*[$H(]")
private val TABLE_CELL = py("\\s*\\|\\s*")
private val ARROW = py("->|→")
internal const val MAX_ENGLISH_CHARS = 120
private const val MAX_FREE_KOREAN_WORDS = 6

internal class NoteItem(
    var korean: String,
    var english: String,
    val romanization: String? = null,
    val examples: MutableList<Pair<String, String>> = mutableListOf(),
    val notes: MutableList<String> = mutableListOf(),
    val numbered: Boolean = false,
) {
    val grammar: Boolean get() = GRAMMAR_HEAD.pyMatch(korean) != null

    /** The example to show (one with a translation if any has it). */
    val example: Pair<String?, String?>
        get() = examples.firstOrNull { it.second.isNotEmpty() }?.let { it.first to it.second }
            ?: if (examples.isNotEmpty()) examples[0].first to null else null to null
}

// The stem without its last syllable when it has two or more (계산하다 -> 계산: 계산할게요, 가르치다 -> 가르: 가르쳐요).
private fun verbCore(verb: String): String = verb.dropLast(1).let { if (it.length >= 2) it.dropLast(1) else it }

/** [phrase] uses the entry [korean] (any of its "가장 / 제일" forms, also conjugated). */
internal fun usesEntry(korean: String, phrase: String): Boolean {
    val variants = koreanVariants(korean).map { it.replace(" ", "") }
    val words = phrase.split(" ").map { it.trim('.', ',', '!', '?') }
    return variants.any { v ->
        v.isNotEmpty() && (hasForm(phrase, v) ||
            (v.endsWith("다") && v.length >= 2 && (words.any { w -> w.startsWith(verbCore(v)) || aeoForms(v).any { w.startsWith(it) } || lemmas(w, setOf(v)).isNotEmpty() })))
    }
}

internal fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

internal fun cleanEnglish(text: String, maxChars: Int = MAX_ENGLISH_CHARS): String? {
    val t = text.pyStrip().trimEnd('.').pyStrip()
    if (t.isEmpty() || t.pyLen > maxChars || NOTE_HANGUL.containsMatchIn(t)) return null
    return if (LATIN.containsMatchIn(t)) t else null
}

/**
 * "학교 (hakgyo) = School" -> (학교, hakgyo, School). Korean goes first. [maxEnglish]: a longer English side is prose,
 * not a meaning; a numbered entry passes no limit (phase 8, 0b: its meaning is kept whole).
 */
internal fun splitPair(input: String, maxEnglish: Int = MAX_ENGLISH_CHARS): Triple<String, String?, String>? {
    val text = input.pyStrip()
    if (text.isEmpty() || (NOTE_HANGUL.pyMatch(text) == null && GRAMMAR_HEAD.pyMatch(text) == null)) return null
    for (sep in SEP.findAll(text)) {
        var left = text.substring(0, sep.range.first).pyStrip()
        val right = text.substring(sep.range.last + 1).pyStrip()
        if (left.isEmpty() || !NOTE_HANGUL.containsMatchIn(left)) continue
        var rom: String? = null
        ROMAN.find(left)?.let { roman ->
            rom = roman.groups["rom"]!!.value.pyStrip()
            left = left.substring(0, roman.range.first).pyStrip()
        }
        if (LATIN.containsMatchIn(left)) return null // the English is before the separator
        val english = cleanEnglish(right, maxEnglish)
        return if (english != null) Triple(left, rom, english) else null
    }
    return null
}

private fun example(text: String): Pair<String, String>? {
    if ("|" in text) { // a copied table row: "정말 기뻐요! | I'm really happy! | Sincere joy"
        val cells = text.split(TABLE_CELL).filter { it.isNotEmpty() }
        if (cells.size >= 2 && NOTE_HANGUL.containsMatchIn(cells[0]) && !NOTE_HANGUL.containsMatchIn(cells[1])) {
            val extra = if (cells.size > 2) " (${cells.drop(2).joinToString(", ")})" else ""
            return cells[0] to cells[1] + extra
        }
        return null
    }
    splitPair(text)?.let { return it.first to it.third }
    if (NOTE_HANGUL.containsMatchIn(text) && !LATIN.containsMatchIn(text)) return text to ""
    return null
}

private fun addDetail(item: NoteItem, raw: String) {
    val text = BULLET.replaceFirst(NOTE_PREFIX.replaceFirst(raw, ""), "").pyStrip()
    if (text.isEmpty()) return // phase 8, 0b: no limit on the lines of an entry
    val label = LABEL.pyMatch(text)
    if (label != null) {
        val key = label.groups["key"]!!.value.lowercase()
        val value = label.groups["value"]!!.value.pyStrip()
        when {
            key == "meaning" && item.english.isEmpty() ->
                item.english = if (item.grammar && LATIN.containsMatchIn(value)) value.trimEnd('.') else cleanEnglish(value) ?: ""
            key == "translation" && item.examples.isNotEmpty() && item.examples.last().second.isEmpty() ->
                item.examples[item.examples.lastIndex] = item.examples.last().first to value
            key in setOf("example", "examples", "ex") && value.isNotEmpty() ->
                if (aboutAnother(item, value)) item.notes += value else item.examples += example(value) ?: (value to "")
            value.isNotEmpty() -> item.notes += value
        }
        return
    }
    val ex = example(text)
    if (ex != null && !aboutAnother(item, text)) item.examples += ex else item.notes += text
}

/**
 * R1-15 (the app departs from the web): "X -> Y", "X → Y", "X - Y", "== X" or "X = Y" below a word, where X does not
 * use the word, is a note about another word (a synonym, a comparison), not an example of it.
 */
private fun aboutAnother(item: NoteItem, text: String): Boolean {
    if (item.grammar) return false
    if (!text.startsWith("==") && !ARROW.containsMatchIn(text) && splitPair(text) == null) return false
    val korean = text.removePrefix("==").split(SEP, limit = 2)[0].pyStrip()
    return korean.isNotEmpty() && !usesEntry(item.korean, korean)
}

private fun legacyHead(input: String): Pair<String, String?>? {
    var text = input
    var rom: String? = null
    ROMAN.find(text)?.let { roman ->
        rom = roman.groups["rom"]!!.value.pyStrip()
        text = text.substring(0, roman.range.first).pyStrip()
    }
    if (text.isNotEmpty() && NOTE_HANGUL.pyMatch(text) != null && !LATIN.containsMatchIn(text) && !SEP.containsMatchIn(text)) return text to rom
    return null
}

/** Entries of a piece of notes, in order, with their examples and notes. */
internal fun parseLines(lines: List<String>): List<NoteItem> {
    val items = mutableListOf<NoteItem>()
    var current: NoteItem? = null // the last numbered entry: what is below is its
    val free = lines.none { ln ->
        (NUMBER.pyMatch(ln) != null && splitPair(NUMBER.replaceFirst(nfc(ln).pyStrip(), "")) != null) || NOTE_PREFIX.pyMatch(ln) != null
    }
    for (raw in lines) {
        val line = nfc(raw).pyStrip()
        if (line.isEmpty() || line.startsWith("## ")) continue
        val numbered = NUMBER.pyMatch(line)
        if (numbered != null) {
            val text = line.substring(numbered.range.last + 1)
            val pair = splitPair(text, Int.MAX_VALUE)
            if (pair != null) {
                current = NoteItem(pair.first, pair.third, pair.second, numbered = true).also { items += it }
                continue
            }
            if (GRAMMAR_HEAD.pyMatch(text) != null && !SEP.containsMatchIn(text)) {
                current = NoteItem(text.pyStrip(), "", numbered = true).also { items += it }
                continue
            }
            val legacy = legacyHead(text)
            if (legacy != null && (current == null || current.english.isNotEmpty())) {
                current = NoteItem(legacy.first, "", legacy.second, numbered = true).also { items += it }
                continue
            }
        }
        val bullet = BULLET.pyMatch(line)
        if (bullet != null && current != null) {
            val pair = splitPair(line.substring(bullet.range.last + 1))
            if (pair != null) {
                current = NoteItem(pair.first, pair.third, pair.second, numbered = true).also { items += it }
                continue
            }
        }
        if (current != null) {
            addDetail(current, line)
            continue
        }
        val text = BULLET.replaceFirst(line, "")
        val pair = splitPair(text)
        if (pair != null && GRAMMAR_HEAD.pyMatch(text) != null && NOTE_PREFIX.pyMatch(line) == null) {
            current = NoteItem(pair.first, pair.third, pair.second, numbered = true).also { items += it }
            continue
        }
        if (!free || NOTE_PREFIX.pyMatch(line) != null) continue
        if (pair != null && pair.first.pySplit().size <= MAX_FREE_KOREAN_WORDS) {
            items += NoteItem(pair.first, pair.third, pair.second)
            continue
        }
        if (!SEP.containsMatchIn(text)) {
            for (match in PAREN_PAIR.findAll(text)) {
                cleanEnglish(match.groups["en"]!!.value)?.let { items += NoteItem(match.groups["ko"]!!.value.pyStrip(), it) }
            }
        }
    }
    return items.filter { it.english.isNotEmpty() }
}

/** "가장 / 제일" -> [가장, 제일]. */
internal fun koreanVariants(korean: String): List<String> = korean.split("/").map { it.pyStrip() }.filter { it.isNotEmpty() }.ifEmpty { listOf(korean) }

/** The section title is already a one-line grammar point: "3. -(으)ㄴ 후에 = after doing something". */
internal fun grammarTitleLine(section: String?): Boolean {
    if (section.isNullOrEmpty()) return false
    val text = NUMBER.replaceFirst(nfc(section).pyStrip(), "")
    return GRAMMAR_HEAD.pyMatch(text) != null && splitPair(text) != null
}
