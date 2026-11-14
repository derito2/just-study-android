package com.dero.juststudykorean.data.audio

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** 4.11: only a ko voice that needs no network and is installed; never synthesis over the network. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OfflineKoreanTest {
    private fun voice(name: String, locale: Locale, network: Boolean, quality: Int = Voice.QUALITY_NORMAL, features: Set<String> = emptySet()) =
        Voice(name, locale, quality, Voice.LATENCY_NORMAL, network, features)

    @Test
    fun picksTheBestOfflineKoreanVoice() {
        val voices = listOf(
            voice("ko-net", Locale.KOREA, network = true, quality = Voice.QUALITY_VERY_HIGH),
            voice("ko-missing", Locale.KOREA, network = false, quality = Voice.QUALITY_HIGH, features = setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)),
            voice("ko-low", Locale.KOREA, network = false, quality = Voice.QUALITY_LOW),
            voice("ko-normal", Locale.KOREA, network = false),
            voice("en", Locale.US, network = false, quality = Voice.QUALITY_VERY_HIGH),
        )
        assertEquals("ko-normal", offlineKorean(voices)?.name)
    }

    @Test
    fun noneWhenOnlyNetworkOrOtherLanguages() {
        assertNull(offlineKorean(listOf(voice("ko-net", Locale.KOREA, network = true), voice("ja", Locale.JAPAN, network = false))))
    }
}
