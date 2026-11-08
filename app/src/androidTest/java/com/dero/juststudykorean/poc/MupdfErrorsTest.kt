package com.dero.juststudykorean.poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** PoC 1: the web's PdfError verdicts (encrypted, empty, scanned, images, corrupt) with MuPDF. */
@RunWith(AndroidJUnit4::class)
class MupdfErrorsTest {
    @Test
    fun errorsMatchTheWeb() {
        val expected = JSONObject(Poc.assetText("errors/expected.json"))
        val out = JSONObject()
        var equal = 0
        for (name in expected.keys().asSequence().sorted()) {
            val want = expected.getJSONObject(name)
            val (error, message) = Poc.classify(Poc.asset("errors/$name").path)
            // optString() turns a JSON null into the string "null": read nulls explicitly.
            fun field(key: String) = if (want.isNull(key)) null else want.getString(key)
            val same = field("error") == error && field("message") == message
            if (same) equal++
            out.put(name, JSONObject().put("expected", want.opt("error") ?: JSONObject.NULL)
                .put("mupdf", error ?: JSONObject.NULL).put("sameMessage", same))
        }
        Poc.write("errors.json", JSONObject().put("cases", expected.length()).put("equal", equal).put("byFile", out))
        assertEquals(expected.length(), equal)
    }
}
