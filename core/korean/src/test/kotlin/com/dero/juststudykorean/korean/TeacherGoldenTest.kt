package com.dero.juststudykorean.korean

import com.dero.juststudykorean.korean.teacher.MemoryStore
import com.dero.juststudykorean.korean.teacher.SelfCheck
import com.dero.juststudykorean.korean.teacher.Teacher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * golden/teacher/<pdf>.jsonl: the web's self-check questions (seed 7, sample 40) and Teacher's answers, made with the
 * self-check's in-memory store ([MemoryStore]). Compared by question: first the text, then the sources.
 */
class TeacherGoldenTest {
    @Test
    fun everyAnswerMatches() {
        var total = 0; var text = 0; var sources = 0
        val wrong = mutableListOf<String>()
        for (file in File("../../golden/teacher").listFiles { f -> f.extension == "jsonl" }!!.sortedBy { it.name }) {
            val name = file.nameWithoutExtension
            val teacher = Teacher(MemoryStore(ChunkerGoldenTest.chunks(name)))
            for (row in file.readLines().filter { it.isNotBlank() }.map(::JSONObject)) {
                total++
                val got = teacher.ask(row.getString("question"))
                val wantSources = row.getJSONArray("sources").objects().map { it.getInt("n") to it.getString("chunk") }
                if (got.answer == row.getString("answer") && got.promptVersion == row.getString("promptVersion")) {
                    text++
                    if (got.sources.map { it.n to it.hit.id } == wantSources) sources++
                    else wrong += "$name [sources] ${row.getString("check")}: ${got.sources.map { it.hit.id }} vs $wantSources"
                } else {
                    wrong += "$name [text] ${row.getString("check")} ${row.getString("question")}\n  want ${row.getString("promptVersion")}: " +
                        row.getString("answer").replace("\n", "⏎") + "\n  got  ${got.promptVersion}: " + got.answer.replace("\n", "⏎")
                }
            }
        }
        println("teacher: $total questions, $text same text, $sources same text and sources")
        wrong.take(15).forEach(::println)
        assertEquals("same text and sources", total, sources)
    }

    /** The self-check asks, on each test PDF, the same questions the goldens recorded (seed 7, sample 40), and finds nothing wrong. */
    @Test
    fun selfCheckAsksTheGoldenQuestionsAndPasses() {
        for (file in File("../../golden/teacher").listFiles { f -> f.extension == "jsonl" }!!.sortedBy { it.name }) {
            val name = file.nameWithoutExtension
            val report = SelfCheck(Teacher(MemoryStore(ChunkerGoldenTest.chunks(name)))).run()
            val want = file.readLines().filter { it.isNotBlank() }.map { JSONObject(it).getString("check") }.groupingBy { it }.eachCount()
            assertEquals("$name questions per check", want, report.byCheck)
            assertEquals("$name failures", emptyMap<String, Int>(), report.counts())
        }
    }
}
