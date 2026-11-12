package com.dero.juststudykorean.ai

import android.util.Log
import com.dero.juststudykorean.korean.ai.SYSTEM_PROMPT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The 8 uses of docs/PLAN.md 10.1 (plus the model's quiz questions) and how long each may take before the version
 * without the model is shown (PoC 2 p90 + margin, R-013 point 3).
 */
enum class AiUse(val timeoutMs: Long) {
    Judge(2_000), ExplainMistake(6_000), Questions(6_000), Teacher(6_000), Coach(3_000), MessyNotes(10_000), Grammar(6_000), Dashboard(3_000),
}

/**
 * One call: [prompt] in English, versioned ([promptVersion]); [input] is what the answer may use (the cache key).
 * [cache]: keep the validated answer in memory for the same version and input (never on disk, never in the backup).
 */
data class AiRequest(
    val use: AiUse,
    val promptVersion: String,
    val prompt: String,
    val input: String,
    val maxTokens: Int,
    val temperature: Float? = null,
    val cache: Boolean = false,
)

/** What the gate must know from the rest of the app: a PDF in the processing queue blocks the model. */
fun interface GateGuard {
    suspend fun pdfsQueued(): Boolean
}

/**
 * Whether the app is in the foreground: AICore blocks calls in the background. Counts started activities: when Android
 * starts a new MainActivity over the old one, the new onStart comes before the old onStop (seen on the OPPO, phase 7),
 * so a single flag ended up false with the app in front.
 */
object AppForeground {
    private val started = java.util.concurrent.atomic.AtomicInteger()
    fun started() { started.incrementAndGet() }
    fun stopped() { started.updateAndGet { maxOf(0, it - 1) } }
    /** Tests set it directly. */
    var visible: Boolean
        get() = started.get() > 0
        set(value) { started.set(if (value) 1 else 0) }
}

/**
 * The only door to Gemini Nano (R-013): one call at a time, 2 s between calls, one retry on BUSY, and no call at all
 * with a PDF in the queue, the app in the background or the model not Available. The answer is shown only if its
 * validator returns something; else null, and the caller shows its version without the model (no error message).
 * Logs: use, time and result only, never a prompt or an answer.
 */
@Singleton
class NanoGate internal constructor(
    private val model: NanoModel,
    private val state: NanoState,
    private val guard: GateGuard,
    private val now: () -> Long,
    private val pause: suspend (Long) -> Unit,
    private val foreground: () -> Boolean,
    private val timeouts: (AiUse) -> Long,
) {
    @Inject constructor(model: NanoModel, state: NanoState, guard: GateGuard) :
        this(model, state, guard, System::currentTimeMillis, { delay(it) }, { AppForeground.visible }, { it.timeoutMs })

    private val lock = Mutex()
    private var lastEnd = 0L
    private val cache = ConcurrentHashMap<String, String>()

    /** The last call's result (ok, rejected, timeout, busy…), for the phone's measurements; never any text. */
    @Volatile var lastResult = ""
        private set

    private fun key(r: AiRequest) = r.promptVersion + ":" + MessageDigest.getInstance("SHA-256").digest(r.input.toByteArray()).joinToString("") { "%02x".format(it) }

    /** True if the model could be used now (the screens show their AI parts only then). */
    fun ready() = state.status.value == NanoStatus.Available

    suspend fun <T : Any> ask(request: AiRequest, validate: (String) -> T?): T? {
        val start = now()
        fun log(result: String) {
            lastResult = result
            Log.i("JustStudy", "ai use=${request.use.name} v=${request.promptVersion} ms=${now() - start} result=$result")
        }
        if (request.cache) cache[key(request)]?.let { cached -> validate(cached)?.let { log("cached"); return it } }
        if (!ready()) return null.also { log("unavailable") }
        if (!foreground()) return null.also { log("background") }
        if (guard.pdfsQueued()) return null.also { log("pdf_queued") }
        val text = try {
            lock.withLock { call(request) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null.also { log(if (e is NanoBusy) "busy" else "error:${e.javaClass.simpleName}") } // an AICore error mid-answer: the version without the model
        } ?: return null.also { log("timeout") }
        val value = validate(text) ?: return null.also { log("rejected") }
        if (request.cache) cache[key(request)] = text
        log("ok")
        return value
    }

    /**
     * Under the lock: waits for the 2 s since the last call, and retries once on BUSY. The use's deadline counts only
     * the model's answer, not that wait (on the OPPO, a judge right after another call always ran out). Null: too late.
     */
    private suspend fun call(request: AiRequest): String? {
        var busy = false
        while (true) {
            val wait = lastEnd + GAP_MS - now()
            if (wait > 0) pause(wait)
            try {
                return withTimeoutOrNull(timeouts(request.use)) { model.generate(SYSTEM_PROMPT, request.prompt, request.maxTokens, request.temperature) }
            } catch (e: NanoBusy) {
                if (busy) throw e
                busy = true
            } finally {
                lastEnd = now()
            }
        }
    }

    companion object {
        const val GAP_MS = 2_000L
    }
}
