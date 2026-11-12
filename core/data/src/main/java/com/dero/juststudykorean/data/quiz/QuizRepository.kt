package com.dero.juststudykorean.data.quiz

import androidx.room3.withWriteTransaction
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.QuestionAttemptsEntity
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.db.QuizRunsEntity
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.PyRandom
import com.dero.juststudykorean.korean.isCorrect
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

// Port of backend/app/services/quiz_service.py, checked against golden/db (QuizGoldenTest, ReplayGoldenTest).

/** What to build a quiz from. [documentIds] empty: all. [words]: only questions about these words (rules). */
data class QuizRequest(
    val documentIds: List<Long> = emptyList(),
    val topic: String? = null,
    val contentType: String? = null,
    val count: Int = 5,
    val types: List<String> = listOf("multiple_choice", "ko_to_en", "en_to_ko", "vocab_recognition", "write_ko"),
    val words: List<String> = emptyList(),
    val seed: Long? = null,
)

data class QuizRun(val id: Long, val questions: List<QuestionsEntity>)

/** One answered question of a quiz: what you answered (the last time) and whether it was right. */
data class ResultRow(val question: QuestionsEntity, val given: String, val correct: Boolean)

/** [aiAccepted]: the rules said wrong and the meaning judge (the model) said it means the same (design/quiz.md A.2). */
data class Grade(val correct: Boolean, val correctAnswer: String, val explanation: String?, val aiAccepted: Boolean = false)

/** A question you got wrong and have not got right since; [lastAt] in epoch millis. */
data class Mistake(val question: QuestionsEntity, val lastAt: Long, val times: Int)

data class HistoryItem(val questionId: Long, val prompt: String, val result: String)

/** A quiz of the last 15 days (or, before quizzes were saved, a quiz session); [startedAt] in epoch millis. */
data class HistoryCard(val key: String, val title: String, val startedAt: Long, val items: List<HistoryItem>)

class QuizException(message: String) : Exception(message)

private val COOLDOWN = Duration.ofDays(7)
private val HISTORY_DAYS = Duration.ofDays(15)

