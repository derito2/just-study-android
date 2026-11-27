package com.dero.juststudykorean.ui.listening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.audio.KoreanVoice
import com.dero.juststudykorean.data.audio.KoreanVoices
import com.dero.juststudykorean.data.audio.VoiceState
import com.dero.juststudykorean.data.listening.ListeningRepository
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.data.speech.KoreanSpeech
import com.dero.juststudykorean.data.speech.SpeechAvailability
import com.dero.juststudykorean.korean.Comparison
import com.dero.juststudykorean.korean.Verdict
import com.dero.juststudykorean.korean.compareAnswer
import com.dero.juststudykorean.korean.listening.LabSentence
import com.dero.juststudykorean.korean.listening.ListenMode
import com.dero.juststudykorean.korean.listening.dictation
import com.dero.juststudykorean.korean.listening.shadowing
import com.dero.juststudykorean.korean.listening.unavailable
import com.dero.juststudykorean.korean.listening.withWords
import com.dero.juststudykorean.ui.components.runCatchingCancellable
import com.dero.juststudykorean.ui.components.speech.SpeakPhase
import com.dero.juststudykorean.ui.components.speech.SpeakingMachine
import com.dero.juststudykorean.ui.components.speech.SpeakingUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.random.Random

/** My Mistakes sent words, and none has a dictation sentence. */
const val NO_SENTENCE_FOR_WORDS = "None of these words has a sentence to dictate."

private const val SLOWER = 0.7f
private const val VOICE_CHECK_MS = 3_000L

/** Dictation, after Check: what you typed, the letter-by-letter comparison and whether it was exact. */
data class DictationResult(val typed: String, val correct: Boolean, val comparison: Comparison)

data class ListeningUi(
    val loading: Boolean = true,
    val mode: ListenMode = ListenMode.Dictation,
    val rate: Float = 1f,
    val items: List<LabSentence> = emptyList(),
    val index: Int = 0,
    val typed: String = "",
    val result: DictationResult? = null,
    /** Hands-free: "{n} cards due · not rated here". */
    val dueCount: Int = 0,
    /** Why the mode can't be used now (no voice, no sentences, no cards); null if it can. */
    val reason: String? = null,
    /** Hands-free is going on by itself. */
    val running: Boolean = false,
    /** Hands-free: the meaning shows after the Korean has sounded. */
    val showMeaning: Boolean = false,
) {
    val current: LabSentence? get() = items.getOrNull(index)
}

/**
 * Listening Lab (design/beta.md 4, FASE_11_PLAN PR 7): Dictation, Shadowing (the Quiz's microphone, not scored, D9-6) and
 * Hands-free (cards are never rated). Time here doesn't count as studying (D11-2). Nothing said is stored or logged.
 */
