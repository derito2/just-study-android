package com.dero.juststudykorean.ai

import kotlinx.coroutines.flow.Flow

/** AICore's state for Gemini Nano (design/grammar-calendar-settings.md, On-device AI). */
sealed interface NanoStatus {
    data object Available : NanoStatus
    data object Downloadable : NanoStatus
    /** [percent] if AICore says how big the download is. */
    data class Downloading(val percent: Int?) : NanoStatus
    data object Unavailable : NanoStatus
}

/** AICore is busy (quota): the gate waits and tries once more. */
class NanoBusy : Exception("busy")

/** The model, as the gate sees it: [MlKitNano] on the phone, a fake in the tests. */
interface NanoModel {
    suspend fun status(): NanoStatus

    /** Starts AICore's download (only when the user taps Download) and reports its progress. */
    fun download(): Flow<NanoStatus>

    /** The whole answer (never streamed: it is validated before it is shown). Throws [NanoBusy] when AICore is busy. */
    suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?): String
}
