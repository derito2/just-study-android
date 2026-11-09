package com.dero.juststudykorean.pdf

import com.dero.juststudykorean.korean.pyIsSpace
import com.dero.juststudykorean.korean.pyStrip
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer

// Port of _extract_lines, _rows, _row_line and normalize_text (backend/app/rag/parser.py).

/** One letter as PyMuPDF's rawdict gives it: its box, its span's size and whether its span is bold. */
internal class Glyph(val c: String, val x0: Double, val y0: Double, val x1: Double, val y1: Double, val size: Double, val bold: Boolean) {
    val isSpace get() = c.length == 1 && c[0].pyIsSpace()
}

/** A rawdict line (a "unit"): its box and its letters, in the PDF's order. */
internal class Unit(val bbox: Box, val glyphs: List<Glyph>)

data class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    fun toList() = listOf(x0, y0, x1, y1)
}

class Line(val text: String, val size: Double, val bold: Boolean, val bbox: Box)

private val ZERO_WIDTH = Regex("[​‌‍⁠﻿]")
private val CONTROL = Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]")
private val SPACES = Regex("[ \t]{2,}")

/** NFC (composed jamo), without control or zero-width characters. */
fun normalizeText(text: String): String {
    val t = Normalizer.normalize(text, Normalizer.Form.NFC).replace(ZERO_WIDTH, "").replace(' ', ' ')
    return CONTROL.replace(t, "").pyStrip()
}

/** Python's round(x, n): half-even on the exact binary value. */
internal fun pyRound(x: Double, places: Int) = BigDecimal(x).setScale(places, RoundingMode.HALF_EVEN).toDouble()

private class Row(var bbox: Box, val units: MutableList<Unit>)

/**
 * The PDF's lines grouped by row. A PDF exported with two fonts (Latin + Korean) splits each row into several lines,
 * with the spaces as lines of their own: they are joined again if they overlap in height and are next to each other.
 */
internal fun rows(units: List<Unit>): List<List<Unit>> {
    val rows = mutableListOf<Row>()
    for (unit in units) {
        val (x0, y0, x1, y1) = unit.bbox
        val size = unit.glyphs.maxOf { it.size }
        val hits = rows.filter { row ->
            val r = row.bbox
            val overlap = minOf(y1, r.y1) - maxOf(y0, r.y0)
            val height = minOf(y1 - y0, r.y1 - r.y0).let { if (it == 0.0) 1.0 else it }
            val near = x0 - r.x1 < 0.8 * size && r.x0 - x1 < 0.8 * size
            overlap >= 0.5 * height && near
        }
        if (hits.isEmpty()) {
            rows += Row(unit.bbox, mutableListOf(unit))
            continue
        }
        val first = hits[0]
        for (row in hits.drop(1)) {
            first.units += row.units
            rows.remove(row)
        }
        first.units += unit
        val boxes = first.units.map { it.bbox }
        first.bbox = Box(boxes.minOf { it.x0 }, boxes.minOf { it.y0 }, boxes.maxOf { it.x1 }, boxes.maxOf { it.y1 })
    }
    return rows.map { it.units }
}

/** A row's text, letter by letter in x order: a visible gap between two letters is a space the PDF didn't write. */
internal fun rowLine(units: List<Unit>): Line? {
    val glyphs = units.flatMap { it.glyphs }
    val sb = StringBuilder()
    var lastX1: Double? = null
    for (g in glyphs.sortedBy { it.x0 }) {
        if (lastX1 != null && !g.isSpace && sb.isNotEmpty() && !sb.last().pyIsSpace() && g.x0 - lastX1 > 0.25 * g.size) sb.append(' ')
        sb.append(g.c)
        if (!g.isSpace) lastX1 = g.x1
    }
    val text = normalizeText(SPACES.replace(sb.toString(), " "))
    if (text.isEmpty()) return null
    // A span counts if it prints something; every letter of a span shares its size and boldness.
    val printed = glyphs.filter { !it.isSpace }.ifEmpty { glyphs }
    val boxes = units.map { it.bbox }
    val bbox = Box(boxes.minOf { it.x0 }, boxes.minOf { it.y0 }, boxes.maxOf { it.x1 }, boxes.maxOf { it.y1 })
    return Line(text, pyRound(printed.maxOf { it.size }, 1), printed.any { it.bold }, bbox)
}

internal fun extractLines(units: List<Unit>): List<Line> = rows(units.filter { it.glyphs.isNotEmpty() }).mapNotNull(::rowLine)
