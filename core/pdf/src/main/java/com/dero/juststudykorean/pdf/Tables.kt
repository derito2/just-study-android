package com.dero.juststudykorean.pdf

import com.dero.juststudykorean.korean.pySplit
import kotlin.math.abs

// The web finds tables with pdfplumber (_extract_tables, "lines" strategy). Here: the ruled grid from the page's
// drawn segments (PoC 1 spike), one table per connected group of ruling lines, with merged cells.

/** A ruling line already in page coordinates (a stroke, or a thin filled bar as LibreOffice exports them). */
internal class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
    val horizontal get() = abs(y0 - y1) < 1f && abs(x1 - x0) > 5f
    val vertical get() = abs(x0 - x1) < 1f && abs(y1 - y0) > 5f
}

/** A letter with the stext line it belongs to: a new line inside a cell is a space (pdfplumber's "\n", then split). */
internal class CellLetter(val c: String, val cx: Double, val cy: Double, val line: Int)

class Table(val rows: List<List<String>>, val text: String, val bbox: Box)

private const val TOL = 1.5f

private fun cluster(values: List<Float>): List<Float> {
    val out = mutableListOf<Float>()
    for (v in values.sorted()) if (out.isEmpty() || v - out.last() > TOL) out += v
    return out
}

private fun touches(a: Seg, b: Seg): Boolean =
    minOf(a.x0, a.x1) - TOL <= maxOf(b.x0, b.x1) && minOf(b.x0, b.x1) - TOL <= maxOf(a.x0, a.x1) &&
        minOf(a.y0, a.y1) - TOL <= maxOf(b.y0, b.y1) && minOf(b.y0, b.y1) - TOL <= maxOf(a.y0, a.y1)

/** Ruling lines that touch each other form one table. */
private fun groups(lines: List<Seg>): List<List<Seg>> {
    val parent = IntArray(lines.size) { it }
    fun find(i: Int): Int = if (parent[i] == i) i else find(parent[i]).also { parent[i] = it }
    for (i in lines.indices) for (j in i + 1 until lines.size) if (touches(lines[i], lines[j])) parent[find(i)] = find(j)
    return lines.indices.groupBy(::find).values.map { idx -> idx.map(lines::get) }
}

/** Is there a ruling line along x = [x] covering y in (y0, y1)? (or along y = [x], for horizontal lines) */
private fun covered(lines: List<Seg>, at: Float, from: Float, to: Float, vertical: Boolean): Boolean {
    val mid = (from + to) / 2
    return lines.any { s ->
        if (vertical) abs(s.x0 - at) <= TOL && minOf(s.y0, s.y1) - TOL <= mid && mid <= maxOf(s.y0, s.y1) + TOL
        else abs(s.y0 - at) <= TOL && minOf(s.x0, s.x1) - TOL <= mid && mid <= maxOf(s.x0, s.x1) + TOL
    }
}

private fun cleanCell(cell: String) = normalizeText(cell).pySplit().joinToString(" ")

/** One table per ruled grid: cells between consecutive ruling lines, joined where the line between them is missing. */
internal fun findTables(segments: List<Seg>, letters: List<CellLetter>): List<Table> {
    val ruled = segments.filter { it.horizontal || it.vertical }
    val tables = mutableListOf<Table>()
    for (group in groups(ruled)) {
        val ys = cluster(group.filter { it.horizontal }.map { it.y0 })
        val xs = cluster(group.filter { it.vertical }.map { it.x0 })
        if (ys.size < 2 || xs.size < 2) continue
        val nr = ys.size - 1
        val nc = xs.size - 1
        // Merged cells: a cell joins its right/lower neighbour when no line separates them (pdfplumber gives None there).
        val owner = Array(nr) { r -> IntArray(nc) { c -> r * nc + c } }
        fun root(i: Int): Int { var k = i; while (owner[k / nc][k % nc] != k) k = owner[k / nc][k % nc]; return k }
        for (r in 0 until nr) for (c in 0 until nc) {
            if (c + 1 < nc && !covered(group, xs[c + 1], ys[r], ys[r + 1], vertical = true)) {
                val a = root(r * nc + c); val b = root(r * nc + c + 1)
                if (a != b) owner[maxOf(a, b) / nc][maxOf(a, b) % nc] = minOf(a, b)
            }
            if (r + 1 < nr && !covered(group, ys[r + 1], xs[c], xs[c + 1], vertical = false)) {
                val a = root(r * nc + c); val b = root((r + 1) * nc + c)
                if (a != b) owner[maxOf(a, b) / nc][maxOf(a, b) % nc] = minOf(a, b)
            }
        }
        val text = HashMap<Int, StringBuilder>()
        val lastLine = HashMap<Int, Int>()
        for (l in letters) {
            val r = (0 until nr).firstOrNull { l.cy >= ys[it] && l.cy <= ys[it + 1] } ?: continue
            val c = (0 until nc).firstOrNull { l.cx >= xs[it] && l.cx <= xs[it + 1] } ?: continue
            val cell = root(r * nc + c)
            val sb = text.getOrPut(cell) { StringBuilder() }
            if (lastLine[cell] != null && lastLine[cell] != l.line) sb.append(' ')
            lastLine[cell] = l.line
            sb.append(l.c)
        }
        val rows = (0 until nr).map { r -> (0 until nc).map { c -> if (root(r * nc + c) == r * nc + c) cleanCell(text[r * nc + c]?.toString() ?: "") else "" } }
            .filter { row -> row.any { it.isNotEmpty() } }
        if (rows.size < 2 || rows.maxOf { it.size } < 2) continue
        val t = tableToText(rows)
        if (t.isNotEmpty()) tables += Table(rows, t, Box(xs.first().toDouble(), ys.first().toDouble(), xs.last().toDouble(), ys.last().toDouble()))
    }
    return tables.sortedWith(compareBy({ it.bbox.y0 }, { it.bbox.x0 }))
}

/** First row = headers. Each data row becomes a "Header: value" block. */
fun tableToText(rows: List<List<String>>): String {
    val header = rows[0].mapIndexed { i, h -> h.ifEmpty { "Column ${i + 1}" } }
    return rows.drop(1).mapNotNull { row ->
        header.zip(row).filter { it.second.isNotEmpty() }.joinToString("\n") { "${it.first}: ${it.second}" }.ifEmpty { null }
    }.joinToString("\n\n")
}