@HiltViewModel
class ListeningViewModel @Inject constructor(
    private val repo: ListeningRepository,
    private val prefs: UserPrefs,
    voices: KoreanVoices,
    private val speech: KoreanSpeech? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ListeningUi())
    val state = _state.asStateFlow()
    /** A call or another app taking the audio pauses hands-free and stops the voice. */
    private val voice: KoreanVoice = voices.create().also { v -> v.onInterrupted = { viewModelScope.launch(Dispatchers.Main) { pause() } } }
    val systemKeyboard: StateFlow<Boolean> = prefs.labSystemKeyboard.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val machine = speech?.let { SpeakingMachine(it, viewModelScope) }
    val speaking: StateFlow<SpeakingUi> = machine?.state ?: MutableStateFlow(SpeakingUi(phase = SpeakPhase.Unavailable))
    /** Hands-free waits 3 of these after the Korean and 2 after the meaning (tests make it short). */
    internal var handsFreePauseMs = 1_000L
    private var availability: SpeechAvailability = SpeechAvailability.Unsupported
    private var micDenied = false
    private var started = false
    private var modeChosen = false
    private var sound: Job? = null
    private var loop: Job? = null
    private var load: Job? = null
    private val seed = System.currentTimeMillis()

    init {
        // Shadowing: each comparison saves only whether it matched (never what was heard).
        machine?.let { m -> viewModelScope.launch { var seen = 0; m.state.collect { s -> if (s.phase == SpeakPhase.Compared && s.attempts > seen) { seen = s.attempts; save(s.comparison?.matches == true) } } } }
    }

    /** Only for My Mistakes: a dictation of the sentences with these words. */
    private var keys: List<String>? = null

    fun open(keys: List<String>? = null) {
        if (started) return
        started = true
        this.keys = keys
        if (keys != null) { modeChosen = true; _state.value = _state.value.copy(mode = ListenMode.Dictation) }
        viewModelScope.launch {
            if (!modeChosen) _state.value = _state.value.copy(mode = runCatching { ListenMode.valueOf(prefs.listeningMode.first()) }.getOrDefault(ListenMode.Dictation))
            _state.value = _state.value.copy(rate = prefs.listeningRate.first())
            reload()
        }
    }

    private fun reload() {
        pause()
        machine?.close()
        load?.cancel()
        load = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            withTimeoutOrNull(VOICE_CHECK_MS) { voice.state.first { it != VoiceState.Checking } }
            availability = speech?.let { runCatchingCancellable { it.availability() }.getOrNull() } ?: SpeechAvailability.Unsupported
            val mode = _state.value.mode
            val sentences = runCatchingCancellable { repo.sentences() }.getOrDefault(emptyList())
            val (cards, due) = if (mode == ListenMode.HandsFree) runCatchingCancellable { repo.cards() }.getOrDefault(emptyList<LabSentence>() to 0) else emptyList<LabSentence>() to 0
            val items = when (mode) {
                ListenMode.Dictation -> dictation(sentences).let { d -> keys?.let { withWords(d, it) } ?: d }.shuffled(Random(seed))
                ListenMode.Shadowing -> shadowing(sentences).shuffled(Random(seed))
                ListenMode.HandsFree -> cards
            }
            val reason = unavailable(mode, sentences, cards.size, voice.state.value == VoiceState.Ready)
                ?: NO_SENTENCE_FOR_WORDS.takeIf { mode == ListenMode.Dictation && keys != null && items.isEmpty() }
            _state.value = _state.value.copy(loading = false, items = items, index = 0, typed = "", result = null, dueCount = due, reason = reason, showMeaning = false)
            if (reason == null) enter()
        }
    }

    /** A new item: Dictation and Shadowing play it once; Shadowing gets its microphone ready. */
    private fun enter() {
        val item = _state.value.current ?: return
        when (_state.value.mode) {
            ListenMode.Dictation -> say(item.korean, _state.value.rate)
            ListenMode.Shadowing -> { machine?.start(item.korean, sentence = true, availability, micDenied); say(item.korean, 1f) }
            ListenMode.HandsFree -> Unit
        }
    }

    private fun say(text: String, rate: Float) {
        sound?.cancel()
        voice.stop()
        sound = viewModelScope.launch { runCatchingCancellable { voice.speak(text, rate) } }
    }

    private fun save(correct: Boolean) {
        val item = _state.value.current ?: return
        val mode = _state.value.mode
        viewModelScope.launch { runCatchingCancellable { repo.save(mode, item.key, correct) } }
    }

    fun setMode(mode: ListenMode) {
        modeChosen = true
        if (mode == _state.value.mode && !_state.value.loading) return
        _state.value = _state.value.copy(mode = mode)
        viewModelScope.launch { prefs.setListeningMode(mode.name) }
        if (started) reload()
    }

    fun setRate(rate: Float) {
        _state.value = _state.value.copy(rate = rate)
        viewModelScope.launch { prefs.setListeningRate(rate) }
    }

    fun setSystemKeyboard(on: Boolean) = viewModelScope.launch { prefs.setLabSystemKeyboard(on) }

    /** The 72 dp button: Dictation and Shadowing say it again; Hands-free starts (or resumes) going on by itself. */
    fun play() {
        val item = _state.value.current ?: return
        if (_state.value.mode != ListenMode.HandsFree) { say(item.korean, if (_state.value.mode == ListenMode.Dictation) _state.value.rate else 1f); return }
        if (_state.value.running) return
        _state.value = _state.value.copy(running = true)
        loop = viewModelScope.launch {
            while (true) {
                val now = _state.value.current ?: break
                _state.value = _state.value.copy(showMeaning = false)
                runCatchingCancellable { voice.speak(now.korean, _state.value.rate) }
                delay(3 * handsFreePauseMs)
                _state.value = _state.value.copy(showMeaning = true)
                delay(2 * handsFreePauseMs)
                val s = _state.value
                _state.value = s.copy(index = (s.index + 1) % s.items.size)
            }
        }
    }

    fun replay() { _state.value.current?.let { say(it.korean, _state.value.rate) } }

    fun slower() { _state.value.current?.let { say(it.korean, SLOWER) } }

    /** Hands-free stops where it is (Pause, leaving the screen, the screen turning off, an interruption). */
    fun pause() {
        loop?.cancel()
        loop = null
        sound?.cancel()
        voice.stop()
        if (_state.value.running) _state.value = _state.value.copy(running = false)
    }

    fun type(text: String) { if (_state.value.result == null) _state.value = _state.value.copy(typed = text) }

    /** Dictation: right only if exact (Almost is not); the sentence sounds again. */
    fun check() {
        val s = _state.value
        val item = s.current ?: return
        if (s.mode != ListenMode.Dictation || s.result != null || s.typed.isBlank()) return
        val c = compareAnswer(s.typed, item.korean, korean = true)
        _state.value = s.copy(result = DictationResult(s.typed, c.verdict == Verdict.Exact, c))
        save(c.verdict == Verdict.Exact)
        say(item.korean, s.rate)
    }

    fun next() {
        val s = _state.value
        if (s.items.isEmpty()) return
        val running = s.running
        pause()
        machine?.close()
        _state.value = s.copy(index = (s.index + 1) % s.items.size, typed = "", result = null, showMeaning = false, running = false)
        if (running) play() else enter()
    }

    fun tapMic() = machine?.tap()
    fun cancelMic() = machine?.close()
    fun closeMic() = machine?.close()
    fun tryAgain() = machine?.tryAgain()
    fun micDenied() { micDenied = true; machine?.permissionDenied() }
    fun retrySpeech() = viewModelScope.launch {
        availability = speech?.let { runCatchingCancellable { it.availability() }.getOrNull() } ?: SpeechAvailability.Unsupported
        machine?.retry(availability)
    }

    override fun onCleared() = voice.release()
}
