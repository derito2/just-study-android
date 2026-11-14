package com.dero.juststudykorean.data.audio

import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * FASE_8_PLAN 4.11, task 9: what text-to-speech engine and Korean voices the phone has, and whether one works without
 * network. Skipped when there is none. It only speaks (one fixed word, not from the notes) with `-e speak true`.
 */
@RunWith(AndroidJUnit4::class)
class KoreanVoiceCheck {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("KoreanVoiceCheck", s)

    @Test
    fun theOfflineKoreanVoice() = runBlocking {
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val tts = TextToSpeech(context) { status = it; ready.countDown() }
        ready.await(10, TimeUnit.SECONDS)
        val korean = if (status == TextToSpeech.SUCCESS) tts.voices.orEmpty().filter { it.locale.language == "ko" } else emptyList()
        log("engine=${tts.defaultEngine} engines=${tts.engines.size} status=$status korean=${korean.size} offline=${korean.count { !it.isNetworkConnectionRequired }} chosen=${offlineKorean(korean)?.name}")
        tts.shutdown()

        val voice = TtsKoreanVoice(context)
        val state = withTimeout(10_000) { voice.state.first { it != VoiceState.Checking } }
        log("state=$state")
        assumeTrue("no offline Korean voice on this phone", state == VoiceState.Ready)
        if (InstrumentationRegistry.getArguments().getString("speak") == "true") {
            val start = System.currentTimeMillis()
            withTimeout(10_000) { voice.speak("물") }
            log("spoke ms=${System.currentTimeMillis() - start}")
        }
        voice.release()
    }
}
