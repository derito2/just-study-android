package com.dero.juststudykorean.data.speech

import kotlinx.coroutines.flow.Flow

// FASE_9_PLAN 3: the speech contract, the same whatever the engine (D9-3: engine B, the platform's on-device recognizer).

sealed interface SpeechAvailability {
    data object Ready : SpeechAvailability
    data object NeedsDownload : SpeechAvailability
    data object Unsupported : SpeechAvailability
    data class Error(val code: String) : SpeechAvailability
}

enum class SpeechFailure { NoSpeech, NotUnderstood, Busy, Engine, Timeout }

sealed interface SpeechEvent {
    data object Listening : SpeechEvent
    /** 0..1, when the engine gives it. */
    data class Level(val value: Float) : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    /** The speaker stopped (or [KoreanSpeech.finish]): the engine is recognizing (design C, Processing). */
    data object EndOfSpeech : SpeechEvent
    data class Final(val candidates: List<String>, val confidences: List<Float>?) : SpeechEvent
    data class Failed(val reason: SpeechFailure) : SpeechEvent
}

interface KoreanSpeech {
    suspend fun availability(): SpeechAvailability
    /**
     * Opens the microphone and emits until Final or Failed (then it completes). Cancelling the collection closes the
     * microphone. Completing without either means it was cut (the audio focus was lost). Nothing is ever stored.
     */
    fun listen(maxMs: Long): Flow<SpeechEvent>
    /** Stops listening now and recognizes what was heard so far (tapping the microphone again, design C). */
    fun finish()
}
