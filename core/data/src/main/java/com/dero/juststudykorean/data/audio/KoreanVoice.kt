package com.dero.juststudykorean.data.audio

import kotlinx.coroutines.flow.StateFlow

enum class VoiceState { Checking, Ready, Missing }

/** FASE_8_PLAN 4.11: the Korean voice of the two listening games. TtsKoreanVoice (the platform's, no network) is PR C. */
interface KoreanVoice {
    val state: StateFlow<VoiceState>
    /** Returns when it has finished sounding; cancellable. */
    suspend fun speak(text: String, rate: Float = 1f)
    fun stop()
    fun release()
}
