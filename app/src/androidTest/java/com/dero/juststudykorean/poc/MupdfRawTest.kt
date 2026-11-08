package com.dero.juststudykorean.poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** PoC 1, step 1: MuPDF letters vs PyMuPDF `rawdict` (golden/parser_raw), one test per test PDF. */
@RunWith(AndroidJUnit4::class)
class MupdfRawTest {
    private fun check(name: String) {
        val m = Poc.measureAsset(name)
        // Letter count and NFC text per page must match on 100 % of pages.
        assertEquals("$name letters per page", m.getInt("pages"), m.getInt("pagesLetterCountEqual"))
        assertEquals("$name text per page", m.getInt("pages"), m.getInt("pagesTextEqual"))
    }

    @Test fun rawCharsMatchGolden_Frases_dramas_y_redes() = check("Frases_dramas_y_redes")

    @Test fun rawCharsMatchGolden_Gramatica_semana_14() = check("Gramatica_semana_14")

    @Test fun rawCharsMatchGolden_Korean_Notes_Example() = check("Korean_Notes_Example")

    @Test fun rawCharsMatchGolden_Vocab_cafe_y_comida() = check("Vocab_cafe_y_comida")

    @Test fun rawCharsMatchGolden_Vocab_emociones_redes() = check("Vocab_emociones_redes")

    @Test fun rawCharsMatchGolden_Vocab_viajes_irregulares() = check("Vocab_viajes_irregulares")
}
