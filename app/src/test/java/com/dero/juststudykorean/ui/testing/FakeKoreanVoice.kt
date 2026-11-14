package com.dero.juststudykorean.ui.testing

import android.content.Context
import com.dero.juststudykorean.data.audio.KoreanVoice
import com.dero.juststudykorean.data.audio.KoreanVoices
import com.dero.juststudykorean.data.audio.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** 4.11: the voice in tests: "speaks" for [ms] and records what it said. Nothing sounds. */
class FakeKoreanVoice(ready: Boolean = true, private val ms: Long = 300) : KoreanVoice {
    override val state = MutableStateFlow(if (ready) VoiceState.Ready else VoiceState.Missing)
    val said = mutableListOf<String>()
    override var muted = false
    override var onInterrupted: (() -> Unit)? = null
    override suspend fun speak(text: String, rate: Float) { said += text; delay(ms) }
    override fun stop() = Unit
    override fun release() = Unit
}

class FakeKoreanVoices(context: Context, private val voice: KoreanVoice = FakeKoreanVoice(ready = false)) : KoreanVoices(context) {
    override fun create() = voice
}
