package com.dero.juststudykorean.speechpoc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Debug
import android.os.SystemClock
import android.speech.RecognitionListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer as MlKitRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import java.io.File
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "SpeechPoC"
private val KOREAN = Locale.KOREA

/** PoC 4, step 1 (plan §2): availability of engines B and A for ko-KR, without speaking or downloading. */
class PocActivity : ComponentActivity() {
    private val lines = mutableStateListOf<String>()
    private var engine by mutableStateOf("B")
    private var session by mutableStateOf("main")
    private var index by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("")
    private val prefs by lazy { getSharedPreferences("poc", MODE_PRIVATE) }
    private val csv by lazy { File(filesDir, "results.csv") }
    private val warm = mutableSetOf<String>()
    private var recognizerB: SpeechRecognizer? = null
    private var clientA: MlKitRecognizer? = null
    private var pendingItem: Item? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) pendingItem?.let(::listen) else status = "Microphone not granted" }

    private fun loadIndex() = prefs.getInt("idx_${engine}_$session", 0)
    private fun saveIndex() = prefs.edit().putInt("idx_${engine}_$session", index).apply()

    private fun log(line: String) {
        Log.i(TAG, line)
        lines += line
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selfCheck()
        index = loadIndex()
        setContent {
            MaterialTheme {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Row { for (e in listOf("B", "A")) FilterChip(engine == e, { if (!busy) { engine = e; index = loadIndex() } }, { Text("Engine $e") }) }
                    Row { for (x in listOf("main", "airplane", "reboot")) FilterChip(session == x, { if (!busy) { session = x; index = loadIndex() } }, { Text(x) }) }
                    val list = items(session)
                    val item = list.getOrNull(index)
                    if (item == null) Text("Session done (${list.size}).") else {
                        Text("${index + 1} / ${list.size} · ${item.kind} · ${item.condition} #${item.rep}")
                        Text(conditionHelp(item.condition))
                        Text(item.spoken, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        if (item.kind == "error") Text("(on purpose: a different word from ${item.expected})")
                        Row {
                            Button({ listen(item) }, enabled = !busy) { Text(if (busy) "Listening…" else "Listen") }
                            OutlinedButton({ stopA() }, enabled = busy && engine == "A") { Text("Stop") }
                            OutlinedButton({ index = (index - 1).coerceAtLeast(0); saveIndex() }, enabled = !busy) { Text("Back") }
                        }
                    }
                    Text(status)
                    Button(::downloadB) { Text("Download B (ko-KR)") }
                    Button(::downloadA) { Text("Download A Basic (ko-KR)") }
                    Button({ checkB(); checkA() }) { Text("Check again") }
                    Text(lines.joinToString("\n"))
                }
            }
        }
        checkB()
        checkA()
    }

    private fun checkB() {
        val services = packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        log("B services: " + services.joinToString { it.serviceInfo.packageName + "/" + it.serviceInfo.name }.ifEmpty { "none" })
        log("B recognitionAvailable=${SpeechRecognizer.isRecognitionAvailable(this)} onDeviceAvailable=${SpeechRecognizer.isOnDeviceRecognitionAvailable(this)}")
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        recognizer.checkRecognitionSupport(koIntent(), mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(s: RecognitionSupport) {
                val ko = KOREAN.toLanguageTag()
                val state = when {
                    s.installedOnDeviceLanguages.any { it.startsWith("ko") } -> "INSTALLED"
                    s.pendingOnDeviceLanguages.any { it.startsWith("ko") } -> "PENDING"
                    s.supportedOnDeviceLanguages.any { it.startsWith("ko") } -> "DOWNLOADABLE"
                    else -> "NOT SUPPORTED on device"
                }
                log("B $ko: $state")
                log("B installed=${s.installedOnDeviceLanguages}")
                log("B pending=${s.pendingOnDeviceLanguages}")
                log("B supported=${s.supportedOnDeviceLanguages.size} langs, online=${s.onlineLanguages.size} langs (ko online: ${s.onlineLanguages.any { it.startsWith("ko") }})")
                recognizer.destroy()
            }

            override fun onError(error: Int) {
                log("B checkRecognitionSupport error=$error")
                recognizer.destroy()
            }
        })
    }

    private fun checkA() = lifecycleScope.launch {
        for ((name, mode) in listOf("Basic" to SpeechRecognizerOptions.Mode.MODE_BASIC, "Advanced" to SpeechRecognizerOptions.Mode.MODE_ADVANCED)) {
            val options = SpeechRecognizerOptions.builder().apply { locale = KOREAN; preferredMode = mode }.build()
            try {
                SpeechRecognition.getClient(options).use { log("A $name ko-KR: ${status(it.checkStatus())}") }
            } catch (e: Exception) {
                log("A $name ko-KR: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    private fun listen(item: Item) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingItem = item
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        busy = true
        status = "Listening…"
        if (engine == "B") listenB(item) else listenA(item)
    }

    /** One CSV row per utterance: the synthetic phrase, the condition, the transcript and times. Never audio. */
    private fun record(item: Item, cold: Boolean, heard: List<String>, conf: String, startupMs: Long, latencyMs: Long, error: String) {
        val top = heard.firstOrNull().orEmpty()
        val exact = normalize(top) == normalize(item.expected)
        val rank = heard.indexOfFirst { normalize(it) == normalize(item.expected) } + 1 // 0 = not among the candidates
        val pss = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
        val cer = if (top.isEmpty()) 1.0 else cer(item.expected, top)
        if (!csv.exists()) csv.writeText("engine,session,kind,condition,rep,expected,spoken,transcript,candidates,confidence,exact,cer,rank,cold,startup_ms,latency_ms,pss_kb,error\n")
        val row = listOf(engine, session, item.kind, item.condition, item.rep, item.expected, item.spoken, top, heard.joinToString("|"), conf, exact, "%.3f".format(cer), rank, cold, startupMs, latencyMs, pss, error)
        csv.appendText(row.joinToString(",") { "\"" + it.toString().replace("\"", "\"\"") + "\"" } + "\n")
        status = "\"$top\" · exact=$exact · cer=${"%.2f".format(cer)} · ${latencyMs} ms" + if (error.isNotEmpty()) " · $error" else ""
        Log.i(TAG, "row ${item.kind}/${item.condition} exact=$exact cer=$cer latency=$latencyMs startup=$startupMs err=$error")
        index++; saveIndex(); busy = false
    }

    private fun listenB(item: Item) {
        val cold = warm.add("B")
        val t0 = SystemClock.elapsedRealtime()
        var ready = 0L
        var end = 0L
        val r = recognizerB ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also { recognizerB = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { ready = SystemClock.elapsedRealtime() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { end = SystemClock.elapsedRealtime() }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(error: Int) {
                record(item, cold, emptyList(), "", if (ready > 0) ready - t0 else -1, -1, "error=$error")
            }
            override fun onResults(results: Bundle) {
                val now = SystemClock.elapsedRealtime()
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val conf = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.joinToString("|").orEmpty()
                record(item, cold, heard, conf, ready - t0, if (end > 0) now - end else -1, "")
            }
        })
        r.startListening(koIntent().putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
    }

    // A gives no end-of-speech event: the end is the last audio level above half the loudest one (an estimate, said in the report).
    private fun listenA(item: Item) = lifecycleScope.launch {
        val cold = warm.add("A")
        val t0 = SystemClock.elapsedRealtime()
        val levels = mutableListOf<Pair<Long, Int>>()
        var text: String? = null
        var tFinal = 0L
        var error = ""
        try {
            val client = clientA ?: SpeechRecognition.getClient(
                SpeechRecognizerOptions.builder().apply { locale = KOREAN; preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC }.build()
            ).also { clientA = it }
            val request = SpeechRecognizerRequest.builder().apply { audioSource = AudioSource.fromMic() }.build()
            client.startRecognition(request).collect { r ->
                when (r) {
                    is SpeechRecognizerResponse.AudioLevelResponse -> levels += SystemClock.elapsedRealtime() to r.audioLevel
                    is SpeechRecognizerResponse.FinalTextResponse -> { text = r.text; tFinal = SystemClock.elapsedRealtime() }
                    is SpeechRecognizerResponse.ErrorResponse -> error = "error=${r.e.errorCode} ${r.e.message}"
                    else -> {}
                }
            }
        } catch (e: Exception) {
            error = "${e.javaClass.simpleName} ${e.message}"
        }
        val max = levels.maxOfOrNull { it.second } ?: 0
        val tEnd = levels.lastOrNull { it.second * 2 > max }?.first
        record(item, cold, listOfNotNull(text), "", (levels.firstOrNull()?.first ?: t0) - t0, if (tEnd != null && tFinal > 0) tFinal - tEnd else -1, error)
    }

    private fun stopA() { lifecycleScope.launch { clientA?.stopRecognition() } }

    private fun koIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, KOREAN.toLanguageTag())

    // API 33: the system shows its own screen to confirm the language pack; no result callback (that is API 34's listener).
    private fun downloadB() {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        recognizer.triggerModelDownload(koIntent())
        log("B triggerModelDownload(ko-KR) sent; press Check again when the system finishes")
        recognizer.destroy()
    }

    private fun downloadA() = lifecycleScope.launch {
        val options = SpeechRecognizerOptions.builder().apply { locale = KOREAN; preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC }.build()
        SpeechRecognition.getClient(options).use { client ->
            var lastMb = -10L
            client.download().collect { d ->
                when (d) {
                    is DownloadStatus.DownloadStarted -> log("A download started: ${d.bytesToDownload / 1_000_000} MB")
                    is DownloadStatus.DownloadProgress -> (d.totalBytesDownloaded / 1_000_000).let { mb -> if (mb >= lastMb + 10) { lastMb = mb; log("A download: $mb MB") } }
                    is DownloadStatus.DownloadCompleted -> log("A download completed")
                    is DownloadStatus.DownloadFailed -> log("A download failed: ${d.e.errorCode} ${d.e.message}")
                }
            }
        }
    }

    // genai-common FeatureStatus
    private fun status(s: Int) = when (s) { 0 -> "UNAVAILABLE"; 1 -> "DOWNLOADABLE"; 2 -> "DOWNLOADING"; 3 -> "AVAILABLE"; else -> "?$s" }
}
