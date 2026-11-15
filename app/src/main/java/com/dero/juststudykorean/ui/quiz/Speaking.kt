package com.dero.juststudykorean.ui.quiz

import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.speech.KoreanSpeech
import com.dero.juststudykorean.data.speech.SpeechAvailability
import com.dero.juststudykorean.data.speech.SpeechEvent
import com.dero.juststudykorean.data.speech.SpeechFailure
import com.dero.juststudykorean.korean.KOREAN_ALTERNATIVES
import com.dero.juststudykorean.korean.speech.SpokenComparison
import com.dero.juststudykorean.korean.speech.compareSpoken
import com.dero.juststudykorean.korean.speech.normalizeSpoken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// FASE_9_PLAN 5 and design/quiz-speaking.md C: the spoken practice of the Quiz (D9-6: not scored, unlimited tries).

/** The form's type (never sent to the generator: it asks for write_ko). */
const val SPEAK = "speak_ko"

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

/** The form's types as the generator gets them: Say it in Korean asks for Write in Korean (FASE_9_PLAN 5). */
fun requestTypes(types: List<String>): List<String> =
    (types.filter { it != SPEAK } + if (SPEAK in types) listOf("write_ko") else emptyList()).distinct()

/** Can be said: a rules' write_ko whose answer is only Hangul (any " / " alternative) and has 2 blocks or more. */
fun speakable(q: QuestionsEntity): Boolean = q.id > 0 && q.type == "write_ko" &&
    q.correctAnswer.split(KOREAN_ALTERNATIVES).all { alt -> normalizeSpoken(alt).let { it.length >= 2 && it.all { c -> c in '가'..'힣' } } }

/** Which questions are spoken: all the sayable ones if only Say it in Korean is on; alternating (the first spoken) if both. */
fun spokenIds(questions: List<QuestionsEntity>, speakOn: Boolean, writeOn: Boolean): Set<Long> {
    if (!speakOn) return emptySet()
    val sayable = questions.filter(::speakable)
    return (if (writeOn) sayable.filterIndexed { i, _ -> i % 2 == 0 } else sayable).map { it.id }.toSet()
}

/** Read it out loud for a sentence of 2 words or more; otherwise, say the word from its meaning (design B). */
fun readAloud(q: QuestionsEntity) = q.correctAnswer.split(KOREAN_ALTERNATIVES).first().trim().contains(' ')

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
                        _state.update { it.copy(phase = SpeakPhase.Compared, comparison = c, attempts = it.attempts + 1, level = 0f, partial = null) }
                    }
                    is SpeechEvent.Failed -> {
                        ended = true
                        val phase = when (e.reason) {
                            SpeechFailure.Busy, SpeechFailure.Engine -> SpeakPhase.Unavailable
                            else -> SpeakPhase.NoSpeech
                        }
                        _state.update { it.copy(phase = phase, level = 0f, partial = null) }
                    }
                }
            }
            if (!ended) toReady() // cut without a result (the audio focus was lost): Cancelled → Ready
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

    companion object {
        const val WORD_MS = 8_000L
        const val SENTENCE_MS = 12_000L
        const val PROCESSING_MS = 4_000L
        const val TALKBACK_MS = 600L
    }
}
