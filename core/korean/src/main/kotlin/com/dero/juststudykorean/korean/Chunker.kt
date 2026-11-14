package com.dero.juststudykorean.korean

// Port of backend/app/rag/chunker.py (extracted text -> chunks with metadata, deterministic rules),
// checked against golden/chunks/*.json.

const val MAX_CHARS = 450
const val GRAMMAR_MAX_CHARS = 1500
const val GENERAL_TOPIC = "General"

internal val HANGUL = py("[\\u3131-\\u318e\\uac00-\\ud7a3]")
internal val LATIN = py("[A-Za-z]")
private val UNIT_START = py("^(\\d{1,3}[.)]|[-•*])\\s+")
private val HEADING = py("^## (.+)$")
private val QUESTION_START = py(
    "^\\s*(\\d{1,3}[.)]\\s*)?(what|which|how|why|choose|select|complete|fill|translate|write|match|circle|identify)\\b",
    ignoreCase = true,
)
internal val GRAMMAR_WORDS = py(
    "\\b(rule|rules|particle|particles|grammar|conjugat\\w*|tense|honorific|marker|attach(es|ed)?|pattern)\\b",
    ignoreCase = true,
)
internal val PARTICLE_PAIR = py("[\\u3131-\\ud7a3]/[\\u3131-\\ud7a3]")
internal val VOCAB_LINE = py("^[\\d.)\\s-]*[\\u3131-\\ud7a3][^=—–:]*?\\s*(=|—|–|-|:)\\s*\\S|^[\\d.)\\s]*[\\u3131-\\ud7a3]+\\s*\\([A-Za-z' -]+\\)")
private val EXAMPLE_LINE = py("^\\s*(example|examples|e\\.g\\.|예문)\\b", ignoreCase = true, multiline = true)
private val PATTERN_HEADING = py(
    "(^|[\\s.])-\\(?[\\u3131-\\ud7a3]|\\(으\\)|[\\u3131-\\u318e]|^\\s*(?:#+\\s*)?(?:\\d{1,3}[.)]\\s*)?[가-힣]{1,3}(?:\\s*/\\s*[가-힣]{1,3})*\\s*$",
)
internal val MEANING_LINE = py("^\\s*Meaning\\s*:", ignoreCase = true)
private val GRAMMAR_PART = py("^\\s*(Examples?|Explanation|Tense and structure|Structure|Usage)\\s*:", ignoreCase = true)
private val EXAMPLE_PAIR = py("^\\s*(\\d{1,3}[.)]\\s*)?[\\u3131-\\ud7a3][^=A-Za-z]*=\\s*[A-Za-z]")
private val KOREAN_ONLY = py("^[^A-Za-z]*[\\u3131-\\ud7a3][^A-Za-z]*$")
internal val PARTICLE_CELL = py("^(?:\\([으이]\\))?[\\uac00-\\ud7a3]{1,3}(/(?:\\([으이]\\))?[\\uac00-\\ud7a3]{1,3})?$")
private val NUMBERED_UNIT = py("^\\d{1,3}[.)]\\s")
private val NUMBERED_PATTERN = py("^\\s*\\d{1,3}[.)]\\s*(?:-|~|\\(으\\)|\\(이\\))\\s*\\(?[\\u3131-\\ud7a3][^=]*$")
private val SENTENCE_END = py("[.!?](\\s|$)")

/** One page of the extracted text (data/extracted, the parser's output). Only what chunking reads. */
data class ExtractedPage(val pageNumber: Int, val section: String?, val text: String, val tables: List<String> = emptyList())

data class Chunk(
    val documentId: Long,
    val filename: String,
    val pageNumber: Int,
    val index: Int,
    val section: String?,
    val topic: String,
    val contentType: String,
    val language: String,
    val text: String,
) {
    val chromaId: String get() = "$documentId:$pageNumber:$index"

    fun metadata(): Map<String, Any> = linkedMapOf(
        "document_id" to documentId, "filename" to filename, "page_number" to pageNumber, "section" to (section ?: ""),
        "topic" to topic, "content_type" to contentType, "language" to language, "source_type" to "pdf",
    )
}

fun isGrammarEntry(section: String?, lines: List<String>): Boolean =
    !section.isNullOrEmpty() &&
        HANGUL.containsMatchIn(section) &&
        PATTERN_HEADING.containsMatchIn(section) &&
        lines.any { MEANING_LINE.pyMatch(it) != null } &&
        lines.any { GRAMMAR_PART.pyMatch(it) != null || EXAMPLE_PAIR.pyMatch(it) != null || KOREAN_ONLY.pyMatch(it) != null }

