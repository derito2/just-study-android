package com.dero.juststudykorean.ui.quiz

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.prefs.QuizForm
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.data.quiz.Grade
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.quiz.QuizRequest
import com.dero.juststudykorean.data.quiz.QuizRun
import com.dero.juststudykorean.data.quiz.ResultRow
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.ui.components.HistoryLine
import com.dero.juststudykorean.ui.components.MistakeLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.data.ai.QuizAi
import com.dero.juststudykorean.ai.NanoStatus
import javax.inject.Inject
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import kotlinx.coroutines.CancellationException

/** Each question type: name, Korean and hint (the web's TYPE_LABELS), in the form's order. */
val QUIZ_TYPES = linkedMapOf(
    "ko_to_en" to Triple("Korean → English", "한국어 → 영어", "Type the meaning"),
    "write_ko" to Triple("Write in Korean", "한국어로 쓰기", "Type the Korean"),
    "vocab_recognition" to Triple("Vocabulary recognition", "어휘 알아보기", "Pick the meaning"),
    "en_to_ko" to Triple("English → Korean", "영어 → 한국어", "Pick the Korean word"),
    "fill_blank" to Triple("Fill in the blank", "빈칸 채우기", "Pick what goes in the gap"),
    "multiple_choice" to Triple("Multiple choice", "객관식", "Pick one answer"),
    "grammar_selection" to Triple("Grammar selection", "문법 고르기", "Pick the correct form"),
    "short_answer" to Triple("Short answer", "단답형", "Type a short answer"),
)

/**
 * Types only the model can write. Without Nano the form shows them as unavailable and they never count
 * (design/quiz.md A.1); the saved preference keeps them. Fill in the blank is made by the rules (R-017, A.2).
 */
val MODEL_TYPES = setOf("short_answer")

/** The types of [this] form a quiz can be built from now ([ai]: Gemini Nano is Available). */
fun QuizForm.usableTypes(ai: Boolean = false): List<String> = QUIZ_TYPES.keys.filter { it in types && (ai || it !in MODEL_TYPES) }

/** Difficulty only counts with a type the model writes (multiple choice or short answer) and Nano Available. */
fun QuizForm.levelsCount(ai: Boolean) = ai && types.any { it == "multiple_choice" || it == "short_answer" }

/** Types the model writes: only these change with the difficulty (the web's LEVEL_TYPES). */
val LEVEL_TYPES = setOf("fill_blank", "multiple_choice", "short_answer")
val LEVELS = listOf(Triple(1, "1 · Recall", "기억하기"), Triple(2, "2 · Similar options", "비슷한 선택지"), Triple(3, "3 · Apply a rule", "규칙 적용"))

/** How a question is answered (the web's answer_mode): options, typed English or typed Korean. */
fun answerMode(type: String) = when (type) {
    in com.dero.juststudykorean.data.quiz.CHOICE_TYPES -> "choice"
    "write_ko" -> "korean"
    else -> "text"
}

/** "Today, 8:10 PM", "Yesterday, 9:32 PM" or "Nov 6, 7:45 PM", in the phone's zone. */
fun whenText(millis: Long, now: Instant, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(millis).atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(t)
    return when (t.toLocalDate()) {
        today -> "Today, $time"
        today.minusDays(1) -> "Yesterday, $time"
        else -> DateTimeFormatter.ofPattern("MMM d", Locale.US).format(t) + ", $time"
    }
}

sealed interface QuizEvent {
    data class Open(val runId: Long) : QuizEvent
    data class Message(val text: String) : QuizEvent
}

/** Quiz home (design/quiz.md A): the form (kept in DataStore), your weaknesses and the history. */
@HiltViewModel
class QuizHomeViewModel @Inject constructor(private val quiz: QuizRepository, private val prefs: UserPrefs, private val clock: AppClock, nano: NanoState? = null) : ViewModel() {
    /** Gemini Nano is Available: Short answer and Difficulty can be used (design/quiz.md A.2). */
    val ai: StateFlow<Boolean> = (nano?.status ?: MutableStateFlow(NanoStatus.Unavailable)).map { it == NanoStatus.Available }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val form: StateFlow<QuizForm?> = prefs.quizForm.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _documents = MutableStateFlow<List<DocumentsEntity>>(emptyList())
    val documents = _documents.asStateFlow()
    private val events = Channel<QuizEvent>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val mistakes: StateFlow<List<MistakeLine>?> = quiz.mistakes(5).map { list ->
        val names = quiz.documentNames(list.map { it.question })
        list.map { m ->
            val type = QUIZ_TYPES[m.question.type]?.first ?: m.question.type
            MistakeLine(names[m.question.id] ?: "Unknown document", "${whenText(m.lastAt, clock.now(), clock.zone)} · $type" + if (m.times > 1) " · wrong ${m.times}×" else "")
        }
    }.catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<HistoryLine>> = quiz.history().map { cards ->
        cards.map { h -> HistoryLine(h.key, h.items.count { it.result == "correct" }, h.items.size, h.title, whenText(h.startedAt, clock.now(), clock.zone), h.leftEarly) }
    }.catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _documents.value = runCatchingCancellable { quiz.readyDocuments() }.getOrDefault(emptyList())
            runCatchingCancellable { quiz.warmUp(_documents.value) }
        }
    }

    fun setForm(f: QuizForm) = viewModelScope.launch { prefs.setQuizForm(f) }

    fun practiceSaved() = viewModelScope.launch {
        val run = runCatchingCancellable { quiz.practiceSaved() }.getOrNull()
        events.send(if (run != null) QuizEvent.Open(run.id) else QuizEvent.Message("You have no saved questions right now."))
    }

    fun fixWeaknesses() = viewModelScope.launch {
        runCatchingCancellable { quiz.fixWeaknesses() }.getOrNull()?.let { events.send(QuizEvent.Open(it.id)) }
    }
}

