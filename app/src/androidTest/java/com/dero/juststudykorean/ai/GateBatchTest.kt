package com.dero.juststudykorean.ai

import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.MainActivity
import com.dero.juststudykorean.korean.ai.inventedKorean
import com.dero.juststudykorean.korean.ai.isEnglish
import com.dero.juststudykorean.korean.isGrounded
import com.dero.juststudykorean.korean.leaksAnswer
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 6, task 1 on the OPPO: PoC 2's batches (assets/ai/nano-cases.json, made only from the public goldens) through
 * NanoGate. Writes only numbers to files/ai/gate-batch.txt (per batch: calls, p50/p90, valid, results); never the
 * model's text. Skipped unless Nano is Available. Arg `perTask` (default 10; grading uses 2x).
 */
@RunWith(AndroidJUnit4::class)
class GateBatchTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class Job(val use: AiUse, val prompt: String, val input: String, val maxTokens: Int, val temperature: Float?, val validate: (String) -> Boolean)

    private val option = Regex("""^\s*([A-D])[).:]\s*(.+?)\s*$""")
    private val answerLine = Regex("""ANSWER\s*:\s*\(?([A-D])""", RegexOption.IGNORE_CASE)

    private fun strings(o: JSONObject, k: String) = o.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } }

    private fun coach(c: JSONObject): Job {
        val facts = "Weak topic: ${c.getString("weakTopic")}\nWords from that topic: ${strings(c, "words").joinToString(", ")}\n" +
            "Cards due today: ${c.getInt("dueCards")}\nAccuracy last 7 days: ${c.getInt("accuracyPct")}%\nStudy streak: ${c.getInt("streakDays")} days"
        val prompt = "You are a friendly Korean study coach. Write 2 or 3 short sentences in English for the learner, " +
            "using only these facts. Do not use any Korean word that is not listed in the facts.\n\nFacts:\n$facts"
        return Job(AiUse.Coach, prompt, facts, 120, null) { it.isNotBlank() && inventedKorean(it, facts).isEmpty() }
    }

    private fun explain(c: JSONObject): Job {
        val entry = c.getString("entry")
        val prompt = "Explain the Korean grammar point below to a beginner in 3 or 4 short English sentences. " +
            "You may quote the Korean pattern and the Korean examples from the entry, but write no other Korean.\n\nEntry:\n$entry"
        return Job(AiUse.Grammar, prompt, entry, 256, null) { isEnglish(it) && inventedKorean(it, entry).isEmpty() }
    }

    private fun mcq(c: JSONObject): Job {
        val ko = c.getString("korean"); val en = c.getString("english"); val source = c.getString("source")
        val prompt = "Write one multiple choice question for a Korean learner. The question asks what '$ko' means. " +
            "The correct option is \"$en\". Take the 3 wrong options from other English meanings in the notes below. " +
            "Answer in exactly this format and nothing else:\nQUESTION: ...\nA) ...\nB) ...\nC) ...\nD) ...\nANSWER: <letter>\n\nNotes:\n$source"
        return Job(AiUse.Questions, prompt, source, 160, 0f) { out ->
            val question = out.lines().firstOrNull { it.trim().startsWith("QUESTION", ignoreCase = true) }?.substringAfter(":")?.trim()
            val opts = out.lines().mapNotNull { option.find(it) }.associate { it.groupValues[1] to it.groupValues[2] }
            val letter = answerLine.find(out)?.groupValues?.get(1)?.uppercase()
            question != null && opts.size == 4 && letter != null && letter in opts && opts.values.map(::norm).toSet().size == 4 &&
                norm(opts.getValue(letter)) == norm(en) && isGrounded(opts.getValue(letter), source) && !leaksAnswer(question, opts.getValue(letter))
        }
    }

    private fun grade(c: JSONObject): Job {
        val prompt = "A Korean learner was asked what '${c.getString("korean")}' means. The correct answer is " +
            "\"${c.getString("correct")}\". The learner wrote \"${c.getString("given")}\". " +
            "Does the learner's answer mean the same thing as the correct answer? Reply with YES or NO first, then one short reason."
        val label = c.getBoolean("label")
        return Job(AiUse.Judge, prompt, c.toString(), 32, 0.2f) { out ->
            val said = out.trim().uppercase().let { if (it.startsWith("YES")) true else if (it.startsWith("NO")) false else null }
            said == label
        }
    }

    private fun pct(xs: List<Long>, p: Int) = xs.sorted().let { if (it.isEmpty()) 0 else it[((it.size - 1) * p) / 100] }

    @Test
    fun pocBatchesThroughTheGate() = runBlocking {
        val perTask = InstrumentationRegistry.getArguments().getString("perTask", "10").toInt()
        val cases = InstrumentationRegistry.getInstrumentation().context.assets.open("ai/nano-cases.json").bufferedReader().use { JSONObject(it.readText()) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val model = MlKitNano()
            val state = NanoState(model)
            state.refresh()
            val until = SystemClock.elapsedRealtime() + 10_000
            while (state.status.value != NanoStatus.Available && SystemClock.elapsedRealtime() < until) delay(200)
            assumeTrue("Nano is not Available: ${state.status.value}", state.status.value == NanoStatus.Available)
            val gate = NanoGate(model, state) { false } // no PDF is processed during this test
            gate.ask(AiRequest(AiUse.Coach, "warmup", "Say hi.", "", 8)) { it } // warm-up, not counted
            val lines = mutableListOf<String>()
            var busy = 0
            for ((task, make) in listOf<Pair<String, (JSONObject) -> Job>>("coach" to ::coach, "explain" to ::explain, "mcq" to ::mcq, "grade" to ::grade)) {
                val arr = cases.getJSONArray(task)
                val idx = if (task == "grade") (0 until 30).take(perTask) + (30 until 60).take(perTask) else (0 until perTask).toList()
                val times = mutableListOf<Long>()
                val results = mutableMapOf<String, Int>()
                var valid = 0
                for (i in idx) {
                    val job = make(arr.getJSONObject(i))
                    delay(NanoGate.GAP_MS) // the gate's pacing happens here, so the time below is the call itself
                    val t0 = SystemClock.elapsedRealtime()
                    val ok = gate.ask(AiRequest(job.use, "batch.$task", job.prompt, job.input, job.maxTokens, job.temperature)) { out -> out.takeIf(job.validate) } != null
                    times += SystemClock.elapsedRealtime() - t0
                    if (ok) valid++
                    results.merge(gate.lastResult, 1, Int::plus)
                    if (gate.lastResult == "busy") busy++
                }
                lines += "$task calls=${idx.size} p50=${pct(times, 50)}ms p90=${pct(times, 90)}ms valid=$valid results=$results"
            }
            File(context.filesDir, "ai").apply { mkdirs() }.resolve("gate-batch.txt").writeText(lines.joinToString("\n") + "\nbusy_after_retry=$busy\n")
            assertEquals("BUSY after the retry", 0, busy)
        }
    }
}
