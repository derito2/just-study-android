package com.dero.juststudykorean.ai

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.korean.ai.JUDGE_VERSION
import com.dero.juststudykorean.korean.ai.judgeApproves
import com.dero.juststudykorean.korean.ai.judgePrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** An empty screen in front, so AICore takes the calls. */
class FrontActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

/**
 * Phase 7, task 4: PoC 2's 60 grading cases (30 right paraphrases, 30 wrong) through the gate, per setting. Writes only
 * counts to files/judge.txt: true and false positives. A setting is only usable with 0 false positives.
 */
@RunWith(AndroidJUnit4::class)
class JudgeBatchTest {
    private class Setting(val name: String, val temperature: Float?, val system: Boolean)

    /** The model without the system instruction (the PoC's calls had none). */
    private class NoSystem(val inner: NanoModel) : NanoModel by inner {
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?) = inner.generate("", prompt, maxTokens, temperature)
    }

    @Test
    fun pocCasesPerSetting() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val cases = JSONObject(ctx.assets.open("grade-cases.json").bufferedReader().readText()).getJSONArray("grade")
        ActivityScenario.launch(FrontActivity::class.java).use {
            AppForeground.visible = true
            val nano = MlKitNano()
            val state = NanoState(nano).also { s -> s.refresh() }
            repeat(50) { if (state.status.value != NanoStatus.Available) delay(200) }
            assumeTrue(state.status.value == NanoStatus.Available)
            val lines = mutableListOf<String>()
            val settings = (InstrumentationRegistry.getArguments().getString("settings") ?: "now,poc").split(",")
            for (s in listOf(Setting("now", 0.2f, true), Setting("poc", 0f, false), Setting("t0", 0f, true)).filter { it.name in settings }) {
                val gate = NanoGate(if (s.system) nano else NoSystem(nano), state) { false }
                var tp = 0; var fp = 0; var noAnswer = 0
                for (i in 0 until cases.length()) {
                    val c = cases.getJSONObject(i)
                    val asked = "What does '${c.getString("korean")}' mean?"
                    val out = gate.ask(AiRequest(AiUse.Judge, "$JUDGE_VERSION.${s.name}", judgePrompt(asked, c.getString("correct"), c.getString("given")), "$i", 32, s.temperature)) { it }
                    if (out == null) { noAnswer++; continue }
                    if (judgeApproves(out, c.getString("given"))) { if (c.getBoolean("label")) tp++ else fp++ }
                }
                lines += "${s.name} approved_right=$tp/30 approved_wrong=$fp/30 no_answer=$noAnswer"
            }
            File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "judge.txt").writeText(lines.joinToString("\n") + "\n")
        }
    }
}