@Singleton
class QuizRepository @Inject constructor(
    private val db: JustStudyDatabase,
    private val clock: AppClock,
    private val progress: ProgressRepository,
    private val notes: NotesSource,
) {
    /**
     * Questions the model wrote (phase 6): only in memory, never in `questions` or `question_attempts` (the owner's rule:
     * the model's output goes to no backup table). Negative ids; lost when the app's process ends.
     */
    private val aiRuns = java.util.concurrent.ConcurrentHashMap<Long, List<QuestionsEntity>>()
    private val aiAnswers = java.util.concurrent.ConcurrentHashMap<Long, Pair<String, Boolean>>()
    private val aiIds = java.util.concurrent.atomic.AtomicLong(0)

    /** True for a question the model wrote (kept in memory only). */
    fun isAiQuestion(question: QuestionsEntity) = question.id < 0

    /** What the rules say about [given] (no write). */
    fun check(question: QuestionsEntity, given: String) = isCorrect(question.type, given, question.correctAnswer)

    private fun ids(json: String?): List<Long> = json?.let { Json.parseToJsonElement(it).jsonArray.map { e -> e.jsonPrimitive.long } }.orEmpty()

    private suspend fun lastAttempts(): Map<Long, QuestionAttemptsEntity> =
        db.questionAttemptsDao().all().groupBy { it.questionId }.mapValues { (_, a) -> a.maxBy { it.id } }

    // ---------- reads ----------

    /** The questions you got right in the last 7 days (their last attempt was right). */
    internal suspend fun savedQuestions(limit: Int?, rng: PyRandom? = null): List<QuestionsEntity> {
        val since = clock.now().toEpochMilli() - COOLDOWN.toMillis()
        val questions = db.questionsDao().all().associateBy { it.id }
        val last = lastAttempts().values.filter { it.isCorrect && it.createdAt > since && it.questionId in questions }
        val ordered = if (rng != null) last.toMutableList().also(rng::shuffle) else last.sortedWith(compareByDescending<QuestionAttemptsEntity> { it.createdAt }.thenByDescending { it.questionId })
        return ordered.map { questions.getValue(it.questionId) }.let { if (limit != null) it.take(limit) else it }
    }

    /** Questions whose last attempt was wrong ("Quiz for your weaknesses"). */
    private suspend fun wrongQuestions(): List<QuestionsEntity> {
        val questions = db.questionsDao().all().associateBy { it.id }
        return lastAttempts().values.filter { !it.isCorrect }.mapNotNull { questions[it.questionId] }
    }

    fun mistakes(limit: Int = 5): Flow<List<Mistake>> = db.invalidationTracker.createFlow("question_attempts", "questions").map { mistakesNow(limit) }

    suspend fun mistakesNow(limit: Int): List<Mistake> = withContext(Dispatchers.IO) {
        val questions = db.questionsDao().all().associateBy { it.id }
        val attempts = db.questionAttemptsDao().all()
        val wrongTimes = attempts.filter { !it.isCorrect }.groupingBy { it.questionId }.eachCount()
        lastAttempts().values.filter { !it.isCorrect && it.questionId in questions && it.questionId in wrongTimes }
            .sortedWith(compareByDescending<QuestionAttemptsEntity> { it.createdAt }.thenByDescending { it.id }).take(limit)
            .map { Mistake(questions.getValue(it.questionId), it.createdAt, wrongTimes.getValue(it.questionId)) }
    }

    fun history(): Flow<List<HistoryCard>> = db.invalidationTracker.createFlow("quiz_runs", "question_attempts").map { historyNow(20) }

    /** Your quizzes of the last 15 days, newest first; older ones are deleted here (your answers stay). */
    suspend fun historyNow(limit: Int): List<HistoryCard> = withContext(Dispatchers.IO) {
        val cutoff = clock.now().toEpochMilli() - HISTORY_DAYS.toMillis()
        pruneHistory(cutoff)
        val attempts = db.questionAttemptsDao().all()
        val questions = db.questionsDao().all().associateBy { it.id }
        val cards = mutableListOf<HistoryCard>()
        val answered = attempts.mapNotNull { it.quizRunId }.toSet()
        for (run in db.quizRunsDao().all().filter { it.id in answered && it.startedAt >= cutoff }
            .sortedWith(compareByDescending<QuizRunsEntity> { it.startedAt }.thenByDescending { it.id }).take(limit)) {
            val last = attempts.filter { it.quizRunId == run.id }.sortedBy { it.id }.associate { it.questionId to it.isCorrect }
            val qs = ids(run.questionIds).mapNotNull { questions[it] } // deleted ones (document deleted) are left out
            cards += HistoryCard("run-${run.id}", title(qs), run.startedAt, qs.map { q -> HistoryItem(q.id, q.prompt, last[q.id]?.let { if (it) "correct" else "wrong" } ?: "unanswered") })
        }
        val legacy = attempts.filter { it.quizRunId == null && it.sessionId != null }.groupBy { it.sessionId!! }
        for (session in db.studySessionsDao().all().filter { it.mode == "quiz" && it.id in legacy && it.startedAt >= cutoff }
            .sortedWith(compareByDescending<com.dero.juststudykorean.data.db.StudySessionsEntity> { it.startedAt }.thenByDescending { it.id }).take(limit)) {
            val last = LinkedHashMap<Long, Boolean>()
            for (a in legacy.getValue(session.id).sortedBy { it.id }) last[a.questionId] = a.isCorrect // order of the first time, result of the last
            val qs = last.keys.mapNotNull { questions[it] }
            if (qs.isEmpty()) continue
            cards += HistoryCard("session-${session.id}", title(qs), session.startedAt, qs.map { HistoryItem(it.id, it.prompt, if (last.getValue(it.id)) "correct" else "wrong") })
        }
        cards.sortedByDescending { it.startedAt }.take(limit)
    }

    private suspend fun pruneHistory(cutoff: Long) {
        val old = db.quizRunsDao().all().filter { it.startedAt < cutoff }
        if (old.isEmpty()) return
        val ids = old.map { it.id }.toSet()
        db.withWriteTransaction {
            db.questionAttemptsDao().all().filter { it.quizRunId in ids }.forEach { db.questionAttemptsDao().update(it.copy(quizRunId = null)) }
            old.forEach { db.quizRunsDao().delete(it) }
        }
    }

    /** Where the questions came from: the document's name if all are from one; "N documents" if several. */
    private suspend fun title(questions: List<QuestionsEntity>): String {
        val docs = db.documentsDao().all().associateBy { it.id }
        val names = LinkedHashMap<Long, String>()
        for (q in questions) {
            val source = Json.parseToJsonElement(q.sourceChunkIds ?: "[]").jsonArray.map { it.jsonPrimitive.content }
                .firstNotNullOfOrNull { id -> id.split(":").takeIf { it.size == 3 }?.let { it[0].toLongOrNull()?.takeIf { _ -> it[1].toIntOrNull() != null } } }
                ?: continue
            names.putIfAbsent(source, docs[source]?.originalName?.substringBeforeLast(".") ?: "Deleted document #$source")
        }
        return when {
            names.size == 1 -> names.values.first()
            names.isNotEmpty() -> "${names.size} documents"
            else -> "Quiz"
        }
    }

    /** A saved quiz and its questions, in order (deleted questions are left out). */
    suspend fun run(runId: Long): QuizRun? = withContext(Dispatchers.IO) {
        val run = db.hotDao().quizRun(runId) ?: return@withContext null
        val wanted = ids(run.questionIds)
        val questions = db.hotDao().questions(wanted).associateBy { it.id }
        QuizRun(run.id, mixed(wanted.mapNotNull { questions[it] }, aiRuns[run.id].orEmpty()))
    }

    /** The answers of a history card ("run-{id}" or "session-{id}"): the last answer to each question, in order. */
    suspend fun results(key: String): List<ResultRow> = withContext(Dispatchers.IO) {
        val (kind, idText) = key.split("-", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val id = idText.toLongOrNull() ?: return@withContext emptyList()
        val attempts = db.questionAttemptsDao().all().filter { if (kind == "run") it.quizRunId == id else it.quizRunId == null && it.sessionId == id }.sortedBy { it.id }
        val questions = db.questionsDao().all().associateBy { it.id }
        val last = LinkedHashMap<Long, com.dero.juststudykorean.data.db.QuestionAttemptsEntity>()
        attempts.forEach { last[it.questionId] = it }
        val order = if (kind == "run") db.quizRunsDao().all().firstOrNull { it.id == id }?.let { ids(it.questionIds) }.orEmpty() else last.keys.toList()
        val saved = order.mapNotNull { q -> last[q]?.let { a -> questions[q]?.let { ResultRow(it, a.userAnswer, a.isCorrect) } } }
        val ai = if (kind == "run") aiRuns[id].orEmpty().mapNotNull { q -> aiAnswers[q.id]?.let { (given, ok) -> ResultRow(q, given, ok) } } else emptyList()
        mixed(saved, ai)
    }

    /** The model's questions after the rules' ones, in the order they arrived (design/quiz.md A.3). */
    private fun <T> mixed(rules: List<T>, ai: List<T>): List<T> = rules + ai

    private val aiFailed = java.util.concurrent.ConcurrentHashMap<Long, Int>()

    /** A.3: how many of the model's questions of a quiz couldn't be written (the results say so). */
    fun aiFailed(runId: Long) = aiFailed[runId] ?: 0
    fun setAiFailed(runId: Long, n: Int) { if (n > 0) aiFailed[runId] = n }

    /** A.3: a question the model just wrote, added at the end of an open quiz (in memory only). */
    fun addAi(runId: Long, c: com.dero.juststudykorean.korean.Candidate): QuestionsEntity {
        val q = aiEntity(c, clock.now().toEpochMilli())
        aiRuns.merge(runId, listOf(q)) { a, b -> a + b }
        return q
    }

    private fun aiEntity(c: com.dero.juststudykorean.korean.Candidate, now: Long) = QuestionsEntity(
        id = -aiIds.incrementAndGet(), type = c.type, prompt = c.prompt,
        optionsJson = c.options?.let { BackupFormat.pyJson(JsonArray(it.map(::JsonPrimitive))) }, correctAnswer = c.answer,
        explanation = c.explanation.ifEmpty { null }, difficulty = 1, topicId = null,
        sourceChunkIds = BackupFormat.pyJson(JsonArray(listOf(JsonPrimitive(c.chunk.chromaId)))),
        promptVersion = c.promptVersion, model = c.model, createdAt = now,
    )

    /** The document each question comes from (its file name), for the question card and the mistakes. */
    suspend fun documentNames(questions: List<QuestionsEntity>): Map<Long, String> = withContext(Dispatchers.IO) {
        val docs = db.documentsDao().all().associate { it.id to it.originalName }
        questions.mapNotNull { q ->
            Json.parseToJsonElement(q.sourceChunkIds ?: "[]").jsonArray.firstNotNullOfOrNull { it.jsonPrimitive.content.split(":")[0].toLongOrNull() }
                ?.let { docs[it] }?.let { q.id to it }
        }.toMap()
    }

    /** Your processed documents (the Documents selector). */
    suspend fun readyDocuments() = withContext(Dispatchers.IO) { db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id } }

    /** Fills the chunk cache of [docs] so the first quiz doesn't pay for reading and chunking the notes. */
    suspend fun warmUp(docs: List<DocumentsEntity>) = withContext(Dispatchers.Default) { docs.forEach(notes::chunks) }

    // ---------- writes ----------

    /** Saves a quiz when it starts: its questions, in order, and the time. */
    suspend fun createRun(questionIds: List<Long>): QuizRun = db.withWriteTransaction {
        val ids = questionIds.distinct()
        val found = db.questionsDao().all().associateBy { it.id }
        if (ids.any { it !in found }) throw QuizException("Some of these questions don't exist.")
        val run = QuizRunsEntity(progress.nextId("quiz_runs"), BackupFormat.pyJson(JsonArray(ids.map { JsonPrimitive(it) })), clock.now().toEpochMilli())
        db.quizRunsDao().insert(run)
        QuizRun(run.id, ids.map(found::getValue))
    }

    /** New questions (or reused ones) for [request], built by rules from your notes; null if there is no material. */
    internal suspend fun generateQuestions(request: QuizRequest, rng: PyRandom): List<QuestionsEntity>? = db.withWriteTransaction {
        val ready = db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }
        val docs = if (request.documentIds.isEmpty()) ready else request.documentIds.map { id -> ready.firstOrNull { it.id == id } ?: throw QuizException("Document not found.") }
        val all = docs.flatMap(notes::chunks)
        val targets = request.topic?.trim()?.lowercase()?.let { wanted -> all.filter { (if (it.topic != "General") it.topic else com.dero.juststudykorean.korean.cleanTopic(it.filename.substringBeforeLast("."))).lowercase() == wanted } } ?: all
        val chunks = pickChunks(targets, rng, request.contentType)
        if (chunks.isEmpty()) return@withWriteTransaction null
        val existing = db.questionsDao().all()
        val byKey = existing.associateBy { Triple(norm(it.prompt), norm(it.correctAnswer), it.type) }
        val resting = (savedQuestions(null) + wrongQuestions()).map { norm(it.prompt) to norm(it.correctAnswer) }.toMutableSet()
        var types = request.types
        var keep: ((com.dero.juststudykorean.korean.VocabPair) -> Boolean)? = null
        if (request.words.isNotEmpty()) {
            types = types.filter { it in RULE_TYPES }.ifEmpty { RULE_TYPES.sorted() }
            val wanted = request.words.map(::norm).toSet()
            keep = { norm(it.korean) in wanted }
        }
        val run = RuleRun(chunks, all, request.count, types, keep)
        run.go(resting, rng)
        val topics = Topics(db, progress)
        val now = clock.now().toEpochMilli()
        run.candidates.map { c ->
            byKey[Triple(norm(c.prompt), norm(c.answer), c.type)] ?: QuestionsEntity(
                id = progress.nextId("questions"), type = c.type, prompt = c.prompt,
                optionsJson = c.options?.let { BackupFormat.pyJson(JsonArray(it.map(::JsonPrimitive))) }, correctAnswer = c.answer,
                explanation = c.explanation.ifEmpty { null }, difficulty = 1, topicId = topics.idFor(c.chunk),
                sourceChunkIds = BackupFormat.pyJson(JsonArray(listOf(JsonPrimitive(c.chunk.chromaId)))),
                promptVersion = c.promptVersion.ifEmpty { "quiz.v1" }, model = c.model.ifEmpty { "rules" }.take(64), createdAt = now,
            ).also { db.questionsDao().insert(it) }
        }
    }

    /**
     * A quiz by rules from your notes; null if there is no material for these settings. [ai]: questions the model wrote
     * (already checked), kept in memory with the quiz; the rules make the rest.
     */
    suspend fun generate(request: QuizRequest, ai: List<com.dero.juststudykorean.korean.Candidate> = emptyList(), pendingAi: Int = 0): QuizRun? {
        val rules = request.copy(count = request.count - ai.size - pendingAi)
        val questions = if (rules.count > 0) generateQuestions(rules, PyRandom(request.seed ?: clock.now().toEpochMilli())).orEmpty() else emptyList()
        if (questions.isEmpty() && ai.isEmpty() && pendingAi == 0) return null
        val run = createRun(questions.map { it.id })
        if (ai.isEmpty()) return run
        val now = clock.now().toEpochMilli()
        val mine = ai.map { aiEntity(it, now) }
        aiRuns[run.id] = mine
        return QuizRun(run.id, mixed(run.questions, mine))
    }

    /** [accepted]: the meaning judge approved an English answer the rules rejected; only this true/false is saved. */
    suspend fun answer(runId: Long?, questionId: Long, given: String, elapsedMs: Long, accepted: Boolean = false): Grade = withContext(Dispatchers.IO) {
        if (questionId < 0) {
            val q = runId?.let { aiRuns[it] }?.firstOrNull { it.id == questionId } ?: throw QuizException("Question not found.")
            val ok = check(q, given) || accepted
            aiAnswers[questionId] = given to ok
            return@withContext Grade(ok, q.correctAnswer, q.explanation, aiAccepted = accepted && !check(q, given))
        }
        db.withWriteTransaction { answerNow(runId, questionId, given, elapsedMs, accepted) }
    }

    private suspend fun answerNow(runId: Long?, questionId: Long, given: String, elapsedMs: Long, accepted: Boolean): Grade {
        val question = db.hotDao().question(questionId) ?: throw QuizException("Question not found.")
        if (runId != null) {
            val run = db.hotDao().quizRun(runId) ?: throw QuizException("Quiz not found.")
            if (question.id !in ids(run.questionIds)) throw QuizException("That question is not part of this quiz.")
        }
        if (question.type in CHOICE_TYPES && norm(given) !in Json.parseToJsonElement(question.optionsJson ?: "[]").jsonArray.map { norm(it.jsonPrimitive.content) }) {
            throw QuizException("That answer is not one of the options.")
        }
        val byRules = isCorrect(question.type, given, question.correctAnswer)
        val correct = byRules || accepted
        val now = clock.now().toEpochMilli()
        val session = progress.attachSession(now, elapsedMs, question.topicId, mode = "quiz")
        db.questionAttemptsDao().insert(QuestionAttemptsEntity(progress.nextId("question_attempts"), question.id, session.id, runId, given, correct, elapsedMs, now))
        question.topicId?.let { progress.recomputeTopic(it) }
        return Grade(correct, question.correctAnswer, question.explanation, aiAccepted = accepted && !byRules)
    }

    /** "Practice saved questions": the ones you got right in the last 7 days, in random order. */
    suspend fun practiceSaved(): QuizRun? =
        savedQuestions(10, PyRandom(clock.now().toEpochMilli())).takeIf { it.isNotEmpty() }?.let { q -> createRun(q.map { it.id }) }

    /** "Fix these weaknesses": the same questions you got wrong and have not fixed yet. */
    suspend fun fixWeaknesses(): QuizRun? = mistakesNow(10).takeIf { it.isNotEmpty() }?.let { m -> createRun(m.map { it.question.id }) }
}

/** The options of a choice question (options_json), in order. */
fun QuestionsEntity.options(): List<String> = Json.parseToJsonElement(optionsJson ?: "[]").jsonArray.map { it.jsonPrimitive.content }
