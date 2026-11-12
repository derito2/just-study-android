package com.dero.juststudykorean.ai

import com.dero.juststudykorean.korean.ai.koreanOnlyFrom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R-013's acceptance, with a fake model and a virtual clock (docs/RECOMMENDATIONS.md, R-013). */
class NanoGateTest {
    private var clock = 1_000_000L
    private val calls = mutableListOf<Long>()

    private inner class Fake(var answer: String = "It means coffee.", var status: NanoStatus = NanoStatus.Available) : NanoModel {
        var busyTimes = 0
        var slowMs = 0L
        /** Not null: the call hangs until it completes and ignores cancellation (ML Kit on the OPPO, phase 7). */
        var hang: CompletableDeferred<Unit>? = null
        override suspend fun status() = status
        override fun download(): Flow<NanoStatus> = emptyFlow()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?): String {
            calls += clock
            if (busyTimes > 0) { busyTimes--; throw NanoBusy() }
            if (slowMs > 0) delay(slowMs) // real time: the gate's deadline is real
            hang?.let { h -> withContext(NonCancellable) { h.await() }; return "late" }
            clock += 500 // each answer takes 0.5 s of the virtual clock
            return answer
        }
    }

    private fun gate(model: Fake, queued: Boolean = false, foreground: Boolean = true, timeoutMs: Long = 5_000) = NanoGate(
        model, NanoState(model).also { runBlocking { it.check() } }, { queued }, { clock }, { clock += it }, { foreground }, { timeoutMs },
    )

    private val request = AiRequest(AiUse.Coach, "test.v1", "Say it.", input = "커피 = Coffee", maxTokens = 32)

    @Test
    fun twoCallsAreTwoSecondsApart() = runBlocking {
        val g = gate(Fake())
        g.ask(request) { it }
        g.ask(request) { it }
        assertEquals(2, calls.size)
        assertTrue("gap ${calls[1] - calls[0]}", calls[1] - calls[0] >= 2_000 + 500)
    }

    @Test
    fun aQueuedPdfMeansNoCall() = runBlocking {
        assertNull(gate(Fake(), queued = true).ask(request) { it })
        assertNull(gate(Fake(), foreground = false).ask(request) { it })
        assertNull(gate(Fake(status = NanoStatus.Downloadable)).ask(request) { it })
        assertEquals(0, calls.size)
    }

    @Test
    fun inventedKoreanNeverReachesTheScreen() = runBlocking {
        val shown = gate(Fake(answer = "Say 우유를 마셔요.")).ask(request) { out -> out.takeIf { koreanOnlyFrom(it, request.input) } }
        assertNull(shown)
        assertEquals("You can say 커피를.", gate(Fake(answer = "You can say 커피를.")).ask(request) { out -> out.takeIf { koreanOnlyFrom(it, request.input) } })
    }

    @Test
    fun aLateAnswerIsTheVersionWithoutTheModel() = runBlocking {
        val slow = Fake().apply { slowMs = 500 }
        assertNull(gate(slow, timeoutMs = 50).ask(request) { it })
    }

    /** The 2 s wait between calls doesn't count against the use's deadline (on the OPPO the judge always ran out). */
    @Test
    fun theWaitBetweenCallsIsNotPartOfTheDeadline() = runBlocking {
        val model = Fake()
        val g = NanoGate(model, NanoState(model).also { runBlocking { it.check() } }, { false }, { clock }, { delay(150); clock += it }, { true }, { 100 })
        assertEquals("It means coffee.", g.ask(request) { it })
        assertEquals("It means coffee.", g.ask(request) { it }) // waited 150 ms (real) for the gap, deadline 100 ms
    }

    /** A new MainActivity starts before the old one stops: the app is still in front (phase 7, seen on the OPPO). */
    @Test
    fun foregroundCountsActivities() {
        AppForeground.visible = false
        AppForeground.started() // the old one
        AppForeground.started() // the new one, before the old one's onStop
        AppForeground.stopped()
        assertTrue(AppForeground.visible)
        AppForeground.stopped()
        assertTrue(!AppForeground.visible)
    }

    /**
     * A call that never answers and ignores cancellation (phase 7): the gate gives up at the deadline, the next calls
     * never block (they wait for it with their own deadline), and its answer, if it comes, is ignored.
     */
    @Test
    fun aHungCallNeverBlocksTheGate() = runBlocking {
        val model = Fake().apply { hang = CompletableDeferred() }
        val g = gate(model, timeoutMs = 100)
        withTimeout(2_000) { assertNull(g.ask(request) { it }) }
        assertEquals("timeout", g.lastResult)
        withTimeout(2_000) { assertNull(g.ask(request) { it }) } // still hung: the version without the model
        assertEquals("stuck", g.lastResult)
        assertEquals(1, calls.size) // AICore is not asked again while it is busy with the old one
        val hung = model.hang!!
        model.hang = null
        hung.complete(Unit) // the old answer arrives late…
        assertEquals("It means coffee.", withTimeout(2_000) { g.ask(request) { it } }) // …and is not the one shown
    }

    @Test
    fun busyIsRetriedOnceAfterTheGap() = runBlocking {
        assertEquals("It means coffee.", gate(Fake().apply { busyTimes = 1 }).ask(request) { it })
        assertTrue(calls[1] - calls[0] >= 2_000)
        calls.clear()
        assertNull(gate(Fake().apply { busyTimes = 2 }).ask(request) { it })
        assertEquals(2, calls.size)
    }

    @Test
    fun cacheIsOnlyInMemoryAndPerVersion() = runBlocking {
        val model = Fake()
        val g = gate(model)
        val cached = request.copy(cache = true)
        g.ask(cached) { it }
        model.answer = "Other."
        assertEquals("It means coffee.", g.ask(cached) { it })
        assertEquals("Other.", g.ask(cached.copy(promptVersion = "test.v2")) { it })
        assertEquals(2, calls.size)
    }
}
