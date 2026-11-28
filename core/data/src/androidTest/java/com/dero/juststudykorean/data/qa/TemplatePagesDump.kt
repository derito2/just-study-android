package com.dero.juststudykorean.data.qa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.pdf.PdfParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** LOCAL helper (not committed): the app's own reader (MuPDF) on the notes template PDF, written like extracted/<id>.json. */
@RunWith(AndroidJUnit4::class)
class TemplatePagesDump {
    @Test
    fun dump() {
        val parsed = PdfParser.parse(File("/data/local/tmp/Korean_Notes_Template.pdf"))
        val pages = JsonArray(parsed.pages.map { p ->
            buildJsonObject {
                put("page_number", p.pageNumber)
                put("text", p.text)
                put("headings", JsonArray(p.headings.map(::JsonPrimitive)))
                put("section", p.section?.let(::JsonPrimitive) ?: JsonNull)
                put("tables", JsonArray(p.tables.map { t ->
                    buildJsonObject {
                        put("rows", JsonArray(t.rows.map { r -> JsonArray(r.map(::JsonPrimitive)) }))
                        put("text", t.text)
                    }
                }))
                put("image_count", p.imageCount)
                put("char_count", p.charCount)
            }
        })
        val root = buildJsonObject {
            put("page_count", parsed.pageCount)
            put("table_count", parsed.tableCount)
            put("warnings", JsonArray(parsed.warnings.map(::JsonPrimitive)))
            put("pages", pages)
        }
        val json = Json { prettyPrint = true }
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "template_pages.json")
            .writeText(json.encodeToString(JsonElement.serializer(), root) + "\n")
    }
}
