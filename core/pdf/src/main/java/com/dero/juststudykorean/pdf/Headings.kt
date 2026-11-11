package com.dero.juststudykorean.pdf

import com.dero.juststudykorean.korean.pyLen
import com.dero.juststudykorean.korean.pySplit

// Port of _body_font_size, _is_heading, _demote_header_rows and _build_page (backend/app/rag/parser.py).

/** Three or more "headings" in a row with no text between them are the headers of a table without drawn lines. */
private const val MIN_HEADER_ROW_RUN = 3
private const val TABLE_SEPARATORS = "=—:–"

/** The most frequent font size, weighted by characters (the body text). Ties: the first one seen, like Counter. */
internal fun bodyFontSize(allLines: List<List<Line>>): Double {
    val counter = LinkedHashMap<Double, Int>()
    for (lines in allLines) for (ln in lines) counter[ln.size] = (counter[ln.size] ?: 0) + ln.text.pyLen
    var best: Map.Entry<Double, Int>? = null
    for (e in counter.entries) if (best == null || e.value > best.value) best = e
    return best?.key ?: 0.0
}

internal fun isHeading(line: Line, body: Double): Boolean {
    if (line.text.pyLen > 80 || body == 0.0) return false
    if (line.size >= body * 1.15) return true
    return line.bold && line.text.pyLen <= 50 && line.text.pySplit().size <= 6 && TABLE_SEPARATORS.none { it in line.text }
}

/**
 * Turns runs of >= 3 headings in a row into plain text. If the run has >= 3 bold-only headings, only those: the real
 * heading of a section (by font size, [bySize]) often sits right above the bold headers of its table.
 */
internal fun demoteHeaderRows(out: List<String>, headings: List<String>, bySize: Set<Int>): Pair<List<String>, List<String>> {
    val result = out.toMutableList()
    val demoted = mutableListOf<String>()
    var i = 0
    while (i < result.size) {
        var j = i
        while (j < result.size && result[j].startsWith("## ")) j++
        val boldOnly = (i until j).filter { it !in bySize }
        val row = when {
            boldOnly.size >= MIN_HEADER_ROW_RUN -> boldOnly
            j - i >= MIN_HEADER_ROW_RUN -> (i until j).toList()
            else -> emptyList()
        }
        for (k in row) {
            result[k] = result[k].substring(3)
            demoted += result[k]
        }
        i = maxOf(j, i + 1)
    }
    if (demoted.isEmpty()) return out to headings
    val remaining = headings.toMutableList()
    for (text in demoted) remaining.remove(text)
    return result to remaining
}

private fun inside(line: Line, b: Box): Boolean {
    val cx = (line.bbox.x0 + line.bbox.x1) / 2
    val cy = (line.bbox.y0 + line.bbox.y1) / 2
    return b.x0 <= cx && cx <= b.x1 && b.y0 <= cy && cy <= b.y1
}

/** The page's text without the lines already inside a table; also returns its first heading. */
internal fun buildPage(number: Int, lines: List<Line>, tables: List<Table>, body: Double, imageCount: Int): Pair<PageContent, String?> {
    val out = mutableListOf<String>()
    val headings = mutableListOf<String>()
    val bySize = mutableSetOf<Int>()
    for (ln in lines) {
        if (tables.any { inside(ln, it.bbox) }) continue
        if (isHeading(ln, body)) {
            if (body != 0.0 && ln.size >= body * 1.15) bySize += out.size
            headings += ln.text
            out += "## ${ln.text}"
        } else {
            out += ln.text
        }
    }
    val (text, kept) = demoteHeaderRows(out, headings, bySize).let { (o, h) -> o.joinToString("\n") to h }
    val page = PageContent(
        pageNumber = number, text = text, headings = kept, section = null, tables = tables, imageCount = imageCount,
        charCount = text.pyLen + tables.sumOf { it.text.pyLen },
    )
    return page to kept.firstOrNull()
}
