package com.dero.juststudykorean.pdf

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.DefaultColorSpaces
import com.artifex.mupdf.fitz.Device
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Path
import com.artifex.mupdf.fitz.PathWalker
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.Shade
import com.artifex.mupdf.fitz.StrokeState
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.StructuredTextWalker
import com.artifex.mupdf.fitz.Text
import com.dero.juststudykorean.korean.pyLen
import java.io.File

// Port of parse_pdf (backend/app/rag/parser.py): digital PDFs only, text and tables. Without OCR the text inside an
// image can't be read, so a PDF with large images is rejected whole; small ones (logos, icons) are ignored.

private const val MIN_PAGE_CHARS = 50
private const val SIGNIFICANT_IMAGE_RATIO = 0.10f
private const val MIN_DRAWING_ITEMS_FOR_TABLE = 6
/** The same flags as the web's _extract_lines (TEXT_PRESERVE_WHITESPACE | TEXT_MEDIABOX_CLIP), no images. */
private const val STEXT_OPTIONS = "preserve-whitespace,mediabox-clip=yes"

const val SCANNED_MESSAGE = "This document doesn't contain enough digital text to be processed automatically. " +
    "OCR is not enabled in this application."

/** An expected error; its message can be shown to the user (the web's PdfError and subclasses). */
sealed class PdfError(message: String) : Exception(message) {
    class Corrupt(message: String) : PdfError(message)
    class Empty(message: String) : PdfError(message)
    /** Probably scanned: it would need OCR, which is off. */
    open class NoDigitalText(message: String) : PdfError(message)
    /** Text, but also large images whose content can't be read. */
    class ImagesNotSupported(message: String) : NoDigitalText(message)
}

class PageContent(
    val pageNumber: Int,
    val text: String,
    val headings: List<String>,
    var section: String?,
    val tables: List<Table>,
    val imageCount: Int,
    val charCount: Int,
)

class ParsedDocument(
    val pageCount: Int,
    val pages: List<PageContent>,
    val charCount: Int,
    val imageCount: Int,
    val tableCount: Int,
    val warnings: List<String>,
)

private fun pagesList(pages: List<Int>) = pages.take(10).joinToString(", ") + if (pages.size > 10) "…" else ""

fun imagesMessage(pages: List<Int>): String {
    val label = if (pages.size == 1) "page" else "pages"
    return "This PDF has images on $label ${pagesList(pages)}. Only text and tables are supported: text inside images " +
        "can't be read (OCR is not enabled). Remove the images or type that content as text, then upload it again."
}

/** What one page gives in a single pass: rawdict lines, large images, drawing items and the ruling lines. */
private class RawPage(val units: List<Unit>, val letters: List<CellLetter>, val images: Int, val drawingItems: Int, val segments: List<Seg>)

object PdfParser {
    fun parse(file: File): ParsedDocument {
        val doc = try {
            Document.openDocument(file.path)
        } catch (e: Exception) {
            throw PdfError.Corrupt("The file is corrupt or is not a valid PDF.")
        }
        val raw: List<RawPage>
        try {
            if (doc.needsPassword()) throw PdfError.Corrupt("The PDF is password-protected and cannot be processed.")
            val count = doc.countPages()
            if (count == 0) throw PdfError.Empty("The PDF has no pages.")
            raw = (0 until count).map { readPage(doc, it) }
        } catch (e: PdfError) {
            throw e
        } catch (e: Exception) {
            throw PdfError.Corrupt("The file is corrupt or is not a valid PDF.")
        } finally {
            doc.destroy()
        }

        val linesPerPage = raw.map { extractLines(it.units) }
        val charsPerPage = linesPerPage.map { lines -> lines.sumOf { it.text.pyLen } }
        val totalImages = raw.sumOf { it.images }
        if (charsPerPage.sum() == 0 && totalImages == 0) throw PdfError.Empty("The PDF appears to be empty: no text and no images were found.")
        if (charsPerPage.max() < MIN_PAGE_CHARS) throw PdfError.NoDigitalText(SCANNED_MESSAGE)
        val imagePages = raw.indices.filter { raw[it].images > 0 }.map { it + 1 }
        if (imagePages.isNotEmpty()) throw PdfError.ImagesNotSupported(imagesMessage(imagePages))

        // Tables only on pages with enough drawn lines (the web's pdfplumber pass).
        val warnings = mutableListOf<String>()
        val tablesPerPage = raw.indices.associateWith { i ->
            if (raw[i].drawingItems < MIN_DRAWING_ITEMS_FOR_TABLE) emptyList()
            else try {
                findTables(raw[i].segments, raw[i].letters)
            } catch (e: Exception) {
                warnings += "Page ${i + 1}: a table could not be extracted; its content was kept as plain text."
                emptyList()
            }
        }

        val body = bodyFontSize(linesPerPage)
        val pages = mutableListOf<PageContent>()
        var currentSection: String? = null
        for ((i, lines) in linesPerPage.withIndex()) {
            val (page, firstHeading) = buildPage(i + 1, lines, tablesPerPage.getValue(i), body, raw[i].images)
            page.section = currentSection ?: firstHeading
            if (page.headings.isNotEmpty()) currentSection = page.headings.last()
            pages += page
        }
        val blank = pages.filter { it.charCount == 0 }.map { it.pageNumber }
        if (blank.isNotEmpty()) {
            val one = blank.size == 1
            warnings += "${if (one) "Page" else "Pages"} ${pagesList(blank)} ${if (one) "has" else "have"} no text and ${if (one) "was" else "were"} skipped."
        }
        return ParsedDocument(raw.size, pages, pages.sumOf { it.charCount }, totalImages, pages.sumOf { it.tables.size }, warnings)
    }

