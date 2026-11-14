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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
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
                Text(lines.joinToString("\n"), Modifier.padding(16.dp).verticalScroll(rememberScrollState()))
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
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, KOREAN.toLanguageTag())
        recognizer.checkRecognitionSupport(intent, mainExecutor, object : RecognitionSupportCallback {
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

    // genai-common FeatureStatus
    private fun status(s: Int) = when (s) { 0 -> "UNAVAILABLE"; 1 -> "DOWNLOADABLE"; 2 -> "DOWNLOADING"; 3 -> "AVAILABLE"; else -> "?$s" }
}
