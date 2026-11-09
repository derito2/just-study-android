package com.dero.juststudykorean.korean

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** golden/builders.json: the rule builders with Python's random.Random(seed) per case, options in the same order. */
class BuildersGoldenTest {
    private val golden = Golden.obj("builders.json")
    private val seed = golden.getLong("seed")

    private class Doc(val chunks: List<Chunk>) {
        val pairs = chunks.flatMap { c -> extractPairs(c).map { c to it } }
        val points = chunks.flatMap { c -> extractGrammar(c).map { c to it } }
        val grounding = chunks.joinToString("\n") { it.text }
    }

    private val docs = HashMap<String, Doc>()
    private fun doc(name: String) = docs.getOrPut(name) { Doc(ChunkerGoldenTest.chunks(name)) }

    private fun check(where: String, want: JSONObject, got: Built) {
        if (want.has("reason")) {
            assertEquals("$where reason", want.getString("reason"), got.reason)
            return
        }
        val c = got.candidate ?: throw AssertionError("$where: expected a question, got ${got.reason}")
        assertEquals("$where type", want.getString("type"), c.type)
        assertEquals("$where prompt", want.getString("prompt"), c.prompt)
        assertEquals("$where answer", want.getString("answer"), c.answer)
        assertEquals("$where options", want.optJSONArray("options")?.strings(), c.options)
        assertEquals("$where explanation", want.getString("explanation"), c.explanation)
        assertEquals("$where chunk", want.getString("chunk"), c.chunk.chromaId)
        assertEquals("$where version", want.getString("prompt_version"), c.promptVersion)
        assertEquals("$where model", want.getString("model"), c.model)
    }

    @Test
    fun fromPair() {
        val cases = golden.getJSONArray("from_pair").objects()
        cases.forEach { c ->
            val d = doc(c.getString("doc"))
            val (chunk, pair) = d.pairs[c.getInt("pair")]
            val got = buildFromPair(c.getString("kind"), pair, chunk, d.pairs.map { it.second }, d.grounding, PyRandom(seed))
            check("${c.getString("doc")} pair ${c.getInt("pair")} ${c.getString("kind")}", c.getJSONObject("out"), got)
        }
        println("from_pair: ${cases.size}")
    }

    @Test
    fun blank() {
        val cases = golden.getJSONArray("blank").objects()
        cases.forEach { c ->
            val d = doc(c.getString("doc"))
            val chunk = d.chunks.first { it.chromaId == c.getString("chunk") }
            val i = c.getJSONObject("in")
            val gb = GeneratedBlank(i.getString("sentence"), i.getString("answer"), i.getJSONArray("distractors").strings(), i.getString("explanation"))
            check("${c.getString("doc")} blank ${c.getInt("blank")} ${c.getString("kind")}", c.getJSONObject("out"), buildBlank(c.getString("kind"), gb, chunk, d.grounding, PyRandom(seed)))
        }
        println("blank: ${cases.size}")
    }

    @Test
    fun grammar() {
        val cases = golden.getJSONArray("grammar").objects()
        // Same order as the generator: per document, per question type, grammarItems.
        val byDoc = cases.groupBy { it.getString("doc") }
        for ((name, list) in byDoc) {
            val d = doc(name)
            val points = d.points.map { it.second }
            val items = GRAMMAR_VARIANTS.keys.flatMap { kind -> grammarItems(kind, d.chunks).map { kind to it } }
            assertEquals("$name items", list.size, items.size)
            list.zip(items).forEach { (c, item) ->
                val (kind, triple) = item
                val (chunk, point, variant) = triple
                val where = "$name ${c.getString("chunk")} ${c.getString("kind")} ${c.getString("variant")}"
                assertEquals("$where item", listOf(c.getString("kind"), c.getString("variant"), c.getString("chunk"), c.getInt("point")),
                    listOf(kind, variant, chunk.chromaId, points.indexOf(point)))
                check(where, c.getJSONObject("out"), buildGrammar(kind, variant, point, chunk, points, d.grounding, PyRandom(seed)))
            }
        }
        println("grammar: ${cases.size}")
    }
}
