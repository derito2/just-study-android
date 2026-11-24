package com.dero.juststudykorean.ui.components.speech

import com.dero.juststudykorean.data.speech.KoreanSpeech
import com.dero.juststudykorean.data.speech.SpeechAvailability
import com.dero.juststudykorean.data.speech.SpeechEvent
import com.dero.juststudykorean.data.speech.SpeechFailure
import com.dero.juststudykorean.korean.speech.SpokenComparison
import com.dero.juststudykorean.korean.speech.compareSpoken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Moved from ui/quiz (FASE_11_PLAN PR 7): the microphone state machine shared by the Quiz and Listening Lab.

/** design C. [PermissionDenied] and [NotDownloaded] / [Unavailable] only let you go on. */
enum class SpeakPhase { Ready, Listening, Processing, Compared, NoSpeech, NotDownloaded, Unavailable, PermissionDenied }

data class SpeakingUi(
    val phase: SpeakPhase = SpeakPhase.Ready,
    /** 0..1 while listening, when the engine gives it. */
    val level: Float = 0f,
    val partial: String? = null,
    /** The last comparison: kept on Try again (design B). */
    val comparison: SpokenComparison? = null,
    val attempts: Int = 0,
    val shownAnswer: Boolean = false,
) {
    /** design B and G: Next from the first try or Show answer, and never blocked when the microphone can't be used. */
    val canGoOn: Boolean get() = attempts > 0 || shownAnswer || phase in setOf(SpeakPhase.NotDownloaded, SpeakPhase.Unavailable, SpeakPhase.PermissionDenied)
    val listening: Boolean get() = phase == SpeakPhase.Listening || phase == SpeakPhase.Processing
}

/**
 * The microphone of one spoken question. One listening at a time; every way it closes goes through [close] (cancelling
 * the collection), as the plan asks. Nothing heard is logged or stored: [onHeard] gets it for the results in memory.
 */
class SpeakingMachine(
    private val speech: KoreanSpeech,
    private val scope: CoroutineScope,
    private val talkBack: () -> Boolean = { false },
    private val onHeard: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow(SpeakingUi())
    val state: StateFlow<SpeakingUi> = _state.asStateFlow()
    private var job: Job? = null
    private var expected = ""
    private var maxMs = WORD_MS

    /** A new spoken question: its answer, its cap (8 s words, 12 s sentences) and what the engine can do now. */
    fun start(answer: String, sentence: Boolean, availability: SpeechAvailability, permissionDenied: Boolean) {
        close()
        expected = answer
        maxMs = if (sentence) SENTENCE_MS else WORD_MS
        _state.value = SpeakingUi(phase = phaseFor(availability, permissionDenied))
    }

    private fun phaseFor(a: SpeechAvailability, denied: Boolean) = when {
        a is SpeechAvailability.NeedsDownload -> SpeakPhase.NotDownloaded
        a !is SpeechAvailability.Ready -> SpeakPhase.Unavailable
        denied -> SpeakPhase.PermissionDenied
        else -> SpeakPhase.Ready
    }

    /** The microphone button: listen, or (while listening) stop now and recognize. */
    fun tap() {
        when (_state.value.phase) {
            SpeakPhase.Listening -> { speech.finish(); _state.update { it.copy(phase = SpeakPhase.Processing, level = 0f) }; capProcessing() }
            SpeakPhase.Ready, SpeakPhase.Compared, SpeakPhase.NoSpeech -> listen()
            else -> Unit
        }
    }

    private fun listen() {
        job?.cancel()
        _state.update { it.copy(phase = SpeakPhase.Listening, level = 0f, partial = null) }
        val started = System.currentTimeMillis()
        job = scope.launch {
            if (talkBack()) delay(TALKBACK_MS) // G: TalkBack finishes speaking first
            var ended = false
            speech.listen(maxMs).collect { e ->
                when (e) {
                    SpeechEvent.Listening -> Unit
                    is SpeechEvent.Level -> _state.update { if (it.phase == SpeakPhase.Listening) it.copy(level = e.value) else it }
                    is SpeechEvent.Partial -> _state.update { it.copy(partial = e.text) }
                    SpeechEvent.EndOfSpeech -> if (_state.value.phase == SpeakPhase.Listening) { _state.update { it.copy(phase = SpeakPhase.Processing, level = 0f) }; capProcessing() }
                    is SpeechEvent.Final -> {
                        ended = true
                        val c = compareSpoken(expected, e.candidates)
                        onHeard(c.heard)
                        log("speech result matches=${c.matches} attempt=${_state.value.attempts + 1} ms=${System.currentTimeMillis() - started}")
                        _state.update { it.copy(phase = SpeakPhase.Compared, comparison = c, attempts = it.attempts + 1, level = 0f, partial = null) }
                    }
                    is SpeechEvent.Failed -> {
                        ended = true
                        val phase = when (e.reason) {
                            SpeechFailure.Busy, SpeechFailure.Engine -> SpeakPhase.Unavailable
                            else -> SpeakPhase.NoSpeech
                        }
                        _state.update { it.copy(phase = phase, level = 0f, partial = null) }
                        log("speech failed reason=${e.reason} ms=${System.currentTimeMillis() - started}")
                    }
                }
            }
            if (!ended) { toReady(); log("speech cut ms=${System.currentTimeMillis() - started}") } // the audio focus was lost: Cancelled → Ready
        }
    }

    private var cap: Job? = null

    /** C: Checking… lasts 4 s at most; then it's No speech. */
    private fun capProcessing() {
        cap?.cancel()
        cap = scope.launch {
            delay(PROCESSING_MS)
            if (_state.value.phase == SpeakPhase.Processing) { job?.cancel(); _state.update { it.copy(phase = SpeakPhase.NoSpeech) } }
        }
    }

    private fun toReady() = _state.update { if (it.listening) it.copy(phase = SpeakPhase.Ready, level = 0f, partial = null) else it }

    /** Cancel, Leave quiz, Next, leaving the screen, ON_STOP: the microphone closes and nothing changes but the phase. */
    fun close() {
        job?.cancel(); job = null
        cap?.cancel(); cap = null
        toReady()
    }

    fun tryAgain() = _state.update { if (it.phase == SpeakPhase.Compared || it.phase == SpeakPhase.NoSpeech) it.copy(phase = SpeakPhase.Ready) else it }

    fun showAnswer() = _state.update { it.copy(shownAnswer = true) }

    /** The permission dialog was refused twice (or "don't ask again"). */
    fun permissionDenied() { close(); _state.update { it.copy(phase = SpeakPhase.PermissionDenied) } }

    /** Unavailable → Try again: the engine is asked again. */
    fun retry(availability: SpeechAvailability) = _state.update { it.copy(phase = phaseFor(availability, false)) }

    /** FASE_9_PLAN 6: only the result, the try, times and the error code; never what was heard. */
    private fun log(line: String) { runCatching { android.util.Log.i("JustStudy", line) } }

    companion object {
        const val WORD_MS = 8_000L
        const val SENTENCE_MS = 12_000L
        const val PROCESSING_MS = 4_000L
        const val TALKBACK_MS = 600L
    }
}

/** The activity behind a Compose context (to ask whether the microphone permission was refused for good). */
internal tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
