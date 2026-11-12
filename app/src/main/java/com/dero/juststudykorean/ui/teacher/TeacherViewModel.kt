package com.dero.juststudykorean.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.coach.CoachEngine
import com.dero.juststudykorean.data.flashcards.FlashcardException
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.quiz.QuizRequest
import com.dero.juststudykorean.data.teacher.QUESTION_MAX
import com.dero.juststudykorean.data.teacher.QUESTION_MIN
import com.dero.juststudykorean.data.teacher.TeacherService
import com.dero.juststudykorean.korean.coach.CoachAnswer
import com.dero.juststudykorean.korean.coach.FORCED
import com.dero.juststudykorean.korean.coach.GRAMMAR_QUIZ
import com.dero.juststudykorean.korean.coach.QuizProposal
import com.dero.juststudykorean.korean.coach.TIME_BUDGET
import com.dero.juststudykorean.korean.coach.intent
import com.dero.juststudykorean.korean.coach.plannedTools
import com.dero.juststudykorean.korean.teacher.TeacherAnswer
import com.dero.juststudykorean.korean.teacher.askedPhrase
import com.dero.juststudykorean.korean.teacher.askedWord
import com.dero.juststudykorean.korean.teacher.asksSentence
import com.dero.juststudykorean.korean.teacher.checkedSentence
import com.dero.juststudykorean.korean.teacher.comparedWords
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import com.dero.juststudykorean.ui.quiz.MODEL_TYPES
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ask my notes (Teacher) or What should I study? (the coach). */
enum class TeacherMode { Notes, Coach }

/** A deck the coach proposed: not created yet, being created, created (its id) or failed (the web's text). */
sealed interface DeckState {
    data object Idle : DeckState
    data object Creating : DeckState
    data class Created(val deckId: Long) : DeckState
    data class Failed(val message: String) : DeckState
}

/** One question and its answer (null while thinking); [failed] if it could not be answered. */
data class Exchange(
    val id: Long,
    val question: String,
    val notes: TeacherAnswer? = null,
    val coach: CoachAnswer? = null,
    val failed: Boolean = false,
    val deck: DeckState = DeckState.Idle,
    val quizError: String? = null,
) {
    val pending get() = notes == null && coach == null && !failed
}

data class TeacherUi(
    val mode: TeacherMode = TeacherMode.Notes,
    val exchanges: List<Exchange> = emptyList(),
    val thinkingSeconds: Int = 0,
) {
    val busy get() = exchanges.any { it.pending }
}

/** A question is ready to send with at least 2 characters (the web's minimum). */
fun canSend(text: String) = text.trim().length >= QUESTION_MIN

/** The field keeps at most the web's 500 characters. */
fun cut(text: String) = text.take(QUESTION_MAX)

/**
 * Where a question goes: a clear one goes to its place whatever the mode (design/teacher.md, 2); the mode only
 * decides when it is not clear, or when it looks like both.
 */
fun route(question: String, mode: TeacherMode): TeacherMode {
    val forCoach = FORCED.containsKey(question.lowercase().trim().trimEnd('.', '!', '?')) || GRAMMAR_QUIZ.containsMatchIn(question) ||
        TIME_BUDGET.containsMatchIn(question) || intent(question) != null || plannedTools(question).isNotEmpty()
    val forNotes = askedWord(question) != null || comparedWords(question) != null || checkedSentence(question) != null ||
        askedPhrase(question) != null || asksSentence(question)
    return when {
        forCoach && !forNotes -> TeacherMode.Coach
        forNotes && !forCoach -> TeacherMode.Notes
        else -> mode
    }
}

@HiltViewModel
class TeacherViewModel @Inject constructor(
    private val teacher: TeacherService,
    private val coach: CoachEngine,
    private val quiz: QuizRepository,
    private val flashcards: FlashcardRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherUi())
    val state = _state.asStateFlow()
    private var nextId = 0L
    private var ticker: Job? = null

    fun setMode(mode: TeacherMode) = _state.update { it.copy(mode = mode) }

    fun clear() = _state.update { it.copy(exchanges = emptyList()) }

    private fun change(id: Long, block: (Exchange) -> Exchange) =
        _state.update { s -> s.copy(exchanges = s.exchanges.map { if (it.id == id) block(it) else it }) }

    /** Sends a question (once it has 2 characters); answers come in order, one at a time. */
    fun send(text: String) {
        val question = cut(text.trim())
        if (!canSend(question) || _state.value.busy) return
        val id = nextId++
        val mode = route(question, _state.value.mode)
        val history = _state.value.exchanges.map { it.question }
        _state.update { it.copy(exchanges = it.exchanges + Exchange(id, question), thinkingSeconds = 0) }
        ticker?.cancel()
        ticker = viewModelScope.launch { while (true) { delay(1000); _state.update { it.copy(thinkingSeconds = it.thinkingSeconds + 1) } } }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                if (mode == TeacherMode.Coach) coach.ask(question, history).let { a -> { e: Exchange -> e.copy(coach = a) } }
                else teacher.ask(question).let { a -> { e: Exchange -> e.copy(notes = a) } }
            } // never logs the question or the answer
            change(id) { e -> result.getOrNull()?.invoke(e) ?: e.copy(failed = true) }
            ticker?.cancel()
        }
    }

    /** Try again after an error: the same question, as a new one. */
    fun retry(id: Long) {
        val e = _state.value.exchanges.firstOrNull { it.id == id } ?: return
        _state.update { s -> s.copy(exchanges = s.exchanges.filter { it.id != id }) }
        send(e.question)
    }

    /** Start quiz: makes the quiz the coach proposed (rule types only until phase 6) and opens it. */
    fun startQuiz(id: Long, proposal: QuizProposal, onOpen: (Long) -> Unit) = viewModelScope.launch {
        val request = QuizRequest(
            topic = proposal.topic, count = proposal.count, words = proposal.words.orEmpty(),
            types = proposal.types.filter { it !in MODEL_TYPES }.ifEmpty { proposal.types },
        )
        val run = runCatchingCancellable { quiz.generate(request) }.getOrNull()
        if (run != null) onOpen(run.id) else change(id) { it.copy(quizError = "Your notes don't have enough material for this quiz.") }
    }

    /** Create deck: a NEW deck with those words of your notes (copies; your other decks don't change). */
    fun createDeck(id: Long) {
        val proposal = _state.value.exchanges.firstOrNull { it.id == id }?.coach?.deck ?: return
        change(id) { it.copy(deck = DeckState.Creating) }
        viewModelScope.launch {
            val deck = try {
                DeckState.Created(flashcards.createCustomDeck(proposal.name, proposal.words))
            } catch (e: FlashcardException) {
                DeckState.Failed("None of these words are in your notes anymore, so the deck wasn't created.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DeckState.Failed("The deck couldn't be created.")
            }
            change(id) { it.copy(deck = deck) }
        }
    }
}
