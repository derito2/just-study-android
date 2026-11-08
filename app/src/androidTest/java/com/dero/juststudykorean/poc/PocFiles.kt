package com.dero.juststudykorean.poc

import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Context
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

// PoC code (throwaway, branch poc/1-mupdf only).

data class Letter(val c: String, val bbox: List<Double>, val size: Double, val font: String, val bold: Boolean, val line: Int)

data class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

class RawPage(
    val page: Int,
    val letters: List<Letter>,
    val imageCount: Int,
    val drawingItems: Int,
    val segments: List<Seg>,
    val ms: Double,
)

object Poc {
    val ASSET_PDFS = listOf(
        "Frases_dramas_y_redes", "Gramatica_semana_14", "Korean_Notes_Example",
        "Vocab_cafe_y_comida", "Vocab_emociones_redes", "Vocab_viajes_irregulares",
    )
    private const val MIN_PAGE_CHARS = 50
    private const val SIGNIFICANT_IMAGE_RATIO = 0.10f
    private const val MIN_DRAWING_ITEMS_FOR_TABLE = 6
    private const val STEXT_OPTIONS = "preserve-whitespace,mediabox-clip=yes"
    private const val SCANNED = "This document doesn't contain enough digital text to be processed " +
        "automatically. OCR is not enabled in this application."

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    fun dir(sub: String = ""): File = File(instrumentation.targetContext.filesDir, "poc/$sub").apply { mkdirs() }

    fun assetText(name: String): String =
        instrumentation.context.assets.open("poc/$name").bufferedReader().use { it.readText() }