fun isParticleTable(lines: List<String>): Boolean {
    val cells = lines.count { PARTICLE_CELL.pyMatch(it.pyStrip()) != null }
    val examples = lines.count { " — " in it }
    return cells >= 3 && examples >= 3
}

fun cleanTopic(section: String?): String = (section ?: "").pySplit().joinToString(" ").pyTake(120).ifEmpty { GENERAL_TOPIC }

fun detectLanguage(text: String): String {
    val hangul = HANGUL.findAll(text).count()
    val latin = LATIN.findAll(text).count()
    if (hangul == 0) return "en"
    return if (hangul.toDouble() / (hangul + latin) >= 0.6) "ko" else "ko-en"
}

fun classify(text: String, isTable: Boolean = false): String {
    if (isTable) return "table"
    val lines = text.pySplitlines().filter { it.pyStrip().isNotEmpty() }
    val hasMeaning = "Meaning:" in text
    if (!hasMeaning && (
            "___" in text ||
                lines.any { QUESTION_START.pyMatch(it) != null && it.pyRstrip().let { r -> r.endsWith("?") || r.endsWith(":") || r.endsWith(".") } } ||
                lines.any { it.pyRstrip().endsWith("?") }
            )
    ) return "question"
    if (GRAMMAR_WORDS.containsMatchIn(text) || PARTICLE_PAIR.containsMatchIn(text)) return "grammar"
    if (hasMeaning || (lines.isNotEmpty() && lines.count { VOCAB_LINE.pyMatch(it) != null }.toDouble() / lines.size >= 0.5)) return "vocabulary"
    if (EXAMPLE_LINE.containsMatchIn(text)) return "example"
    val latin = LATIN.findAll(text).count()
    val sentences = SENTENCE_END.findAll(text).count()
    if (text.pyLen >= 120 && sentences >= 2 && latin.toDouble() / maxOf(text.pyLen, 1) >= 0.4) return "explanation"
    return "general"
}

private fun splitWords(line: String, maxChars: Int): List<String> {
    val pieces = mutableListOf<String>()
    var current = ""
    for (token in line.split(" ")) {
        val candidate = "$current $token".pyStrip()
        if (candidate.pyLen > maxChars && current.isNotEmpty()) {
            pieces += current
            current = token
        } else {
            current = candidate
        }
    }
    if (current.isNotEmpty()) pieces += current
    return pieces
}

private fun splitLong(unit: String, maxChars: Int): List<String> {
    if (unit.pyLen <= maxChars) return listOf(unit)
    val pieces = mutableListOf<String>()
    var current = ""
    for (line in unit.split("\n")) {
        for (part in if (line.pyLen <= maxChars) listOf(line) else splitWords(line, maxChars)) {
            val candidate = if (current.isNotEmpty()) "$current\n$part" else part
            if (candidate.pyLen > maxChars && current.pyStrip().isNotEmpty()) {
                pieces += current.pyStrip()
                current = part
            } else {
                current = candidate
            }
        }
    }
    if (current.pyStrip().isNotEmpty()) pieces += current.pyStrip()
    return pieces
}

private fun unitPieces(unit: String, maxChars: Int): List<String> {
    val head = unit.split("\n", limit = 2)[0]
    if (NUMBERED_UNIT.pyMatch(head) == null || head.pyLen > maxChars / 3) return splitLong(unit, maxChars)
    if (unit.pyLen <= GRAMMAR_MAX_CHARS) return listOf(unit)
    val body = unit.substring(head.length).trimStart('\n')
    return splitLong(body, maxChars - head.pyLen - 1).map { "$head\n$it" }
}

