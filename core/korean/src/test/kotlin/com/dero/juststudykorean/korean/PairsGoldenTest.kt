package com.dero.juststudykorean.korean

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PairsGoldenTest {
    companion object {
        fun VocabPair.toJson(): JSONObject = JSONObject()
            .put("korean", korean).put("english", english).put("romanization", romanization ?: JSONObject.NULL)
            .put("example_ko", exampleKo ?: JSONObject.NULL).put("example_en", exampleEn ?: JSONObject.NULL).put("source", source)
            .put("examples", org.json.JSONArray(examples.map { org.json.JSONArray(listOf(it.first, it.second)) }))
            .put("notes", org.json.JSONArray(notes))

        fun pair(o: JSONObject) = VocabPair(
            o.getString("korean"), o.getString("english"), o.optString("romanization").takeUnless { o.isNull("romanization") },
            o.optString("example_ko").takeUnless { o.isNull("example_ko") }, o.optString("example_en").takeUnless { o.isNull("example_en") },
            o.getString("source"), o.getJSONArray("examples").let { a -> List(a.length()) { a.getJSONArray(it).let { e -> e.getString(0) to e.getString(1) } } },
            o.getJSONArray("notes").strings(),
        )
    }

    @Test
    fun everyChunkMatches() {
        var total = 0
        for (file in Golden.files("pairs")) {
            val name = file.nameWithoutExtension
            val want = Golden.arr("pairs/${file.name}").objects()
            if (want.isEmpty()) continue
            val chunks = ChunkerGoldenTest.chunks(name).associateBy { it.chromaId }
            for (entry in want) {
                val id = entry.getString("chunk")
                val expected = entry.getJSONArray("pairs").objects()
                val got = extractPairs(chunks.getValue(id))
                assertEquals("$name $id count", expected.size, got.size)
                expected.zip(got).forEach { (w, g) -> assert(w.similar(g.toJson())) { "$name $id: $w vs ${g.toJson()}" } }
                total += expected.size
            }
        }
        println("pairs: $total")
    }
}
