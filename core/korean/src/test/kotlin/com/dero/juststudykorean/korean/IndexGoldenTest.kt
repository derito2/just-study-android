package com.dero.juststudykorean.korean

import com.dero.juststudykorean.korean.teacher.Hit
import com.dero.juststudykorean.korean.teacher.buildIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/** buildIndex (Teacher's NotesIndex) against golden/index: points (chunk and pattern), sentences, verbs and their -아/-어 forms. */
class IndexGoldenTest {
    @Test
    fun everyDocumentMatches() {
        var total = 0
        for (file in Golden.files("index")) {
            val name = file.nameWithoutExtension
            val want = Golden.obj("index/${file.name}")
            val index = buildIndex(ChunkerGoldenTest.chunks(name).map { Hit(it.chromaId, it.text, 0.0, it) })
            assertEquals("$name points", want.getJSONArray("points").objects().map { it.getString("chunk") to it.getJSONObject("point").getString("pattern") },
                index.points.map { (h, p) -> h.id to p.pattern })
            assertEquals("$name sentences", want.getJSONArray("sentences").objects().map { it.getString("text") to it.getString("chunk") },
                index.sentences.map { (s, h) -> s to h.id })
            assertEquals("$name verbs", want.getJSONArray("verbs").strings(), index.verbs.sorted())
            assertEquals("$name aeo", want.getJSONArray("aeo").strings(), index.aeo.sorted())
            total += index.points.size + index.sentences.size + index.verbs.size
        }
        println("index: $total")
    }
}
