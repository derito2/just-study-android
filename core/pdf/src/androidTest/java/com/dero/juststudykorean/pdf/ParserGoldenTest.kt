package com.dero.juststudykorean.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** MuPDF is native: on the phone. The test PDFs against golden/parser and golden/parser_raw (the web's parse_pdf). */
@RunWith(AndroidJUnit4::class)
class ParserGoldenTest {
    private val names = listOf(
        "Frases_dramas_y_redes", "Gramatica_semana_14", "Korean_Notes_Example",
        "Vocab_cafe_y_comida", "Vocab_emociones_redes", "Vocab_viajes_irregulares",
    )
    private val assets get() = InstrumentationRegistry.getInstrumentation().context.assets
    private fun text(path: String) = assets.open(path).bufferedReader().use { it.readText() }
    private fun pdf(path: String): File {
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, path.substringAfterLast('/'))
        assets.open(path).use { input -> out.outputStream().use(input::copyTo) }
        return out
    }

    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    private fun nullable(o: JSONObject, k: String) = if (o.isNull(k)) null else o.getString(k) // optString(null) is "null"

    private fun compare(label: String, parsed: ParsedDocument, golden: JSONObject) {
        assertEquals("$label page_count", golden.getInt("page_count"), parsed.pageCount)
        assertEquals("$label char_count", golden.getInt("char_count"), parsed.charCount)
        assertEquals("$label image_count", golden.getInt("image_count"), parsed.imageCount)
        assertEquals("$label table_count", golden.optInt("table_count", parsed.tableCount), parsed.tableCount)
        assertEquals("$label warnings", strings(golden.optJSONArray("warnings") ?: JSONArray()), parsed.warnings)
        val pages = golden.getJSONArray("pages")
        assertEquals("$label pages", pages.length(), parsed.pages.size)
        for ((i, page) in parsed.pages.withIndex()) {
            val g = pages.getJSONObject(i)
            val at = "$label p${i + 1}"
            assertEquals("$at page_number", g.getInt("page_number"), page.pageNumber)
            assertEquals("$at text", g.getString("text"), page.text)
            assertEquals("$at headings", strings(g.getJSONArray("headings")), page.headings)
            assertEquals("$at section", nullable(g, "section"), page.section)
            assertEquals("$at image_count", g.getInt("image_count"), page.imageCount)
            assertEquals("$at char_count", g.getInt("char_count"), page.charCount)
            val tables = g.getJSONArray("tables")
            assertEquals("$at tables", tables.length(), page.tables.size)
            for ((t, table) in page.tables.withIndex()) {
                val gt = tables.getJSONObject(t)
                val rows = gt.getJSONArray("rows").let { r -> (0 until r.length()).map { strings(r.getJSONArray(it)) } }
                assertEquals("$at table $t rows", rows, table.rows)
                assertEquals("$at table $t text", gt.getString("text"), table.text)
                val bbox = gt.getJSONArray("bbox")
                table.bbox.toList().forEachIndexed { n, v -> assertTrue("$at table $t bbox", abs(bbox.getDouble(n) - v) <= 1.0) }
            }
        }
    }

    @Test
    fun theTestPdfsParseLikeTheWeb() {
        for (name in names) compare(name, PdfParser.parse(pdf("pdfs/$name.pdf")), JSONObject(text("parser/$name.json")))
    }

    /** Ruled tables (stroked borders, and thin filled bars as LibreOffice exports them) against pdfplumber. */
    @Test
    fun syntheticTablesLikePdfplumber() {
        compare("Tablas_sinteticas", PdfParser.parse(pdf("extra/Tablas_sinteticas.pdf")), JSONObject(text("extra/Tablas_sinteticas.parser.json")))
    }

    @Test
    fun lettersLikePyMuPdfRawdict() {
        for ((name, raw) in names.map { it to "parser_raw/$it.json" } + ("Tablas_sinteticas" to "extra/Tablas_sinteticas.parser_raw.json")) {
            val file = if (name == "Tablas_sinteticas") pdf("extra/$name.pdf") else pdf("pdfs/$name.pdf")
            val golden = JSONObject(text(raw)).getJSONArray("pages")
            val pages = PdfParser.raw(file)
            assertEquals("$name pages", golden.length(), pages.size)
            for ((i, page) in pages.withIndex()) {
                val g = golden.getJSONObject(i)
                val letters = g.getJSONArray("letters")
                val (glyphs, images, items) = page
                assertEquals("$name p${i + 1} images", g.getInt("imageCount"), images)
                assertEquals("$name p${i + 1} drawing items", g.getInt("drawingItems"), items)
                assertEquals("$name p${i + 1} letters", letters.length(), glyphs.size)
                for ((k, l) in glyphs.withIndex()) {
                    val w = letters.getJSONObject(k)
                    val at = "$name p${i + 1} letter $k"
                    assertEquals(at, w.getString("c"), l.c)
                    assertEquals("$at size", w.getDouble("size"), pyRound(l.size, 1), 0.0)
                    assertEquals("$at bold", w.getBoolean("bold"), l.bold)
                    val b = w.getJSONArray("bbox")
                    listOf(l.x0, l.y0, l.x1, l.y1).forEachIndexed { n, v -> assertEquals("$at bbox", b.getDouble(n), pyRound(v, 2), 0.0) }
                }
            }
        }
    }

    /** One case per PdfError, with the web's class and message; owner-password and small images parse. */
    @Test
    fun errorsLikeTheWeb() {
        val expected = JSONObject(text("pdfs/errors/expected.json"))
        for (file in expected.keys()) {
            val want = expected.getJSONObject(file)
            val (cls, message) = try {
                PdfParser.parse(pdf("pdfs/errors/$file"))
                null to null
            } catch (e: PdfError) {
                val web = when (e) {
                    is PdfError.Corrupt -> "CorruptPdfError"
                    is PdfError.Empty -> "EmptyPdfError"
                    is PdfError.ImagesNotSupported -> "ImagesNotSupportedError"
                    is PdfError.NoDigitalText -> "NoDigitalTextError"
                }
                web to e.message
            } catch (e: Exception) {
                fail("$file: unexpected ${e.javaClass.simpleName}")
                null to null
            }
            assertEquals("$file error", nullable(want, "error"), cls)
            assertEquals("$file message", nullable(want, "message"), message)
        }
    }
}