    /** Per page: its letters in rawdict order, large images and drawing items (RawGoldenTest, against golden/parser_raw). */
    internal fun raw(file: File): List<Triple<List<Glyph>, Int, Int>> {
        val doc = Document.openDocument(file.path)
        try {
            return (0 until doc.countPages()).map { i -> readPage(doc, i).let { Triple(it.units.flatMap(Unit::glyphs), it.images, it.drawingItems) } }
        } finally {
            doc.destroy()
        }
    }

    private fun readPage(doc: Document, index: Int): RawPage {
        val page = doc.loadPage(index)
        try {
            val b = page.bounds
            val stext = page.toStructuredText(STEXT_OPTIONS)
            val (units, letters) = try { walk(stext) } finally { stext.destroy() }
            val counter = Counter((b.x1 - b.x0) * (b.y1 - b.y0))
            try {
                page.run(counter, Matrix())
                counter.close()
            } finally {
                counter.destroy()
            }
            return RawPage(units, letters, counter.images, counter.items, counter.segments)
        } finally {
            page.destroy()
        }
    }

    /** rawdict's text lines (image blocks skipped) and every letter with its line, for the table cells. */
    private fun walk(stext: StructuredText): Pair<List<Unit>, List<CellLetter>> {
        val units = mutableListOf<Unit>()
        val letters = mutableListOf<CellLetter>()
        var bbox: Rect? = null
        var glyphs = mutableListOf<Glyph>()
        var line = 0
        stext.walk(object : StructuredTextWalker {
            override fun onImageBlock(bbox: Rect?, transform: Matrix?, image: Image?) {}
            override fun beginTextBlock(bbox: Rect?, flags: Int) {}
            override fun endTextBlock() {}
            override fun beginLine(lineBbox: Rect?, wmode: Int, dir: Point?) { bbox = lineBbox; glyphs = mutableListOf(); line++ }
            override fun endLine() {
                val r = bbox ?: return
                if (glyphs.isNotEmpty()) units += Unit(Box(r.x0.toDouble(), r.y0.toDouble(), r.x1.toDouble(), r.y1.toDouble()), glyphs)
            }
            override fun beginStruct(standard: String?, raw: String?, index: Int) {}
            override fun endStruct() {}
            override fun onVector(bbox: Rect?, info: StructuredTextWalker.VectorInfo?, argb: Int) {}
            override fun onChar(c: Int, origin: Point?, font: Font, size: Float, quad: Quad, argb: Int, flags: Int, bidi: Int) {
                val r = quad.toRect()
                val name = font.name.substringAfter('+') // PyMuPDF drops the subset prefix
                val ch = String(Character.toChars(c))
                glyphs += Glyph(ch, r.x0.toDouble(), r.y0.toDouble(), r.x1.toDouble(), r.y1.toDouble(), size.toDouble(), font.isBold || "bold" in name.lowercase())
                letters += CellLetter(ch, (r.x0 + r.x1) / 2.0, (r.y0 + r.y1) / 2.0, line)
            }
        })
        return units to letters
    }
}

/** Images >= 10 % of the page, vector items and ruling lines, like the web's PyMuPDF counters (PoC 1: 63/63 pages). */
private class Counter(val pageArea: Float) : Device() {
    var images = 0
    var items = 0
    val segments = mutableListOf<Seg>()

    private fun image(ctm: Matrix) {
        val r = Rect(0f, 0f, 1f, 1f).transform(ctm)
        if (pageArea > 0 && (r.x1 - r.x0) * (r.y1 - r.y0) / pageArea >= SIGNIFICANT_IMAGE_RATIO) images++
    }

