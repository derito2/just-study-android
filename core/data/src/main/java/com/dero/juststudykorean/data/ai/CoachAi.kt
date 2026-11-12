package com.dero.juststudykorean.data.ai

import com.dero.juststudykorean.ai.AiRequest
import com.dero.juststudykorean.ai.AiUse
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.korean.ai.COACH_AI_VERSION
import com.dero.juststudykorean.korean.ai.coachPrompt
import com.dero.juststudykorean.korean.ai.validFromFacts
import com.dero.juststudykorean.korean.coach.CoachAnswer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6, task 4 (use 5): the model writes the coach's text from what [com.dero.juststudykorean.data.coach.CoachEngine]
 * computed (its text and the summaries of its steps). Answers that quote your notes (a word's entry) stay the rules'.
 * The proposals don't change. If the model is not there, is late or adds anything, the rules' text.
 */
@Singleton
class CoachAi @Inject constructor(private val gate: NanoGate, private val db: JustStudyDatabase) {
    suspend fun write(answer: CoachAnswer): CoachAnswer {
        if (!gate.ready() || answer.steps.firstOrNull()?.tool == "search_knowledge_base") return answer
        val facts = (listOf(answer.text) + answer.steps.mapNotNull { it.summary.takeIf(String::isNotBlank) }).joinToString("\n")
        val topics = db.topicsDao().all().map { it.name }
        val text = gate.ask(AiRequest(AiUse.Coach, COACH_AI_VERSION, coachPrompt(facts), facts, 160, cache = true)) { validFromFacts(it, facts, topics) }
            ?: return answer
        return answer.copy(text = text, ai = true)
    }
}
