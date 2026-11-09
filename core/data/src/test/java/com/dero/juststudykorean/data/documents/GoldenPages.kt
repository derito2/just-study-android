package com.dero.juststudykorean.data.documents

import com.dero.juststudykorean.pdf.Box
import com.dero.juststudykorean.pdf.PageContent
import com.dero.juststudykorean.pdf.ParsedDocument
import com.dero.juststudykorean.pdf.Table
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

internal fun golden(path: String): JsonElement = Json.parseToJsonElement(File("../../golden/$path").readText())

/** What MuPDF gives for a test PDF (golden/parser, the web's parse_pdf; MuPDF itself is checked on the phone). */
internal fun goldenParsed(name: String): ParsedDocument {
    val g = golden("parser/$name.json").jsonObject
    fun strings(e: JsonElement) = e.jsonArray.map { it.jsonPrimitive.content }
    val pages = g.getValue("pages").jsonArray.map { p ->
        val o = p.jsonObject
        PageContent(
            o.getValue("page_number").jsonPrimitive.int, o.getValue("text").jsonPrimitive.content, strings(o.getValue("headings")),
            (o["section"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            o.getValue("tables").jsonArray.map { t ->
                val to = t.jsonObject
                val b = to.getValue("bbox").jsonArray.map { it.jsonPrimitive.double }
                Table(to.getValue("rows").jsonArray.map(::strings), to.getValue("text").jsonPrimitive.content, Box(b[0], b[1], b[2], b[3]))
            },
            o.getValue("image_count").jsonPrimitive.int, o.getValue("char_count").jsonPrimitive.int,
        )
    }
    return ParsedDocument(g.getValue("page_count").jsonPrimitive.int, pages, g.getValue("char_count").jsonPrimitive.int,
        g.getValue("image_count").jsonPrimitive.int, g.getValue("table_count").jsonPrimitive.int, strings(g.getValue("warnings")))
}