    private fun path(path: Path, ctm: Matrix, filled: Boolean) {
        val sub = mutableListOf<Pair<Float, Float>>()
        var closed = false
        fun pt(x: Float, y: Float) = Pair(ctm.a * x + ctm.c * y + ctm.e, ctm.b * x + ctm.d * y + ctm.f)
        fun flush() {
            if (sub.size < 2) { sub.clear(); closed = false; return }
            val pts = if (closed && sub.first() != sub.last()) sub + sub.first() else sub.toList()
            val axisRect = pts.size == 5 && pts.map { it.first }.distinct().size <= 2 && pts.map { it.second }.distinct().size <= 2 && pts.first() == pts.last()
            if (axisRect) {
                items += 1
                val x0 = pts.minOf { it.first }; val x1 = pts.maxOf { it.first }
                val y0 = pts.minOf { it.second }; val y1 = pts.maxOf { it.second }
                if (filled && (x1 - x0 < 2f || y1 - y0 < 2f)) { // a thin filled bar is a ruling line
                    segments += if (x1 - x0 < 2f) Seg((x0 + x1) / 2, y0, (x0 + x1) / 2, y1) else Seg(x0, (y0 + y1) / 2, x1, (y0 + y1) / 2)
                } else {
                    segments += listOf(Seg(x0, y0, x1, y0), Seg(x0, y1, x1, y1), Seg(x0, y0, x0, y1), Seg(x1, y0, x1, y1))
                }
            } else {
                for (k in 1 until pts.size) {
                    items++
                    segments += Seg(pts[k - 1].first, pts[k - 1].second, pts[k].first, pts[k].second)
                }
            }
            sub.clear(); closed = false
        }
        path.walk(object : PathWalker {
            override fun moveTo(x: Float, y: Float) { flush(); sub += pt(x, y) }
            override fun lineTo(x: Float, y: Float) { sub += pt(x, y) }
            override fun curveTo(cx1: Float, cy1: Float, cx2: Float, cy2: Float, ex: Float, ey: Float) { sub += pt(ex, ey) }
            override fun closePath() { closed = true }
        })
        flush()
    }

    override fun fillPath(path: Path, evenOdd: Boolean, ctm: Matrix, cs: ColorSpace?, color: FloatArray?, alpha: Float, cp: Int) = path(path, ctm, true)
    override fun strokePath(path: Path, stroke: StrokeState?, ctm: Matrix, cs: ColorSpace?, color: FloatArray?, alpha: Float, cp: Int) = path(path, ctm, false)
    override fun fillImage(image: Image?, ctm: Matrix, alpha: Float, cp: Int) = image(ctm)
    override fun fillImageMask(image: Image?, ctm: Matrix, cs: ColorSpace?, color: FloatArray?, alpha: Float, cp: Int) = image(ctm)
    override fun close() {}
    override fun clipPath(path: Path?, evenOdd: Boolean, ctm: Matrix?) {}
    override fun clipStrokePath(path: Path?, stroke: StrokeState?, ctm: Matrix?) {}
    override fun fillText(text: Text?, ctm: Matrix?, cs: ColorSpace?, color: FloatArray?, alpha: Float, cp: Int) {}
    override fun strokeText(text: Text?, stroke: StrokeState?, ctm: Matrix?, cs: ColorSpace?, color: FloatArray?, alpha: Float, cp: Int) {}
    override fun clipText(text: Text?, ctm: Matrix?) {}
    override fun clipStrokeText(text: Text?, stroke: StrokeState?, ctm: Matrix?) {}
    override fun ignoreText(text: Text?, ctm: Matrix?) {}
    override fun fillShade(shd: Shade?, ctm: Matrix?, alpha: Float, cp: Int) {}
    override fun clipImageMask(image: Image?, ctm: Matrix?) {}
    override fun popClip() {}
    override fun beginMask(area: Rect?, luminosity: Boolean, cs: ColorSpace?, bc: FloatArray?, cp: Int) {}
    override fun endMask() {}
    override fun beginGroup(area: Rect?, cs: ColorSpace?, isolated: Boolean, knockout: Boolean, blendmode: Int, alpha: Float) {}
    override fun endGroup() {}
    override fun beginTile(area: Rect?, view: Rect?, xstep: Float, ystep: Float, ctm: Matrix?, id: Int, doc: Int): Int = 0
    override fun endTile() {}
    override fun renderFlags(set: Int, clear: Int) {}
    override fun setDefaultColorSpaces(dcs: DefaultColorSpaces?) {}
    override fun beginLayer(name: String?) {}
    override fun endLayer() {}
    override fun beginStructure(standard: Int, raw: String?, idx: Int) {}
    override fun endStructure() {}
    override fun beginMetatext(meta: Int, text: String?) {}
    override fun endMetatext() {}
}
