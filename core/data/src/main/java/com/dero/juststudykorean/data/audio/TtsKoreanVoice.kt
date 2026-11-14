package com.dero.juststudykorean.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Makes the voice when Games opens (it is released when Games closes). */
open class KoreanVoices @Inject constructor(@param:ApplicationContext private val context: Context) {
    open fun create(): KoreanVoice = TtsKoreanVoice(context)
}

/** The offline ko-KR voice wins; among them, the best quality. Null: the listening games are off. */
internal fun offlineKorean(voices: Collection<Voice>): Voice? = voices
    .filter { it.locale.language == "ko" && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
    .maxByOrNull { it.quality }

/**
 * 4.11: Android's text-to-speech with an offline Korean voice only (never synthesis over the network). While it speaks
 * it holds AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK; losing it calls [onInterrupted].
 */
class TtsKoreanVoice(context: Context) : KoreanVoice {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(VoiceState.Checking)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()
    override var onInterrupted: (() -> Unit)? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { if (it == AudioManager.AUDIOFOCUS_LOSS || it == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onInterrupted?.invoke() }
        .build()
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status -> _state.value = if (status == TextToSpeech.SUCCESS) choose() else VoiceState.Missing }
    }

    private fun choose(): VoiceState {
        val t = tts ?: return VoiceState.Missing
        val voice = runCatching { offlineKorean(t.voices.orEmpty()) }.getOrNull() ?: return VoiceState.Missing
        if (t.setVoice(voice) != TextToSpeech.SUCCESS) return VoiceState.Missing
        t.setAudioAttributes(attributes)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onDone(id: String) { pending.remove(id)?.complete(Unit) }
            @Deprecated("Deprecated in Java") override fun onError(id: String) { pending.remove(id)?.complete(Unit) }
            override fun onError(id: String, errorCode: Int) { pending.remove(id)?.complete(Unit) }
            override fun onStop(id: String, interrupted: Boolean) { pending.remove(id)?.complete(Unit) }
        })
        return VoiceState.Ready
    }

    override val muted: Boolean get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

    override suspend fun speak(text: String, rate: Float) {
        val t = tts ?: return
        if (_state.value != VoiceState.Ready) return
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>().also { pending[id] = it }
        audio.requestAudioFocus(focus)
        try {
            t.setSpeechRate(rate)
            if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) return
            done.await()
        } finally {
            if (pending.remove(id) != null) t.stop() // cancelled while sounding
            audio.abandonAudioFocusRequest(focus)
        }
    }

    override fun stop() { tts?.stop() }

    override fun release() {
        onInterrupted = null
        tts?.stop(); tts?.shutdown(); tts = null
        pending.values.forEach { it.complete(Unit) }
        audio.abandonAudioFocusRequest(focus)
    }
}
