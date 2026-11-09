package com.dero.juststudykorean.korean

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GrammarGoldenTest {
    companion object {
        fun GrammarPoint.toJson(): JSONObject = JSONObject()
            .put("kind", kind).put("pattern", pattern).put("meaning", meaning).put("forms", JSONArray(forms))
            .put("examples", JSONArray(examples.map { JSONArray(listOf(it.first, it.second)) }))
            .put("explanation", explanation ?: JSONObject.NULL).put("choices", JSONArray(choices))
            .put("choice_answer", choiceAnswer ?: JSONObject.NULL)

        fun point(o: JSONObject) = GrammarPoint(
            o.getString("kind"), o.getString("pattern"), o.getString("meaning"), o.getJSONArray("forms").strings(),
            o.getJSONArray("examples").let { a -> List(a.length()) { a.getJSONArray(it).let { e -> e.getString(0) to e.getString(1) } } },
            o.optString("explanation").takeUnless { o.isNull("explanation") }, o.getJSONArray("choices").strings(),
            o.optString("choice_answer").takeUnless { o.isNull("choice_answer") },
        )
    }

    @Test
    fun everyChunkMatches() {
        var total = 0
        for (file in Golden.files("grammar")) {
            val name = file.nameWithoutExtension
            val want = Golden.arr("grammar/${file.name}").objects()
            if (want.isEmpty()) continue
            val chunks = ChunkerGoldenTest.chunks(name).associateBy { it.chromaId }
            for (entry in want) {
                val id = entry.getString("chunk")
                val expected = entry.getJSONArray("points").objects()
                val got = extractGrammar(chunks.getValue(id))
                assertEquals("$name $id count", expected.size, got.size)
                expected.zip(got).forEach { (w, g) -> assert(w.similar(g.toJson())) { "$name $id: $w vs ${g.toJson()}" } }
                total += expected.size
            }
        }
        println("grammar: $total")
    }
}
