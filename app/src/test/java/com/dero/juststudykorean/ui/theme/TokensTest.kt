package com.dero.juststudykorean.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

class TokensTest {
    private val tokens = JSONObject(File("../design/tokens.json").readText())

    /** "#116eee" or "rgba(37, 43, 47, 0.4)" → ARGB with the alpha rounded to 255. */
    private fun argb(css: String): Int {
        val v = css.trim()
        if (v.startsWith("#")) return (0xFF000000 or v.drop(1).toLong(16)).toInt()
        val (r, g, b, a) = v.substringAfter("(").substringBefore(")").split(",").map { it.trim() }
        return ((a.toDouble() * 255).roundToInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    private fun pascal(name: String) = name.split("-").joinToString("") { it.replaceFirstChar(Char::uppercase) }

    private fun tokenColor(field: String): Color? =
        runCatching { Tokens::class.java.getMethod("get${field}-0d7_KjU").invoke(Tokens) as Long }
            .getOrNull()?.let { Color(it.toULong()) }

    @Test
    fun everyColorMatchesTokensJson() {
        val list = tokens.getJSONObject("color").getJSONArray("tokens")
        for (i in 0 until list.length()) {
            val t = list.getJSONObject(i)
            val name = pascal(t.getString("name"))
            val value = t.get("value")
            val expected = if (value is JSONObject) mapOf("${name}Light" to value.getString("light"), "${name}Dark" to value.getString("dark"))
            else mapOf(name to value as String)
            for ((field, css) in expected) {
                val color = tokenColor(field)
                assertNotNull("Tokens.$field missing", color)
                assertEquals("Tokens.$field", argb(css), color!!.toArgb())
            }
        }
    }

    @Test
    fun schemeMapping() {
        assertEquals(Tokens.Action, lightScheme.primary)
        assertEquals(Tokens.SurfaceDark, darkScheme.surface)
        assertEquals(Tokens.Action, darkScheme.primary) // buttons do not change in dark mode
    }
}
