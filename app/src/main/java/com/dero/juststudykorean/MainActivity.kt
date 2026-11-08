package com.dero.juststudykorean

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// shortcut: PoC 2 screen (branch poc/2-nano only) to download Gemini Nano through ML Kit;
// phase 2 replaces it with Compose + the design system. AICore (system app) downloads; this
// app has no INTERNET permission.
class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var model: GenerativeModel
    private lateinit var status: TextView
    private lateinit var message: TextView
    private lateinit var progress: ProgressBar
    private lateinit var download: Button
    private var downloading: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = Generation.getClient()
        val pad = (16 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 18f }
        message = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, pad) }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; visibility = View.GONE
        }
        download = Button(this).apply { text = "Download Gemini Nano"; setOnClickListener { startDownload() } }
        val check = Button(this).apply { text = "Check again"; setOnClickListener { refresh() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 6, pad, pad)
            addView(TextView(context).apply { setText(R.string.app_name); textSize = 24f })
            listOf(status, progress, message, download, check).forEach(::addView)
        })
        refresh()
    }

    private fun ui(block: () -> Unit) = runOnUiThread(block)

    private fun name(code: Int) = when (code) {
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.DOWNLOADING -> "DOWNLOADING"
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> "UNKNOWN ($code)"
    }

    private fun refresh() {
        scope.launch {
            val text = try {
                name(model.checkStatus())
            } catch (e: GenAiException) {
                "error ${e.errorCode}"
            }
            Log.i(TAG, "checkStatus=$text")
            ui {
                status.text = "Gemini Nano: $text"
                download.isEnabled = text == "DOWNLOADABLE" && downloading?.isActive != true
            }
        }
    }

    private fun startDownload() {
        if (downloading?.isActive == true) return
        download.isEnabled = false
        message.text = "Starting download…"
        Log.i(TAG, "download requested by the user")
        downloading = scope.launch {
            var total = 0L
            try {
                model.download().collect { s ->
                    when (s) {
                        is DownloadStatus.DownloadStarted -> {
                            total = s.bytesToDownload
                            Log.i(TAG, "download started bytes=$total")
                            ui {
                                progress.visibility = View.VISIBLE
                                message.text = "Downloading ${mb(total)} MB…"
                            }
                        }
                        is DownloadStatus.DownloadProgress -> {
                            val done = s.totalBytesDownloaded
                            Log.i(TAG, "download progress bytes=$done/$total")
                            ui {
                                if (total > 0) progress.progress = (done * 1000 / total).toInt()
                                message.text = "Downloading ${mb(done)} / ${mb(total)} MB"
                            }
                        }
                        is DownloadStatus.DownloadCompleted -> {
                            Log.i(TAG, "download completed")
                            ui { progress.progress = 1000; message.text = "Download completed." }
                        }
                        is DownloadStatus.DownloadFailed -> {
                            Log.w(TAG, "download failed code=${s.e.errorCode} ${s.e.javaClass.simpleName}: ${s.e.message}")
                            ui { message.text = "Download failed: error ${s.e.errorCode} (${s.e.message})" }
                        }
                    }
                }
            } catch (e: GenAiException) {
                Log.w(TAG, "download threw code=${e.errorCode}: ${e.message}")
                ui { message.text = "Download failed: error ${e.errorCode} (${e.message})" }
            } catch (e: Exception) {
                Log.w(TAG, "download threw ${e.javaClass.simpleName}: ${e.message}")
                ui { message.text = "Download failed: ${e.javaClass.simpleName} (${e.message})" }
            }
            refresh()
        }
    }

    private fun mb(bytes: Long) = bytes / (1024 * 1024)

    override fun onDestroy() {
        scope.cancel()
        model.close()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "NanoPoc"
    }
}
