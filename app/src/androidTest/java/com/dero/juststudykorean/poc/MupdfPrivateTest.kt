package com.dero.juststudykorean.poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * PoC 1, step 5: your PDFs, pushed to files/poc/in/ with their golden (golden/private). Only
 * metrics leave the phone (files/poc/private-summary.json): never text from your notes.
 */
@RunWith(AndroidJUnit4::class)
class MupdfPrivateTest {
    @Test
    fun privatePdfsMatchGolden() {
        val inbox = Poc.dir("in")
        val pdfs = inbox.listFiles { f -> f.extension == "pdf" }.orEmpty().sortedBy { it.name }
        assumeTrue("no private PDFs in files/poc/in", pdfs.isNotEmpty())
        val all = pdfs.mapIndexed { i, pdf ->
            val golden = { kind: String -> File(inbox, "${pdf.nameWithoutExtension}.$kind.json").readText() }
            // Anonymous label: the file name is not shown in the summary.
            Poc.measure("private-${i + 1}", pdf, golden("parser_raw"), golden("parser"), keepLetters = false)
        }
        Poc.write("private-summary.json", JSONObject().put("pdfs", JSONArray(all)).put("tables", Poc.tablesSummary(all)))
    }
}