    fun asset(name: String): File {
        val out = File(dir("assets"), name).apply { parentFile?.mkdirs() }
        instrumentation.context.assets.open("poc/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    fun write(name: String, json: JSONObject) = File(dir(), name).writeText(json.toString(1))

    fun mupdfVersion(): String = Context.getVersion().version

    // ------------------------------------------------------------ dump
    private fun round(v: Float, places: Int): Double =
        BigDecimal(v.toDouble()).setScale(places, RoundingMode.HALF_EVEN).toDouble()

    fun dumpRaw(path: String): List<RawPage> {
        val doc = Document.openDocument(path)
        try {
            return (0 until doc.countPages()).map { i ->
                val start = System.nanoTime()
                val page = doc.loadPage(i)
                val bounds = page.bounds
                val letters = letters(page.toStructuredText(STEXT_OPTIONS))
                val counter = Counter((bounds.x1 - bounds.x0) * (bounds.y1 - bounds.y0))
                page.run(counter, Matrix())
                counter.close()
                counter.destroy()
                page.destroy()
                RawPage(i + 1, letters, counter.images, counter.items, counter.segments, (System.nanoTime() - start) / 1e6)
            }
        } finally {
            doc.destroy()
        }
    }

    private fun letters(stext: StructuredText): List<Letter> {
        val out = mutableListOf<Letter>()
        var line = 0
        stext.walk(object : StructuredTextWalker {
            override fun onImageBlock(bbox: Rect?, transform: Matrix?, image: Image?) {}
            override fun beginTextBlock(bbox: Rect?, flags: Int) {}
            override fun endTextBlock() {}
            override fun beginLine(bbox: Rect?, wmode: Int, dir: Point?) { line++ }
            override fun endLine() {}
            override fun beginStruct(standard: String?, raw: String?, index: Int) {}
            override fun endStruct() {}
            override fun onVector(bbox: Rect?, info: StructuredTextWalker.VectorInfo?, argb: Int) {}
            override fun onChar(c: Int, origin: Point?, font: Font, size: Float, quad: Quad, argb: Int, flags: Int, bidi: Int) {
                val r = quad.toRect()
                val name = font.name.substringAfter('+') // PyMuPDF drops the subset prefix
                val bold = font.isBold || "bold" in name.lowercase()
                out += Letter(
                    String(Character.toChars(c)),
                    listOf(r.x0, r.y0, r.x1, r.y1).map { round(it, 2) },
                    round(size, 1), name, bold, line,
                )
            }
        })
        stext.destroy()
        return out
    }

    /** Images ≥ 10 % of the page and vector items, like the web's PyMuPDF counters. */
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
                val xs = pts.map { it.first }.distinct().size
                val ys = pts.map { it.second }.distinct().size
                val axisRect = pts.size == 5 && xs <= 2 && ys <= 2 && pts.first() == pts.last()
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

    // ------------------------------------------------------------ compare
    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    private fun similarity(a: String, b: String): Double {
        val x = a.codePoints().toArray()
        val y = b.codePoints().toArray()
        if (x.isEmpty() && y.isEmpty()) return 1.0
        var prev = IntArray(y.size + 1) { it }
        for (i in 1..x.size) {
            val cur = IntArray(y.size + 1)
            cur[0] = i
            for (j in 1..y.size) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return 1.0 - prev[y.size].toDouble() / max(x.size, y.size)
    }

    fun measureAsset(name: String): JSONObject =
        measure(name, asset("$name.pdf"), assetText("$name.parser_raw.json"), assetText("$name.parser.json"), keepLetters = true)

    /** Dump with MuPDF, compare with the golden and write metrics (never text) per PDF. */
    fun measure(label: String, pdf: File, goldenRaw: String, goldenParsed: String, keepLetters: Boolean): JSONObject {
        val cached = File(dir(), "metrics-$label.json")
        val fresh = InstrumentationRegistry.getArguments().getString("fresh") == "true"
        if (cached.exists() && !fresh) return JSONObject(cached.readText()) // resume after a disconnect

        var pssPeakKb = Debug.getPss()
        val pages = dumpRaw(pdf.path)
        pssPeakKb = max(pssPeakKb, Debug.getPss())
        val golden = JSONObject(goldenRaw).getJSONArray("pages")
        val parsed = JSONObject(goldenParsed).optJSONArray("pages") ?: JSONArray()

        var countEq = 0; var textEq = 0; var minSim = 1.0
        var letters = 0; var sizeEq = 0; var boldEq = 0; var fontEq = 0; var maxBbox = 0.0
        var imgEq = 0; var drawEq = 0
        val pageMetrics = JSONArray()
        val tables = JSONArray()
        for ((i, page) in pages.withIndex()) {
            val g = golden.optJSONObject(i)
            val gl = g?.getJSONArray("letters") ?: JSONArray()
            val gText = nfc((0 until gl.length()).joinToString("") { gl.getJSONObject(it).getString("c") })
            val mText = nfc(page.letters.joinToString("") { it.c })
            val sameCount = gl.length() == page.letters.size
            val sim = if (gText == mText) 1.0 else similarity(gText, mText)
            if (sameCount) countEq++
            if (gText == mText) textEq++
            minSim = minOf(minSim, sim)
            if (sameCount) {
                for (k in page.letters.indices) {
                    val a = gl.getJSONObject(k); val b = page.letters[k]
                    letters++
                    if (a.getDouble("size") == b.size) sizeEq++
                    if (a.getBoolean("bold") == b.bold) boldEq++
                    if (a.getString("font") == b.font) fontEq++
                    val gb = a.getJSONArray("bbox")
                    for (n in 0..3) maxBbox = max(maxBbox, abs(gb.getDouble(n) - b.bbox[n]))
                }
            }
            if (g != null && g.getInt("imageCount") == page.imageCount) imgEq++
            if (g != null && g.getInt("drawingItems") == page.drawingItems) drawEq++
            pageMetrics.put(JSONObject().put("page", page.page).put("ms", page.ms).put("letters", page.letters.size)
                .put("goldenLetters", gl.length()).put("similarity", sim)
                .put("imageCount", page.imageCount).put("goldenImageCount", g?.optInt("imageCount"))
                .put("drawingItems", page.drawingItems).put("goldenDrawingItems", g?.optInt("drawingItems")))
            val want = parsed.optJSONObject(i)?.optJSONArray("tables") ?: JSONArray()
            if (page.drawingItems >= MIN_DRAWING_ITEMS_FOR_TABLE || want.length() > 0) {
                tables.put(compareTables(page, want))
            }
        }
        if (keepLetters) write("mupdf-$label.json", rawJson(pages))
        val ms = pages.map { it.ms }.sorted()
        val out = JSONObject()
            .put("label", label).put("mupdf", mupdfVersion())
            .put("pages", pages.size).put("goldenPages", golden.length())
            .put("pagesLetterCountEqual", countEq).put("pagesTextEqual", textEq).put("minSimilarity", minSim)
            .put("lettersCompared", letters).put("sizeEqual", sizeEq).put("boldEqual", boldEq).put("fontEqual", fontEq)
            .put("maxBboxDiff", maxBbox).put("pagesImageEqual", imgEq).put("pagesDrawingEqual", drawEq)
            .put("msP50", pct(ms, 0.5)).put("msP90", pct(ms, 0.9)).put("pssPeakKb", pssPeakKb)
            .put("bytes", pdf.length()).put("tables", tables).put("byPage", pageMetrics)
        cached.writeText(out.toString(1))
        return out
    }

    private fun pct(sorted: List<Double>, p: Double): Double =
        if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p).toInt()]

    private fun rawJson(pages: List<RawPage>) = JSONObject().put("pages", JSONArray(pages.map { p ->
        JSONObject().put("page", p.page).put("imageCount", p.imageCount).put("drawingItems", p.drawingItems)
            .put("letters", JSONArray(p.letters.map { l ->
                JSONObject().put("c", l.c).put("bbox", JSONArray(l.bbox)).put("size", l.size).put("font", l.font).put("bold", l.bold)
            }))
    }))

    // ------------------------------------------------------------ tables (spike)
    private fun cluster(values: List<Float>, tol: Float = 1.5f): List<Float> {
        val out = mutableListOf<Float>()
        for (v in values.sorted()) if (out.isEmpty() || v - out.last() > tol) out += v
        return out
    }

    /** One ruled grid per page: cells between consecutive distinct x/y ruling lines. */
    fun lattice(page: RawPage): List<List<String>> {
        val h = page.segments.filter { abs(it.y0 - it.y1) < 1f && abs(it.x1 - it.x0) > 5f }
        val v = page.segments.filter { abs(it.x0 - it.x1) < 1f && abs(it.y1 - it.y0) > 5f }
        val ys = cluster(h.map { it.y0 })
        val xs = cluster(v.map { it.x0 })
        if (ys.size < 2 || xs.size < 2) return emptyList()
        val cells = Array(ys.size - 1) { Array(xs.size - 1) { StringBuilder() } }
        val lastLine = Array(ys.size - 1) { IntArray(xs.size - 1) { -1 } }
        for (l in page.letters) {
            val cx = ((l.bbox[0] + l.bbox[2]) / 2).toFloat()
            val cy = ((l.bbox[1] + l.bbox[3]) / 2).toFloat()
            val r = (0 until ys.size - 1).firstOrNull { cy >= ys[it] && cy <= ys[it + 1] } ?: continue
            val c = (0 until xs.size - 1).firstOrNull { cx >= xs[it] && cx <= xs[it + 1] } ?: continue
            if (lastLine[r][c] != -1 && lastLine[r][c] != l.line) cells[r][c].append(' ')
            lastLine[r][c] = l.line
            cells[r][c].append(l.c)
        }
        return cells.map { row -> row.map { nfc(it.toString()).split(Regex("\\s+")).filter(String::isNotEmpty).joinToString(" ") } }
            .filter { row -> row.any { it.isNotEmpty() } }
    }

    private fun compareTables(page: RawPage, want: JSONArray): JSONObject {
        val got = lattice(page)
        val wantRows = (0 until want.length()).flatMap { t ->
            val rows = want.getJSONObject(t).getJSONArray("rows")
            (0 until rows.length()).map { r -> rows.getJSONArray(r).let { a -> (0 until a.length()).map { a.optString(it, "") } } }
        }
        val cells = wantRows.sumOf { it.size }
        val sameCells = got.zip(wantRows).sumOf { (a, b) -> a.zip(b).count { (x, y) -> x == y } }
        return JSONObject().put("page", page.page).put("goldenTables", want.length())
            .put("goldenRows", wantRows.size).put("goldenCells", cells)
            .put("mupdfRows", got.size).put("mupdfCells", got.sumOf { it.size })
            .put("sameCells", sameCells).put("equal", got == wantRows)
    }

    fun tablesSummary(all: List<JSONObject>): JSONObject {
        val pages = all.flatMap { m -> m.getJSONArray("tables").let { t -> (0 until t.length()).map { t.getJSONObject(it) } } }
        val withGolden = pages.filter { it.getInt("goldenTables") > 0 }
        return JSONObject().put("candidatePages", pages.size).put("pagesWithTables", withGolden.size)
            .put("tables", withGolden.sumOf { it.getInt("goldenTables") })
            .put("pagesEqual", withGolden.count { it.getBoolean("equal") })
            .put("cells", withGolden.sumOf { it.getInt("goldenCells") })
            .put("sameCells", withGolden.sumOf { it.getInt("sameCells") })
            .put("detail", JSONArray(pages))
    }

    // ------------------------------------------------------------ errors (the web's PdfError)
    fun classify(path: String): Pair<String?, String?> {
        val doc = try {
            Document.openDocument(path)
        } catch (e: Exception) {
            return "CorruptPdfError" to "The file is corrupt or is not a valid PDF."
        }
        val locked = doc.needsPassword()
        val count = if (locked) 0 else doc.countPages()
        doc.destroy()
        if (locked) return "CorruptPdfError" to "The PDF is password-protected and cannot be processed."
        if (count == 0) return "EmptyPdfError" to "The PDF has no pages."
        val pages = dumpRaw(path)
        // The web counts the characters of its stripped lines.
        val chars = pages.map { p -> p.letters.groupBy { it.line }.values.sumOf { l -> l.joinToString("") { it.c }.trim().length } }
        val images = pages.sumOf { it.imageCount }
        if (chars.sum() == 0 && images == 0) {
            return "EmptyPdfError" to "The PDF appears to be empty: no text and no images were found."
        }
        if (chars.max() < MIN_PAGE_CHARS) return "NoDigitalTextError" to SCANNED
        val imagePages = pages.filter { it.imageCount > 0 }.map { it.page }
        if (imagePages.isNotEmpty()) {
            val label = if (imagePages.size == 1) "page" else "pages"
            val list = imagePages.take(10).joinToString(", ") + if (imagePages.size > 10) "…" else ""
            return "ImagesNotSupportedError" to "This PDF has images on $label $list. Only text and tables are " +
                "supported: text inside images can't be read (OCR is not enabled). Remove the " +
                "images or type that content as text, then upload it again."
        }
        return null to null
    }
}