private fun pack(units: List<String>, maxChars: Int, sep: String = "\n"): List<String> {
    val chunks = mutableListOf<String>()
    var current = ""
    for (unit in units) {
        for (piece in unitPieces(unit, maxChars)) {
            if (current.isNotEmpty() && current.pyLen + sep.pyLen + piece.pyLen > maxChars) {
                chunks += current
                current = piece
            } else {
                current = if (current.isNotEmpty()) "$current$sep$piece" else piece
            }
        }
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
}

private fun units(lines: List<String>): List<String> {
    val out = mutableListOf<String>()
    for (line in lines) if (out.isEmpty() || UNIT_START.pyMatch(line) != null) out += line else out[out.lastIndex] = out.last() + "\n" + line
    return out
}

private fun grammarTitle(line: String, after: List<String>) = NUMBERED_PATTERN.pyMatch(line) != null && after.any { MEANING_LINE.pyMatch(it) != null }

private fun pageSegments(page: ExtractedPage): List<Pair<String?, List<String>>> {
    var section = page.section
    val segments = mutableListOf<Pair<String?, MutableList<String>>>(section to mutableListOf())
    val lines = page.text.pySplitlines()
    lines.forEachIndexed { i, line ->
        val heading = HEADING.pyMatch(line)
        when {
            heading != null -> { section = heading.groupValues[1].pyStrip(); segments += section to mutableListOf() }
            grammarTitle(line, lines.subList(minOf(i + 1, lines.size), minOf(i + 3, lines.size))) -> {
                section = line.pyStrip(); segments += section to mutableListOf()
            }
            line.pyStrip().isNotEmpty() -> segments.last().second += line.pyStrip()
        }
    }
    return segments.filter { it.second.isNotEmpty() }
}

private fun openEntry(lines: List<String>): String? {
    for (line in lines.asReversed()) {
        if (NUMBERED_UNIT.pyMatch(line) != null) return if (line.pyLen <= MAX_CHARS / 3) line else null
        if (UNIT_START.pyMatch(line) != null) return null
    }
    return null
}

private class Segment(val page: Int, val section: String?, var lines: List<String>, var lastOfPage: Boolean)

private fun documentSegments(pages: List<ExtractedPage>): List<Segment> {
    val segments = mutableListOf<Segment>()
    for (page in pages) {
        for ((position, seg) in pageSegments(page).withIndex()) {
            val section = seg.first
            var lines = seg.second
            val previous = segments.lastOrNull()
            val continues = position == 0 && previous != null && previous.lastOfPage && previous.section == section
            if (continues && isGrammarEntry(section, previous!!.lines + lines)) {
                previous.lines = previous.lines + lines
                continue
            }
            if (continues && UNIT_START.pyMatch(lines[0]) == null) {
                // The entry from the previous page goes on here: its lines go back with it.
                if (openEntry(previous!!.lines) != null) {
                    val cut = lines.indexOfFirst { UNIT_START.pyMatch(it) != null }.let { if (it < 0) lines.size else it }
                    previous.lines = previous.lines + lines.subList(0, cut)
                    lines = lines.subList(cut, lines.size)
                    if (lines.isEmpty()) continue
                }
            }
            previous?.lastOfPage = false
            segments += Segment(page.pageNumber, section, lines, true)
        }
        segments.lastOrNull()?.let { it.lastOfPage = it.page == page.pageNumber }
    }
    return segments
}

private data class Piece(val section: String?, val body: String, val isTable: Boolean, val kind: String?)

private fun segmentPieces(segment: Segment, maxChars: Int): List<Piece> {
    val (section, lines) = segment.section to segment.lines
    val kind = when {
        isGrammarEntry(section, lines) -> "grammar"
        isParticleTable(lines) -> "table"
        else -> return pack(units(lines), maxChars).map { Piece(section, it, false, null) }
    }
    val body = lines.joinToString("\n")
    // Phase 8, 0b: a grammar point stays in one chunk however long, or the pieces after its "Meaning:" are not read.
    val bodies = if (kind == "grammar" || body.pyLen <= GRAMMAR_MAX_CHARS) listOf(body) else pack(units(lines), GRAMMAR_MAX_CHARS)
    return bodies.map { Piece(section, it, false, kind) }
}

/** Chunks never cross sections and, except a grammar point that goes on in the next page, never pages. */
fun chunkDocument(documentId: Long, filename: String, pages: List<ExtractedPage>, maxChars: Int = MAX_CHARS): List<Chunk> {
    val byPage = linkedMapOf<Int, MutableList<Piece>>()
    for (segment in documentSegments(pages)) byPage.getOrPut(segment.page) { mutableListOf() } += segmentPieces(segment, maxChars)
    val chunks = mutableListOf<Chunk>()
    for (page in pages) {
        val pieces = byPage[page.pageNumber].orEmpty().toMutableList()
        for (table in page.tables) {
            val blocks = table.split("\n\n").filter { it.pyStrip().isNotEmpty() }
            pieces += pack(blocks, maxChars, sep = "\n\n").map { Piece(page.section, it, true, null) }
        }
        pieces.filter { it.body.pyStrip().pyLen >= 2 }.forEachIndexed { i, p ->
            val text = if (!p.section.isNullOrEmpty()) "${p.section}\n${p.body}" else p.body
            chunks += Chunk(
                documentId, filename, page.pageNumber, i, p.section, cleanTopic(p.section),
                p.kind ?: classify(text, isTable = p.isTable), detectLanguage(text), text,
            )
        }
    }
    return chunks
}
