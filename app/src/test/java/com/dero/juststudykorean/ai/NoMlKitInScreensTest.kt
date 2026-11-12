package com.dero.juststudykorean.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** R-013: no screen talks to ML Kit; every model call goes through NanoGate (:core:ai). */
class NoMlKitInScreensTest {
    @Test
    fun onlyCoreAiImportsMlKit() {
        val offenders = listOf("src/main", "../core/data/src/main", "../core/korean/src/main").flatMap { dir ->
            File(dir).walkTopDown().filter { it.extension == "kt" && "com.google.mlkit" in it.readText() }.map { it.path }.toList()
        }
        assertEquals(emptyList<String>(), offenders)
    }
}
