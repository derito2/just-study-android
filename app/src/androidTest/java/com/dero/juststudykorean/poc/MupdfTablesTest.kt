package com.dero.juststudykorean.poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PoC 1, step 4 (spike): rebuild ruled tables from the vector segments and compare with
 * pdfplumber (golden/parser pages[].tables[].rows). Measured, not asserted: the report says
 * what fails. Neither the test PDFs nor yours have tables: Tablas_sinteticas.pdf (2 ruled
 * tables, stroked and filled borders) is the only real check.
 */
@RunWith(AndroidJUnit4::class)
class MupdfTablesTest {
    @Test
    fun latticeMatchesPdfplumber() {
        Poc.write("tables-assets.json", Poc.tablesSummary((Poc.ASSET_PDFS + "Tablas_sinteticas").map { Poc.measureAsset(it) }))
    }
}
