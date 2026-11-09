package com.dero.juststudykorean.korean

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer

/** golden/text.json: every function, every case. ("nfd" cases are stored NFC: NFD is applied to the input.) */
class TextGoldenTest {
    private val g = Golden.obj("text.json")
    private fun JSONObject.input(key: String) = getString(key).let { if (optString("transform") == "nfd") Normalizer.normalize(it, Normalizer.Form.NFD) else it }

    @Test
    fun norm() = g.getJSONArray("norm").objects().forEachIndexed { i, c -> assertEquals("norm #$i", c.getString("out"), norm(c.input("in"))) }

    @Test
    fun initialConsonant() = g.getJSONArray("initial_consonant").objects().forEachIndexed { i, c ->
        assertEquals("initial #$i", c.optString("out").takeUnless { c.isNull("out") }, initialConsonant(c.input("in")))
    }

    @Test
    fun isCorrect() = g.getJSONArray("is_correct").objects().forEachIndexed { i, c ->
        assertEquals("is_correct #$i ${c.getString("kind")} ${c.getString("type")}", c.getBoolean("out"), isCorrect(c.getString("type"), c.input("given"), c.getString("correct")))
    }

    @Test
    fun englishAnswers() = g.getJSONArray("english_answers").objects().forEachIndexed { i, c ->
        assertEquals("english_answers #$i", c.getJSONArray("out").strings(), englishAnswers(c.getString("correct"), c.getBoolean("split")))
    }

    @Test
    fun englishSenses() = g.getJSONArray("english_senses").objects().forEachIndexed { i, c ->
        assertEquals("english_senses #$i", c.getJSONArray("out").strings().toSet(), englishSenses(c.getString("in")))
    }

    @Test
    fun isGrounded() = g.getJSONArray("is_grounded").objects().forEachIndexed { i, c ->
        assertEquals("is_grounded #$i", c.getBoolean("out"), isGrounded(c.getString("answer"), c.getString("source")))
    }

    @Test
    fun leaksAnswer() = g.getJSONArray("leaks_answer").objects().forEachIndexed { i, c ->
        assertEquals("leaks_answer #$i", c.getBoolean("out"), leaksAnswer(c.getString("prompt"), c.getString("answer")))
    }

    @Test
    fun widenAnswer() {
        val pools = g.getJSONObject("pools").let { p -> p.keys().asSequence().associateWith { k -> p.getJSONArray(k).objects().map(PairsGoldenTest::pair) } }
        g.getJSONArray("widen_answer").objects().forEachIndexed { i, c ->
            val got = widenAnswer(c.getString("answer"), pools.getValue(c.getString("pool")), c.getString("prompt"), c.getString("text"))
            assertEquals("widen_answer #$i", c.optString("out").takeUnless { c.isNull("out") }, got)
        }
    }
}
