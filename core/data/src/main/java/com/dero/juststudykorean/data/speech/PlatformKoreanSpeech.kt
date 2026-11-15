package com.dero.juststudykorean.data.speech

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private val KOREAN: Locale = Locale.KOREA
private const val MAX_RESULTS = 5
/** onRmsChanged gives about -2..10 dB; 0..1 for the wave. */
private fun level(rms: Float) = ((rms + 2f) / 12f).coerceIn(0f, 1f)

internal fun failureOf(error: Int): SpeechFailure = when (error) {
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NoSpeech
    SpeechRecognizer.ERROR_NO_MATCH -> SpeechFailure.NotUnderstood
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechFailure.Busy
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechFailure.Timeout
    else -> SpeechFailure.Engine
}

/**
 * D9-3, engine B: Android's on-device SpeechRecognizer (ko-KR), no dependency. Only on the main thread; one recognizer
 * per listening, destroyed when it ends or is cancelled. The app never sees nor stores the audio.
 */
@Singleton
class PlatformKoreanSpeech @Inject constructor(@param:ApplicationContext private val context: Context) : KoreanSpeech {
    private val audio = context.getSystemService(AudioManager::class.java)

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, KOREAN.toLanguageTag())
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

    override suspend fun availability(): SpeechAvailability = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return@withContext SpeechAvailability.Unsupported
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try {
            suspendCancellableCoroutine<SpeechAvailability> { done ->
                recognizer.checkRecognitionSupport(intent(), context.mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(s: RecognitionSupport) {
                        val ko = { list: List<String> -> list.any { it.startsWith("ko") } }
                        done.resume(
                            when {
                                ko(s.installedOnDeviceLanguages) -> SpeechAvailability.Ready
                                ko(s.pendingOnDeviceLanguages) || ko(s.supportedOnDeviceLanguages) -> SpeechAvailability.NeedsDownload
                                else -> SpeechAvailability.Unsupported
                            },
                        )
                    }

                    override fun onError(error: Int) = done.resume(SpeechAvailability.Error("support_$error"))
                })
            }
        } finally {
            recognizer.destroy()
        }
    }

    override fun listen(maxMs: Long): Flow<SpeechEvent> = callbackFlow {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { if (it < 0) channel.close() } // a call or another app: the microphone closes
            .build()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(SpeechEvent.Listening) }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) { trySend(SpeechEvent.Level(level(rmsdB))) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit // never kept
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) { trySend(SpeechEvent.Failed(failureOf(error))); channel.close() }
            override fun onResults(results: Bundle) {
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                trySend(if (heard.isEmpty()) SpeechEvent.Failed(SpeechFailure.NoSpeech) else SpeechEvent.Final(heard, results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.toList()))
                channel.close()
            }
            override fun onPartialResults(partial: Bundle) {
                partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { trySend(SpeechEvent.Partial(it)) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        audio.requestAudioFocus(focus)
        recognizer.startListening(intent())
        val stop = Runnable { recognizer.stopListening() } // the cap: what was heard so far is recognized
        val handler = android.os.Handler(context.mainLooper).apply { postDelayed(stop, maxMs) }
        awaitClose {
            handler.removeCallbacks(stop)
            recognizer.cancel()
            recognizer.destroy()
            audio.abandonAudioFocusRequest(focus)
        }
    }.flowOn(Dispatchers.Main)
}
