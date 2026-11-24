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
import com.dero.juststudykorean.data.flashcards.activeCardMs
import com.dero.juststudykorean.data.flashcards.StudyCard
import com.dero.juststudykorean.data.prefs.CardDirection
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.data.audio.KoreanVoice
import com.dero.juststudykorean.data.audio.KoreanVoices
import com.dero.juststudykorean.data.audio.VoiceState
import com.dero.juststudykorean.data.games.GameContentProvider
import com.dero.juststudykorean.korean.flashcards.CardMode
import com.dero.juststudykorean.korean.flashcards.Cloze
import com.dero.juststudykorean.korean.flashcards.clozeFor
import com.dero.juststudykorean.korean.flashcards.modeFor
import com.dero.juststudykorean.korean.norm
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
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
    /** F2: a gap in one of your examples, per card id; null until the bank of your notes is read. */
    val clozes: Map<Long, Cloze>? = null,
    /** 🔊 was tapped with the media volume at 0 (design/beta.md 2.4). */
    val volumeHint: Boolean = false,
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

/** F2: how this card is shown now (Fill the Gap and Listen Only need a word card; Listen also the voice). */
fun cardMode(card: StudyCard, ui: StudyUi, gap: Boolean, listen: Boolean, voiceReady: Boolean): CardMode {
    val word = typingFor(true, card, ui.details)
    return modeFor(card.card.id + card.card.repetitions, gap, listen, hasCloze = word && ui.clozes?.containsKey(card.card.id) == true, voice = word && voiceReady)
}

/** When the card would come back with each button ("10 min", "24 h"…), as the web's preview. */
fun preview(card: StudyCard, cfg: Scheduling): Map<Rating, String> {
    val state = CardState(card.card.ease, card.card.intervalHours.toInt(), card.card.repetitions.toInt())
    return Rating.entries.associateWith { label(schedule(state, it, cfg).dueInMinutes) }
}

/** A review session (design/flashcards.md C). Each rating is saved at once; a second tap on the same card is ignored. */
@HiltViewModel
class StudyViewModel @Inject constructor(
    private val repo: FlashcardRepository,
    private val prefs: UserPrefs,
    voices: KoreanVoices,
    private val content: GameContentProvider,
    private val ai: QuizAi? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(StudyUi())
    val state = _state.asStateFlow()
    /** F2: the phone's Korean voice. A call or another app taking the audio only stops it: the session doesn't pause. */
    private val voice: KoreanVoice = voices.create().also { v -> v.onInterrupted = { v.stop() } }
    val voiceReady: StateFlow<Boolean> = voice.state.map { it == VoiceState.Ready }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val voiceMissing: StateFlow<Boolean> = voice.state.map { it == VoiceState.Missing }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val gapCards: StateFlow<Boolean> = prefs.gapCards.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val listenCards: StateFlow<Boolean> = prefs.listenCards.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private var speaking: Job? = null
    val direction: StateFlow<CardDirection> = prefs.cardDirection.stateIn(viewModelScope, SharingStarted.Eagerly, CardDirection.KoToEn)
    val typeAnswer: StateFlow<Boolean> = prefs.typeAnswer.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val scheduling: StateFlow<Scheduling> = prefs.scheduling.stateIn(viewModelScope, SharingStarted.Eagerly, Scheduling())

    private var deckId: Long? = null
    private var opened = false
    private var rating = false
    private var shownAt = 0L
    private var flippedAt: Long? = null // R1-13: flipping is activity too

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
            flippedAt = null
            findGaps(cards)
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
        if (!_state.value.flipped) flippedAt = System.nanoTime()
        _state.value = _state.value.copy(flipped = true)
        val typed = _state.value.typed
        if (meaning == null || ai?.ready() != true || compareAnswer(typed, meaning.second, korean = false).verdict !in setOf(Verdict.Wrong, Verdict.Almost)) return
        viewModelScope.launch {
            val same = runCatchingCancellable { ai.sameMeaning("What does '${meaning.first}' mean?", meaning.second, typed) }.getOrDefault(false)
            if (same && _state.value.card?.card?.id == card.card.id) _state.value = _state.value.copy(aiAccepted = card.card.id)
        }
    }

    fun type(text: String) { _state.value = _state.value.copy(typed = text) }

    /** The gaps of these cards, from all the examples of each word in your notes (the bank Games uses, key "w:" + norm). */
    private suspend fun findGaps(cards: List<StudyCard>) {
        val words = runCatchingCancellable { content.bank().words.associateBy { it.key } }.getOrDefault(emptyMap())
        val gaps = cards.mapNotNull { c ->
            words["w:" + norm(c.card.koreanWord)]?.let { w -> clozeFor(c.card.koreanWord, w.examples, seed = c.card.id) }?.let { c.card.id to it }
        }.toMap()
        _state.value = _state.value.copy(clozes = _state.value.clozes.orEmpty() + gaps)
    }

    /** The mode of [card] with the switches and the voice as they are now. */
    fun modeOf(card: StudyCard) = cardMode(card, _state.value, gapCards.value, listenCards.value, voiceReady.value)

    /** 🔊: says [text] once; at volume 0 nothing sounds and the hint shows. */
    fun speak(text: String) {
        if (voice.muted) { _state.value = _state.value.copy(volumeHint = true); return }
        _state.value = _state.value.copy(volumeHint = false)
        speaking?.cancel()
        voice.stop()
        speaking = viewModelScope.launch { runCatchingCancellable { voice.speak(text) } }
    }

    fun setGapCards(on: Boolean) = viewModelScope.launch { prefs.setGapCards(on) }

    fun setListenCards(on: Boolean) = viewModelScope.launch { prefs.setListenCards(on) }

    override fun onCleared() = voice.release()

    fun setDirection(d: CardDirection) = viewModelScope.launch { prefs.setCardDirection(d) }

    fun setTypeAnswer(on: Boolean) = viewModelScope.launch { prefs.setTypeAnswer(on) }

    /** Saves the rating and shows the next card; when the queue ends, reloads (Again cards may be due again). */
    fun rate(value: Rating) {
        val s = _state.value
        val card = s.card ?: return
        if (!s.flipped || rating) return
        rating = true
        speaking?.cancel()
        voice.stop()
        viewModelScope.launch {
            try {
                val now = System.nanoTime()
                val active = activeCardMs(shownAt / 1_000_000, flippedAt?.let { it / 1_000_000 }, now / 1_000_000)
                repo.review(card.card.id, value, (now - shownAt) / 1_000_000, scheduling.value, activeMs = active)
                val rest = s.queue.drop(1)
                if (rest.isNotEmpty()) {
                    _state.value = _state.value.copy(queue = rest, flipped = false, typed = "", reviewed = s.reviewed + 1, aiAccepted = null, volumeHint = false)
                    shownAt = System.nanoTime()
                    flippedAt = null
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
