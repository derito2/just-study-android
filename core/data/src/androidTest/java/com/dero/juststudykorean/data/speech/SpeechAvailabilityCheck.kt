package com.dero.juststudykorean.data.speech

import android.speech.SpeechRecognizer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** FASE_9_PLAN task 4: is Korean on-device recognition there (engine B)? Skipped without an engine. Never listens. */
@RunWith(AndroidJUnit4::class)
class SpeechAvailabilityCheck {
    @Test
    fun koreanOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("no on-device recognizer on this phone", SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
        val availability = withTimeout(10_000) { PlatformKoreanSpeech(context).availability() }
        Log.i("SpeechAvailabilityCheck", "availability=$availability")
        assumeTrue("Korean is not installed for on-device recognition", availability == SpeechAvailability.Ready)
    }
}
