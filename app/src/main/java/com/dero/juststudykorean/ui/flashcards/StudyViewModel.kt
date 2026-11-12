package com.dero.juststudykorean.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.ai.QuizAi
import com.dero.juststudykorean.data.flashcards.CardDetails
import com.dero.juststudykorean.korean.Verdict
import com.dero.juststudykorean.korean.compareAnswer
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import com.dero.juststudykorean.data.flashcards.CardSource
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.flashcards.StudyCard
import com.dero.juststudykorean.data.prefs.CardDirection
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.korean.CardState
import com.dero.juststudykorean.korean.Rating
import com.dero.juststudykorean.korean.Scheduling
import com.dero.juststudykorean.korean.isWordCard
import com.dero.juststudykorean.korean.label
import com.dero.juststudykorean.korean.schedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

data class StudyUi(
    val loading: Boolean = true,
    val queue: List<StudyCard> = emptyList(),
    val flipped: Boolean = false,
    val typed: String = "",
    val details: Map<Long, CardDetails> = emptyMap(),
    val sources: Map<Long, CardSource> = emptyMap(),
    val reviewed: Int = 0,
    val error: String? = null,
    /** The card whose typed meaning the model accepted (use 3), shown with AiMark; nothing is saved. */
    val aiAccepted: Long? = null,
) {
    val card: StudyCard? get() = queue.firstOrNull()
}

/** The side a card shows: with Both, it alternates by card (the web's sideFor). */
fun sideFor(direction: CardDirection, card: StudyCard): CardDirection = when (direction) {
    CardDirection.Both -> if ((card.card.id + card.card.repetitions) % 2 == 0L) CardDirection.KoToEn else CardDirection.EnToKo
    else -> direction
}

/** Type the answer only works on word cards. */
fun typingFor(on: Boolean, card: StudyCard?, details: Map<Long, CardDetails>) =
    on && card != null && isWordCard(card.card.front, details[card.card.id]?.structure?.isNotEmpty() == true)

/** When the card would come back with each button ("10 min", "24 h"…), as the web's preview. */
fun preview(card: StudyCard, cfg: Scheduling): Map<Rating, String> {
    val state = CardState(card.card.ease, card.card.intervalHours.toInt(), card.card.repetitions.toInt())
    return Rating.entries.associateWith { label(schedule(state, it, cfg).dueInMinutes) }
}

/** A review session (design/flashcards.md C). Each rating is saved at once; a second tap on the same card is ignored. */
@HiltViewModel
class StudyViewModel @Inject constructor(private val repo: FlashcardRepository, private val prefs: UserPrefs, private val ai: QuizAi? = null) : ViewModel() {
    private val _state = MutableStateFlow(StudyUi())
    val state = _state.asStateFlow()
    val direction: StateFlow<CardDirection> = prefs.cardDirection.stateIn(viewModelScope, SharingStarted.Eagerly, CardDirection.KoToEn)
    val typeAnswer: StateFlow<Boolean> = prefs.typeAnswer.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val scheduling: StateFlow<Scheduling> = prefs.scheduling.stateIn(viewModelScope, SharingStarted.Eagerly, Scheduling())

    private var deckId: Long? = null
    private var opened = false
    private var rating = false
    private var shownAt = 0L

    /** Loads the deck's cards that are due (all of them, like the web). Once per screen. */
    fun open(deck: Long?) {
        if (opened) return
        opened = true
        deckId = deck
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val cards = repo.queue(deckId, prefs.cardDirection.first(), limit = 2000)
            val entities = cards.map { it.card }
            _state.value = _state.value.copy(
                loading = false, queue = cards, flipped = false, typed = "", error = null, aiAccepted = null,
                details = repo.details(entities), sources = repo.sources(entities),
            )
            shownAt = System.nanoTime()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = "Couldn't load the cards.")
        }
    }

    /**
     * Shows the back. [meaning]: (front, back) when you typed the meaning; if compareAnswer says it is wrong, the meaning
     * judge (use 3) may accept it. Only the screen changes: the rating stays yours.
     */
    fun flip(meaning: Pair<String, String>? = null) {
        val card = _state.value.card ?: return
        _state.value = _state.value.copy(flipped = true)
        val typed = _state.value.typed
        if (meaning == null || ai?.ready() != true || compareAnswer(typed, meaning.second, korean = false).verdict !in setOf(Verdict.Wrong, Verdict.Almost)) return
        viewModelScope.launch {
            val same = runCatchingCancellable { ai.sameMeaning("What does '${meaning.first}' mean?", meaning.second, typed) }.getOrDefault(false)
            if (same && _state.value.card?.card?.id == card.card.id) _state.value = _state.value.copy(aiAccepted = card.card.id)
        }
    }

    fun type(text: String) { _state.value = _state.value.copy(typed = text) }

    fun setDirection(d: CardDirection) = viewModelScope.launch { prefs.setCardDirection(d) }

    fun setTypeAnswer(on: Boolean) = viewModelScope.launch { prefs.setTypeAnswer(on) }

    /** Saves the rating and shows the next card; when the queue ends, reloads (Again cards may be due again). */
    fun rate(value: Rating) {
        val s = _state.value
        val card = s.card ?: return
        if (!s.flipped || rating) return
        rating = true
        viewModelScope.launch {
            try {
                repo.review(card.card.id, value, (System.nanoTime() - shownAt) / 1_000_000, scheduling.value)
                val rest = s.queue.drop(1)
                if (rest.isNotEmpty()) {
                    _state.value = _state.value.copy(queue = rest, flipped = false, typed = "", reviewed = s.reviewed + 1, aiAccepted = null)
                    shownAt = System.nanoTime()
                } else {
                    _state.value = _state.value.copy(reviewed = s.reviewed + 1)
                    load()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Couldn't save the review.")
            } finally {
                rating = false
            }
        }
    }
}
