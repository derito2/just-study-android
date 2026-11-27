package com.dero.juststudykorean.ui.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.mistakes.MistakesRepository
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.quiz.QuizRequest
import com.dero.juststudykorean.data.quiz.RULE_TYPES
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.mistakes.Mistake
import com.dero.juststudykorean.korean.mistakes.Pattern
import com.dero.juststudykorean.korean.mistakes.PatternKind
import com.dero.juststudykorean.korean.mistakes.classify
import com.dero.juststudykorean.korean.mistakes.patterns
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** 7 days · 30 days · All. */
val PERIOD_DAYS = listOf(7L, 30L, null)

/** Where Practice this goes (design/beta.md 6): Grammar Lab, the dictation, or a quiz / a deck with those words. */
enum class Practice { GrammarLab, Dictation, Words }

fun practiceOf(kind: PatternKind): Practice = when (kind) {
    PatternKind.ParticleBatchim, PatternKind.VowelHarmony, PatternKind.Contraction, PatternKind.Irregular, PatternKind.Tense -> Practice.GrammarLab
    PatternKind.Batchim, PatternKind.Vowel -> Practice.Dictation
    PatternKind.Spacing, PatternKind.WrongWord, PatternKind.Meaning -> Practice.Words
}

data class MistakesUi(
    val loading: Boolean = true,
    val period: Int = 0,
    val patterns: List<Pattern> = emptyList(),
    /** "Deck created: …" or why a quiz or a deck couldn't be made. */
    val message: String? = null,
)

/** My Mistakes (design/beta.md 6, FASE_11_PLAN PR 9): read only; not study time (D11-2). */
@HiltViewModel
class MistakesViewModel @Inject constructor(
    private val repo: MistakesRepository,
    private val quiz: QuizRepository,
    private val cards: FlashcardRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val _state = MutableStateFlow(MistakesUi())
    val state = _state.asStateFlow()
    private var all: List<Mistake> = emptyList()

    fun setPeriod(i: Int) = viewModelScope.launch {
        val since = PERIOD_DAYS[i]?.let { clock.now().minus(Duration.ofDays(it)).toEpochMilli() } ?: 0L
        all = repo.mistakes(since)
        _state.value = MistakesUi(loading = false, period = i, patterns = patterns(all, since))
    }

    /** Your words of that pattern in the period (word_state's keys), at most 10. */
    private fun words(p: Pattern) = all.filter { classify(it) == p.kind }.mapNotNull { it.wordKey }.distinct().take(10)

    /** Grammar Lab opens with the point of the newest example that has one. */
    fun pointOf(p: Pattern): String? = all.filter { classify(it) == p.kind }.sortedByDescending { it.at }.firstNotNullOfOrNull { it.pointKey }

    /** The dictation of Listening Lab with your words of that pattern. */
    fun dictationWords(p: Pattern): List<String> = words(p)

    fun quizMe(p: Pattern, onOpen: (Long) -> Unit) = viewModelScope.launch {
        val words = words(p)
        val run = runCatchingCancellable { quiz.generate(QuizRequest(count = words.size.coerceIn(1, 10), types = RULE_TYPES.sorted(), words = words)) }.getOrNull()
        if (run != null && words.isNotEmpty()) onOpen(run.id) else _state.value = _state.value.copy(message = "Your notes don't have enough material for this quiz.")
    }

    fun makeDeck(p: Pattern) = viewModelScope.launch {
        val name = "Mistakes · " + clock.now().atZone(clock.zone).format(DateTimeFormatter.ofPattern("d MMM", Locale.US))
        val text = runCatchingCancellable { cards.createCustomDeck(name, words(p)); "Deck created: $name." }.getOrElse { "None of these words are in your notes, so the deck wasn't created." }
        _state.value = _state.value.copy(message = text)
    }
}
