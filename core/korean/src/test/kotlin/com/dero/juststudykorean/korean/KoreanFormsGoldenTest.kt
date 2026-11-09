package com.dero.juststudykorean.korean

import org.junit.Assert.assertEquals
import org.junit.Test

/** golden/korean.json: korean_cores, has_form, aeo_forms and past (ported in phase 2). */
class KoreanFormsGoldenTest {
    private val k = Golden.obj("korean.json")

    @Test
    fun aeoForms() = k.getJSONArray("aeo_forms").objects().forEach { c ->
        assertEquals("aeo_forms ${c.getString("in")}", c.getJSONArray("out").strings().toSet(), aeoForms(c.getString("in")))
    }

    @Test
    fun past() = k.getJSONArray("past").objects().forEach { c ->
        assertEquals("past ${c.getString("in")}", c.optString("out").ifEmpty { null }, past(c.getString("in")))
    }

    @Test
    fun koreanCores() = k.getJSONArray("korean_cores").objects().forEach { c ->
        val want = c.getJSONArray("out").let { o -> List(o.length()) { j -> o.getJSONArray(j).let { p -> p.getString(0) to p.getBoolean(1) } } }
        assertEquals("korean_cores ${c.getString("in")}", want, koreanCores(c.getString("in")))
    }

    @Test
    fun hasForm() {
        val aeo = k.getJSONArray("aeo").strings().toSet()
        k.getJSONArray("has_form").objects().forEachIndexed { i, c ->
            assertEquals("has_form #$i ${c.getString("form")}", c.getBoolean("out"), hasForm(c.getString("text"), c.getString("form"), aeo))
        }
    }
}
