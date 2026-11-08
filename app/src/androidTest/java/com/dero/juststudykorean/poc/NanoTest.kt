package com.dero.juststudykorean.poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** PoC 2 (Gemini Nano, ML Kit Prompt API). Step 1: the feature must be AVAILABLE without network. */
@RunWith(AndroidJUnit4::class)
class NanoTest {
    private val poc = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "poc").apply { mkdirs() }

    private fun statusName(status: Int) = when (status) {
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.DOWNLOADING -> "DOWNLOADING"
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> "UNKNOWN($status)"
    }

    @Test
    fun featureStatusIsAvailable() = runBlocking {
        val model = Generation.getClient()
        val status = try { model.checkStatus() } finally { model.close() }
        File(poc, "nano-status.json").writeText(JSONObject().put("status", statusName(status)).toString())
        assertEquals("AVAILABLE", statusName(status))
    }
}
