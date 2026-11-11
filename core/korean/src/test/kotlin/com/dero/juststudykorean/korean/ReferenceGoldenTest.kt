package com.dero.juststudykorean.korean

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** referencePoints (grammar_points, the Grammar screen) against golden/index "points": every chunk of the test PDFs. */
class ReferenceGoldenTest {
    private fun ReferencePoint.toJson(): JSONObject = JSONObject()
        .put("category", category).put("kind", kind).put("pattern", pattern).put("meaning", meaning).put("page_number", pageNumber)
        .put("structure", JSONArray(structure)).put("examples", JSONArray(examples.map { JSONArray(it) }))
        .put("explanation", JSONArray(explanation)).put("notes", JSONArray(notes))

    @Test
    fun everyDocumentMatches() {
        var total = 0
        for (file in Golden.files("index")) {
            val name = file.nameWithoutExtension
            val want = Golden.obj("index/${file.name}").getJSONArray("points").objects()
            val got = ChunkerGoldenTest.chunks(name).flatMap { c -> referencePoints(c).map { c.chromaId to it } }
            assertEquals("$name count", want.size, got.size)
            want.zip(got).forEach { (w, g) ->
                assertEquals("$name chunk", w.getString("chunk"), g.first)
                assert(w.getJSONObject("point").similar(g.second.toJson())) { "$name ${g.first}: ${w.getJSONObject("point")} vs ${g.second.toJson()}" }
            }
            total += want.size
        }
        println("reference points: $total")
    }
}
