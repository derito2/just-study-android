package com.dero.juststudykorean.korean

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Same cases as the PoC 2 checks (poc/2-nano), now on the JVM against ../../golden/. */
class GoldenTest {
    private fun golden(name: String) = JSONObject(File("../../golden/$name").readText())
    private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
    private fun JSONArray.strings() = List(length()) { getString(it) }

    @Test
    fun isGroundedMatchesGolden() {
        golden("text.json").getJSONArray("is_grounded").objects().forEachIndexed { i, c ->
            assertEquals("is_grounded #$i", c.getBoolean("out"), isGrounded(c.getString("answer"), c.getString("source")))
        }
    }

    @Test
    fun leaksAnswerMatchesGolden() {
        golden("text.json").getJSONArray("leaks_answer").objects().forEachIndexed { i, c ->
            assertEquals("leaks_answer #$i", c.getBoolean("out"), leaksAnswer(c.getString("prompt"), c.getString("answer")))
        }
    }

    @Test
    fun aeoFormsMatchGolden() {
        golden("korean.json").getJSONArray("aeo_forms").objects().forEach { c ->
            assertEquals("aeo_forms ${c.getString("in")}", c.getJSONArray("out").strings().toSet(), aeoForms(c.getString("in")))
        }
    }

    @Test
    fun pastMatchesGolden() {
        golden("korean.json").getJSONArray("past").objects().forEach { c ->
            assertEquals("past ${c.getString("in")}", c.optString("out").ifEmpty { null }, past(c.getString("in")))
        }
    }

    @Test
    fun koreanCoresMatchGolden() {
        golden("korean.json").getJSONArray("korean_cores").objects().forEach { c ->
            val want = c.getJSONArray("out").let { o -> List(o.length()) { j -> o.getJSONArray(j).let { p -> p.getString(0) to p.getBoolean(1) } } }
            assertEquals("korean_cores ${c.getString("in")}", want, koreanCores(c.getString("in")))
        }
    }

    @Test
    fun hasFormMatchesGolden() {
        val k = golden("korean.json")
        val aeo = k.getJSONArray("aeo").strings().toSet()
        k.getJSONArray("has_form").objects().forEachIndexed { i, c ->
            assertEquals("has_form #$i ${c.getString("form")}", c.getBoolean("out"), hasForm(c.getString("text"), c.getString("form"), aeo))
        }
    }
}
