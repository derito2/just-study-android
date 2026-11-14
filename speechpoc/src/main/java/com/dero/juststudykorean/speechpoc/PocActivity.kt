package com.dero.juststudykorean.speechpoc

import android.content.Intent
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

    private fun log(line: String) {
        Log.i(TAG, line)
        lines += line
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    // The owner presses these, with network: the system does the download (plan §2, step 1).
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
