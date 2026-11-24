package com.dero.juststudykorean.korean.grammar

import com.dero.juststudykorean.korean.Golden
import com.dero.juststudykorean.korean.objects
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * F3 (FASE_11_PLAN PR 5): which ending each grammar point of your notes is. Stop condition: at least 70 % of the endings
 * of golden/grammar and of the owner's WW1_Grammar (local only) are recognized; particles don't count.
 */
class EndingMatchTest {
    private fun share(patterns: List<String>): Pair<Int, List<String>> {
        val endings = patterns.filterNot(::isParticle)
        val missed = endings.filter { endingOf(it) == null }
        return endings.size to missed
    }

    private fun assertAtLeast70(name: String, patterns: List<String>) {
        val (total, missed) = share(patterns)
        val pct = 100 * (total - missed.size) / total
        println("$name: ${total - missed.size}/$total endings recognized ($pct %)")
        assertTrue("$name: $pct % < 70 %; missed: $missed", pct >= 70)
    }

    @Test
    fun theGoldenGrammarPoints() {
        val patterns = Golden.files("grammar").flatMap { f -> JSONArray(f.readText()).objects().flatMap { c -> c.getJSONArray("points").objects().map { it.getString("pattern") } } }
        assertAtLeast70("golden/grammar", patterns)
    }

    @Test
    fun theOwnersWw1Grammar() { // LOCAL ONLY: qa/private is gitignored; prints only counts
        val file = File("../../qa/private/anki/reader/WW1_Grammar.read.json")
        assumeTrue(file.isFile)
        val patterns = JSONObject(file.readText()).getJSONArray("grammar").objects().map { it.getString("pattern") }
        assertEquals(18, patterns.size)
        assertAtLeast70("WW1_Grammar", patterns)
    }

    @Test
    fun aTableOfPatterns() {
        val cases = mapOf(
            "-아서/어서" to Ending.SoBecause, "-어서/아서" to Ending.SoBecause, "-(으)면" to Ending.If, "-(으)ㄹ게요" to Ending.Promise,
            "-ㄹ 지/ 을 지" to Ending.Whether, "-아/어 보다" to Ending.Try, "-아/어야 하다" to Ending.Must, "-지 말다" to Ending.Dont,
            "-(으)ㄴ/는데(요)" to Ending.Background, "-지(요), -죠" to Ending.Confirm, "-구나, -군(요)" to Ending.Realize,
            "-더라(고(요))" to Ending.Recall, "-어/아가지고" to Ending.AndThen, "(으)ㄹ 만하다" to Ending.WorthIt, "-는 동안" to Ending.During,
        )
        for ((pattern, ending) in cases) assertEquals(pattern, ending, endingOf(pattern))
        assertNull(endingOf("-어/아 있다 vs 고 있다")) // a comparison of two forms is out
        assertNull(endingOf("-거야, -거예요")) // 것이에요: not one ending conjugate can list
        assertTrue(isParticle("이/가") && isParticle("(으)로") && isParticle("까지") && !isParticle("-네요"))
    }
}