/**
 * What you answered to a question, and its grade. Wrong, with Nano Available: [explanation] is the model's (AiMark) or
 * your grammar point as it is ([explaining] while the model writes; design/quiz.md A.2).
 */
data class Answered(val given: String, val grade: Grade, val explanation: String? = null, val aiExplanation: Boolean = false, val explaining: Boolean = false)

data class QuestionUi(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val run: QuizRun? = null,
    val index: Int = 0,
    val answers: Map<Long, Answered> = emptyMap(),
    val typed: String = "",
    val documents: Map<Long, String> = emptyMap(),
    val finished: Boolean = false,
    /** The last answer could not be saved. */
    val error: String? = null,
    /** A.3: questions asked of the model that haven't arrived yet (the counter already counts them). */
    val pendingAi: Int = 0,
) {
    val question: QuestionsEntity? get() = run?.questions?.getOrNull(index)
    val correct: Int get() = answers.values.count { it.grade.correct }
}

/** A quiz (design/quiz.md B and C): created from the saved form when [open] gets no run; each answer is saved at once. */
@HiltViewModel
class QuestionViewModel @Inject constructor(
    private val quiz: QuizRepository,
    private val prefs: UserPrefs,
    private val ai: QuizAi? = null,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(QuestionUi())
    val state = _state.asStateFlow()
    private var opened = false
    private var busy = false
    private var shownAt = 0L

    /** Milliseconds the last generation took (the 300 ms goal), for tests and logcat. */
    var lastGenerationMs: Long = -1
        private set

    fun open(route: Long?) {
        if (opened) return
        opened = true
        // R-008: the quiz this screen created is kept in its saved state (not in a new route: that would clear this
        // ViewModel and cancel the model's questions, phase 7), so a killed process opens it again, not a new one.
        val runId = route ?: saved.get<Long>(RUN_ID)
        viewModelScope.launch {
            try {
                val run = if (runId != null) quiz.run(runId) else generate()?.also { saved[RUN_ID] = it.id }
                val pending = if (runId == null) planned else 0
                if (run == null || (run.questions.isEmpty() && pending == 0)) { _state.value = QuestionUi(loading = false, failed = true); return@launch }
                if (run.questions.isEmpty()) { // A.3: all from the model: state B until the first one
                    _state.value = QuestionUi(loading = true, run = run, pendingAi = pending)
                    return@launch
                }
                // A quiz opened again (the process was killed half way) goes on where it was: the saved answers and the
                // first question without one. Text half typed is not kept.
                val answers = if (runId == null) emptyMap() else quiz.results("run-${run.id}").associate { r ->
                    r.question.id to Answered(r.given, Grade(r.correct, r.question.correctAnswer, r.question.explanation))
                }
                val next = run.questions.indexOfFirst { it.id !in answers }
                _state.value = QuestionUi(
                    loading = false, run = run, documents = quiz.documentNames(run.questions), answers = answers,
                    index = if (next < 0) run.questions.lastIndex else next, finished = next < 0, pendingAi = pending,
                )
                shownAt = System.nanoTime()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = QuestionUi(loading = false, failed = true)
            }
        }
    }

    private companion object { const val RUN_ID = "runId" }

    /** A.3: how many questions the model was asked for when this quiz was created. */
    private var planned = 0

    /** A.3: the model writes while you answer; each question goes at the end; the ones it couldn't write drop out. */
    private fun write(request: QuizRequest, levels: List<Int>, run: QuizRun) = viewModelScope.launch {
        var got = 0
        runCatchingCancellable {
            ai!!.questions(request, levels, System.currentTimeMillis()) { c ->
                val q = quiz.addAi(run.id, c)
                got++
                val s = _state.value
                val r = s.run ?: run
                _state.value = s.copy(run = r.copy(questions = r.questions + q), pendingAi = (planned - got).coerceAtLeast(0), loading = false)
                if (s.loading || s.question == null) shownAt = System.nanoTime()
            }
        }.onFailure { Log.w("JustStudy", "ai questions failed error=${it.javaClass.name}") } // the class only, never text
        quiz.setAiFailed(run.id, planned - got)
        val s = _state.value
        val size = s.run?.questions?.size ?: 0
        _state.value = when {
            size == 0 -> s.copy(pendingAi = 0, loading = false, failed = true)
            s.index >= size -> s.copy(pendingAi = 0, index = size - 1, finished = true) // you were waiting at the end
            else -> s.copy(pendingAi = 0)
        }
    }

    private suspend fun generate(): QuizRun? {
        val f = prefs.quizForm.first()
        val nano = ai?.ready() == true
        val types = f.usableTypes(nano)
        if (types.isEmpty()) return null // only model types saved: the form does not let you get here
        val start = System.nanoTime()
        val request = QuizRequest(documentIds = f.documentIds.toList(), count = f.count, types = types)
        // The model writes some multiple choice / short answer questions (kept in memory only); the rules, the rest.
        planned = if (nano) ai!!.planned(request) else 0
        val run = quiz.generate(request, pendingAi = planned)
        if (run != null && planned > 0) write(request, f.levels.sorted(), run)
        lastGenerationMs = (System.nanoTime() - start) / 1_000_000
        Log.i("JustStudy", "quiz generated: ${run?.questions?.size ?: 0} of ${f.count} questions in $lastGenerationMs ms") // counts only, never content
        return run
    }

    fun type(text: String) { _state.value = _state.value.copy(typed = text) }

    /** Sends [given] for the current question (once). */
    fun answer(given: String) {
        val s = _state.value
        val q = s.question ?: return
        if (busy || q.id in s.answers || given.isBlank()) return
        busy = true
        viewModelScope.launch {
            try {
                val elapsed = (System.nanoTime() - shownAt) / 1_000_000
                val text = given.trim()
                // Uses 1: only a typed English answer the rules rejected goes to the meaning judge; it can only approve.
                val accepted = ai != null && answerMode(q.type) == "text" && !quiz.check(q, text) && ai.sameMeaning(q.prompt, q.correctAnswer, text)
                val grade = quiz.answer(s.run!!.id, q.id, text, elapsed, accepted)
                val explain = !grade.correct && ai?.ready() == true
                _state.value = _state.value.copy(answers = _state.value.answers + (q.id to Answered(text, grade, explaining = explain)), error = null)
                if (explain) explain(q, text, grade)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // never logs the answer
                _state.value = _state.value.copy(error = "Couldn't save your answer. Try again.")
            } finally {
                busy = false
            }
        }
    }

    /** Use 2: the model explains the mistake; if it can't (or its answer is rejected), your grammar point as it is. */
    private fun explain(q: QuestionsEntity, given: String, grade: Grade) = viewModelScope.launch {
        val written = runCatchingCancellable { ai!!.explainMistake(q.prompt, q.correctAnswer, given, q.explanation) }.getOrNull()
        val a = _state.value.answers[q.id] ?: return@launch
        _state.value = _state.value.copy(answers = _state.value.answers + (q.id to a.copy(explanation = written ?: grade.explanation, aiExplanation = written != null, explaining = false)))
    }

    fun next() {
        val s = _state.value
        val total = s.run?.questions?.size ?: return
        // A.3: at the end with a model question still coming: wait for it ("Writing the next question…").
        if (s.index + 1 >= total && s.pendingAi > 0) _state.value = s.copy(index = s.index + 1, typed = "")
        else if (s.index + 1 >= total) _state.value = s.copy(finished = true)
        else { _state.value = s.copy(index = s.index + 1, typed = ""); shownAt = System.nanoTime() }
    }
}

/** Results of a quiz or a history card ("run-{id}" / "session-{id}"). */
@HiltViewModel
class ResultsViewModel @Inject constructor(private val quiz: QuizRepository) : ViewModel() {
    private val _rows = MutableStateFlow<List<ResultRow>?>(null)
    val rows = _rows.asStateFlow()
    private val _aiFailed = MutableStateFlow(0)
    /** A.3: "{k} AI questions couldn't be written this time." */
    val aiFailed = _aiFailed.asStateFlow()
    private var opened = false

    fun open(key: String) {
        if (opened) return
        opened = true
        viewModelScope.launch {
            _rows.value = runCatchingCancellable { quiz.results(key) }.getOrDefault(emptyList())
            _aiFailed.value = key.removePrefix("run-").toLongOrNull()?.takeIf { key.startsWith("run-") }?.let(quiz::aiFailed) ?: 0
        }
    }
}
