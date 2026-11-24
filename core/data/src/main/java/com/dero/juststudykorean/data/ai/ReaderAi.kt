package com.dero.juststudykorean.data.ai

import com.dero.juststudykorean.ai.AiRequest
import com.dero.juststudykorean.ai.AiUse
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.korean.ai.SENTENCE_EXPLAIN_VERSION
import com.dero.juststudykorean.korean.ai.sentenceExplainPrompt
import com.dero.juststudykorean.korean.ai.validSentenceExplain
import javax.inject.Inject
import javax.inject.Singleton

/** "Explain this sentence" (AiUse.SentenceExplain): English only, Korean only from the sentence; null → the glosses are shown. */
@Singleton
class ReaderAi @Inject constructor(private val gate: NanoGate) {
    fun ready() = gate.ready()

    suspend fun explain(sentence: String, translation: String?, glosses: List<String>): String? =
        gate.ask(AiRequest(AiUse.SentenceExplain, SENTENCE_EXPLAIN_VERSION, sentenceExplainPrompt(sentence, translation, glosses), sentence, 200, cache = true)) {
            validSentenceExplain(it, sentence)
        }
}
