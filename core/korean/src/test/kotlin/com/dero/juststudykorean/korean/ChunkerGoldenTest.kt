package com.dero.juststudykorean.korean

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ChunkerGoldenTest {
    companion object {
        /** The parser's pages for a golden PDF (golden/parser/<name>.json); empty if the PDF did not parse. */
        fun pages(name: String): List<ExtractedPage> =
            (Golden.obj("parser/$name.json").optJSONArray("pages") ?: org.json.JSONArray()).objects().map { p ->
                ExtractedPage(
                    p.getInt("page_number"),
                    p.optString("section").takeUnless { p.isNull("section") },
                    p.getString("text"),
                    (p.optJSONArray("tables") ?: org.json.JSONArray()).objects().map { it.getString("text") },
                )
            }

        /** document_id the golden used: the order of the PDFs in golden/manifest (1-based). */
        fun documentId(name: String): Long = Golden.arr("chunks/$name.json").objects().firstOrNull()?.getLong("document_id")
            ?: error("no chunks for $name")

        fun chunks(name: String): List<Chunk> = chunkDocument(documentId(name), "$name.pdf", pages(name))
    }

    @Test
    fun everyDocumentMatches() {
        var total = 0
        for (file in Golden.files("chunks")) {
            val name = file.nameWithoutExtension
            val want = Golden.arr("chunks/${file.name}").objects()
            val got = if (want.isEmpty()) emptyList() else chunks(name)
            assertEquals("$name count", want.size, got.size)
            want.zip(got).forEach { (w, g) ->
                val where = "$name ${w.getString("chroma_id")}"
                assertEquals("$where id", w.getString("chroma_id"), g.chromaId)
                assertEquals("$where section", w.optString("section").takeUnless { w.isNull("section") }, g.section)
                assertEquals("$where topic", w.getString("topic"), g.topic)
                assertEquals("$where type", w.getString("content_type"), g.contentType)
                assertEquals("$where language", w.getString("language"), g.language)
                assertEquals("$where text", w.getString("text"), g.text)
                val meta = w.getJSONObject("metadata")
                assert(meta.similar(JSONObject(g.metadata()))) { "$where metadata: $meta vs ${g.metadata()}" }
            }
            total += want.size
        }
        println("chunks: $total")
    }
}
