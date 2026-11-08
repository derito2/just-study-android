package com.dero.juststudykorean.poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.StreamingCallback
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import com.dero.juststudykorean.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    // AICore rejects calls (error 30) once the screen turns off and the app drops to the background.
    private fun keepScreenOn(scenario: ActivityScenario<MainActivity>) =
        scenario.onActivity {
            it.setShowWhenLocked(true); it.setTurnScreenOn(true)
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

    /** Step 2b: one fixed English prompt, latency to logcat (tag NanoPoc). No note text. */
    @Test
    fun smokeShortSentence() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            keepScreenOn(scenario)
            val model = Generation.getClient()
            try {
                val t0 = SystemClock.elapsedRealtime()
                var first = -1L
                val out = model.generateContent("Reply with one short sentence: what is a good way to start the day?", object : StreamingCallback {
                    override fun onNewText(text: String) { if (first < 0) first = SystemClock.elapsedRealtime() - t0 }
                }).candidates.first().text.orEmpty()
                val total = SystemClock.elapsedRealtime() - t0
                Log.i("NanoPoc", "smoke firstTokenMs=$first totalMs=$total outChars=${out.length}")
                assertTrue(out.isNotBlank())
            } finally { model.close() }
        }
    }

    @Test
    fun validatorsMatchGolden() {
        val cases = cases()
        val g = cases.getJSONArray("is_grounded")
        for (i in 0 until g.length()) g.getJSONObject(i).let {
            assertEquals("is_grounded #$i", it.getBoolean("out"), NanoTasks.isGrounded(it.getString("answer"), it.getString("source")))
        }
        val l = cases.getJSONArray("leaks_answer")
        for (i in 0 until l.length()) l.getJSONObject(i).let {
            assertEquals("leaks_answer #$i", it.getBoolean("out"), NanoTasks.leaksAnswer(it.getString("prompt"), it.getString("answer")))
        }
    }

    private fun cases() = InstrumentationRegistry.getInstrumentation().context.assets
        .open("poc/nano-cases.json").bufferedReader().use { JSONObject(it.readText()) }

    /**
     * Step 4/5: args `run` (wifi | airplane) and `perTask` (30 | 10; grading uses 2x). One JSON line per call in
     * files/poc/nano-results-<run>.jsonl, written case by case so a killed run resumes where it stopped.
     */
    @Test
    fun tasksMeetLatencyAndValidity() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val run = args.getString("run", "wifi")
        val perTask = args.getString("perTask", "30").toInt()
        val out = File(poc, "nano-results-$run.jsonl")
        val done = if (out.exists()) out.readLines().filter { it.isNotBlank() }.map { JSONObject(it).getString("id") }.toSet() else emptySet()
        val cases = cases()
        val jobs = listOf("coach" to NanoTasks::coach, "explain" to NanoTasks::explain, "mcq" to NanoTasks::mcq, "grade" to NanoTasks::grade)
            .flatMap { (task, make) ->
                val arr = cases.getJSONArray(task)
                val n = if (task == "grade") perTask * 2 else perTask
                // Grading: take positives and negatives alike when perTask < 30.
                val idx = if (task == "grade") (0 until 30).take(n / 2) + (30 until 60).take(n / 2) else (0 until n).toList()
                idx.map { arr.getJSONObject(it) }.map { Triple(task, it.getString("id"), make(it)) }
            }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            keepScreenOn(scenario)
            val model = Generation.getClient()
            try {
                assertEquals("AVAILABLE", statusName(model.checkStatus()))
                model.generateContent("Say hi.") // warm-up, not counted
                var quota = 0
                for ((task, id, job) in jobs) {
                    if (id in done) continue
                    val line = JSONObject().put("run", run).put("task", task).put("id", id)
                    val t0 = SystemClock.elapsedRealtime()
                    var first = -1L
                    try {
                        val text = model.generateContent(generateContentRequest(TextPart(job.prompt)) {
                            maxOutputTokens = job.maxTokens
                            job.temperature?.let { temperature = it }
                        }, object : StreamingCallback {
                            override fun onNewText(text: String) { if (first < 0) first = SystemClock.elapsedRealtime() - t0 }
                        }).candidates.first().text.orEmpty()
                        val total = SystemClock.elapsedRealtime() - t0
                        val (valid, reason) = job.validate(text)
                        line.put("ok", true).put("latencyMs", total).put("firstTokenMs", first)
                            .put("outTokens", outTokens(model, text)).put("valid", valid).put("reason", reason)
                    } catch (e: GenAiException) {
                        val isQuota = e.errorCode == GenAiException.ErrorCode.BUSY ||
                            e.errorCode == GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED
                        if (isQuota) quota++
                        line.put("ok", false).put("latencyMs", SystemClock.elapsedRealtime() - t0).put("valid", false)
                            .put("reason", "${if (isQuota) "quota" else "error"}:${e.errorCode}")
                    }
                    out.appendText(line.toString() + "\n")
                    Log.i("NanoPoc", line.toString()) // ids, numbers and reason codes only, no text
                    assertFalse("quota errors above 5%", quota > jobs.size * 5 / 100)
                }
            } finally { model.close() }
        }
    }

    private suspend fun outTokens(model: GenerativeModel, text: String) =
        if (text.isBlank()) 0 else runCatching { model.countTokens(generateContentRequest(TextPart(text)) {}).totalTokens }.getOrDefault(-1)
}
