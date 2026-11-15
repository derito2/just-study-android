package com.dero.juststudykorean.ui.testing

import com.dero.juststudykorean.data.speech.KoreanSpeech
import com.dero.juststudykorean.data.speech.SpeechAvailability
import com.dero.juststudykorean.data.speech.SpeechEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * FASE_9_PLAN 3: the speech engine in tests, by scripts of events. Each listen() plays the next script; [open] says
 * whether a microphone is open right now (to test every way it closes). A script with [HOLD] stays open until cancelled.
 */
class FakeKoreanSpeech(var available: SpeechAvailability = SpeechAvailability.Ready, vararg scripts: List<SpeechEvent>) : KoreanSpeech {
    private val queue = ArrayDeque(scripts.toList())
    var open = false; private set
    var listens = 0; private set

    fun script(vararg events: SpeechEvent) = queue.addLast(events.toList())

    override suspend fun availability() = available

    override fun listen(maxMs: Long): Flow<SpeechEvent> = flow {
        listens++
        open = true
        try {
            for (e in queue.removeFirstOrNull() ?: listOf(HOLD)) {
                if (e === HOLD) awaitCancellation()
                emit(e)
                delay(50)
            }
        } finally { open = false }
    }

    companion object {
        /** In a script: keep the microphone open (until the collection is cancelled). */
        val HOLD: SpeechEvent = SpeechEvent.Partial("\u0000")
    }
}
