package com.dero.juststudykorean.data.ai

import com.dero.juststudykorean.ai.AiRequest
import com.dero.juststudykorean.ai.AiUse
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.quiz.QuizRequest
import com.dero.juststudykorean.korean.Candidate
import com.dero.juststudykorean.korean.PyRandom
import com.dero.juststudykorean.korean.ai.EXPLAIN_MISTAKE_VERSION
import com.dero.juststudykorean.korean.ai.JUDGE_VERSION
import com.dero.juststudykorean.korean.ai.QUESTIONS_VERSION
import com.dero.juststudykorean.korean.ai.hasHangul
import com.dero.juststudykorean.korean.ai.judgeApproves
import com.dero.juststudykorean.korean.ai.judgePrompt
import com.dero.juststudykorean.korean.ai.mistakeInput
import com.dero.juststudykorean.korean.ai.mistakePrompt
import com.dero.juststudykorean.korean.ai.parseQuestion
import com.dero.juststudykorean.korean.ai.questionPrompt
import com.dero.juststudykorean.korean.ai.validMistake
import com.dero.juststudykorean.korean.buildModelQuestion
import com.dero.juststudykorean.korean.extractGrammar
import com.dero.juststudykorean.korean.extractPairs
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Types the model writes (the web's free types). */
val AI_TYPES = setOf("multiple_choice", "short_answer")

/** The model writes at most this many questions per quiz (≈ 6 s each with the 2 s gap); the rules fill the rest. */
const val AI_MAX_QUESTIONS = 3
private const val MIN_HANGUL = 4 // the model only writes about chunks with Korean (the web's MIN_LLM_HANGUL)
private val HANGUL = Regex("[가-힣]")

/**
 * Phase 6, task 2: the quiz's model uses, all through [NanoGate]. Uses 1 and 3 (meaning judge), use 2 (explain your
 * mistake) and the model's questions. Nothing here is saved: the caller decides (only a judge's YES becomes "correct").
 */
@Singleton
class QuizAi @Inject constructor(private val gate: NanoGate, private val db: JustStudyDatabase, private val notes: NotesSource) {
    fun ready() = gate.ready()

    /** Uses 1 and 3: true only if the rules said wrong, the answer is English and the model says it means the same. */
    suspend fun sameMeaning(asked: String, correct: String, given: String): Boolean {
        if (hasHangul(given) || hasHangul(correct) || given.isBlank()) return false
        val request = AiRequest(AiUse.Judge, JUDGE_VERSION, judgePrompt(asked, correct, given), "$asked\n$correct\n$given", 32, 0.2f, cache = true)
        return gate.ask(request) { out -> true.takeIf { judgeApproves(out, given) } } == true
    }

    /** Use 2: one or two sentences from your grammar point ([point]) and the two answers; null → the point as it is. */
    suspend fun explainMistake(asked: String, correct: String, given: String, point: String?): String? {
        if (point.isNullOrBlank()) return null
        val input = mistakeInput(asked, correct, given, point)
        return gate.ask(AiRequest(AiUse.ExplainMistake, EXPLAIN_MISTAKE_VERSION, mistakePrompt(input), input, 128, cache = true)) { validMistake(it, input) }
    }

    /**
     * The model's multiple choice and short answer questions for [request] (at most [AI_MAX_QUESTIONS]), with the
     * difficulty [levels] in turns as in the web, checked like the web. Kept in memory only (the owner's rule).
     */
    suspend fun questions(request: QuizRequest, levels: List<Int>, seed: Long): List<Candidate> {
        val types = request.types.filter { it in AI_TYPES }
        if (types.isEmpty() || !gate.ready()) return emptyList()
        val share = (request.count * types.size + request.types.size - 1) / request.types.size
        val wanted = minOf(AI_MAX_QUESTIONS, share, request.count)
        val rng = PyRandom(seed)
        val (chunks, pairs, grammar) = withContext(Dispatchers.Default) {
            val ready = db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }
            val docs = if (request.documentIds.isEmpty()) ready else ready.filter { it.id in request.documentIds }
            val all = docs.flatMap(notes::chunks)
            Triple(
                all.filter { c -> HANGUL.findAll(c.text).count() >= MIN_HANGUL && (request.contentType == null || c.contentType == request.contentType) }.toMutableList().also(rng::shuffle),
                all.groupBy { it.documentId }.mapValues { (_, cs) -> cs.flatMap(::extractPairs) },
                all.groupBy { it.documentId }.mapValues { (_, cs) -> cs.flatMap(::extractGrammar) },
            )
        }
        val out = mutableListOf<Candidate>()
        val levelTurns = levels.ifEmpty { listOf(1) }
        var calls = 0
        for (chunk in chunks) {
            if (out.size >= wanted || calls >= wanted * 2 || !gate.ready()) break
            val type = types[calls % types.size]
            val level = levelTurns[calls % levelTurns.size]
            calls++
            val candidate = gate.ask(AiRequest(AiUse.Questions, QUESTIONS_VERSION, questionPrompt(type, level, chunk), chunk.text, 200, 0f)) { text ->
                val gq = parseQuestion(text, type) ?: return@ask null
                buildModelQuestion(gq, chunk, types, pairs[chunk.documentId].orEmpty(), grammar[chunk.documentId].orEmpty(), rng).candidate
            } ?: continue
            if (out.any { norm(it.prompt) == norm(candidate.prompt) || repeatsOptions(it, candidate) }) continue
            out += candidate.copy(promptVersion = QUESTIONS_VERSION, model = "gemini-nano")
        }
        return out
    }

    /** The same wrong options as another question (guessable by elimination): the web's _repeats_options. */
    private fun repeatsOptions(a: Candidate, b: Candidate): Boolean {
        fun wrong(c: Candidate) = c.options.orEmpty().map(::norm).filter { it != norm(c.answer) }.toSet()
        return wrong(b).size >= 2 && wrong(a) == wrong(b)
    }
}
