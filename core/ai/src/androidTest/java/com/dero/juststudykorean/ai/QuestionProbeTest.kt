package com.dero.juststudykorean.ai

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.ai.SYSTEM_PROMPT
import com.dero.juststudykorean.korean.ai.coachPrompt
import com.dero.juststudykorean.korean.ai.questionPrompt
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 7: why the quiz's question call hung on the OPPO. Synthetic notes only (never the owner's). For each prompt:
 * tokens (countTokens), the model's limit, and the gate's result and time with a 30 s deadline. Writes only counts.
 */
@RunWith(AndroidJUnit4::class)
class QuestionProbeTest {
    private fun chunk(text: String) = Chunk(1, "notes.pdf", 1, 0, null, "General", "vocabulary", "mixed", text)

    @Test
    fun questionPromptsBySize() = runBlocking {
        ActivityScenario.launch(FrontActivity::class.java).use {
            AppForeground.visible = true
            val nano = MlKitNano()
            val state = NanoState(nano).also { s -> s.refresh() }
            repeat(50) { if (state.status.value != NanoStatus.Available) delay(200) }
            assumeTrue(state.status.value == NanoStatus.Available)
            val client = Generation.getClient()
            val vocab = "커피 = coffee\n우유 = milk\n물 = water\n빵 = bread\n사과 = apple\n"
            val grammar = "-(으)ㄹ 만하다: 어떤 일을 할 가치가 있다는 뜻이에요. 이 영화는 볼 만해요. 그 식당은 가 볼 만해요. "
            val cases = listOf(
                "coach" to coachPrompt("You have 12 flashcards due today.\nYour weakest topic is food."),
                "q_vocab_100" to questionPrompt("multiple_choice", 1, chunk(vocab)),
                "q_vocab_450" to questionPrompt("multiple_choice", 1, chunk(vocab.repeat(6).take(450))),
                "q_grammar_1500" to questionPrompt("short_answer", 3, chunk(grammar.repeat(20).take(1500))),
            )
            val lines = mutableListOf("limit=${client.getTokenLimit()} system=${client.isSystemPromptAvailable()}")
            val gate = NanoGate(nano, state, { false }, System::currentTimeMillis, { delay(it) }, { true }, { 30_000 })
            for ((name, prompt) in cases) for ((mt, temp) in listOf(200 to 0f, 200 to null, 96 to 0f)) {
                val tokens = client.countTokens(generateContentRequest(SystemInstruction(SYSTEM_PROMPT), TextPart(prompt)) {}).totalTokens
                val t = System.currentTimeMillis()
                val out = gate.ask(AiRequest(AiUse.Questions, "probe", prompt, "$name$mt$temp", mt, temp)) { it }
                lines += "$name max=$mt temp=$temp tokens=$tokens chars=${prompt.length} result=${gate.lastResult} ms=${System.currentTimeMillis() - t} out_chars=${out?.length}"
                File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "probe.txt").writeText(lines.joinToString("\n") + "\n")
            }
        }
    }
}
