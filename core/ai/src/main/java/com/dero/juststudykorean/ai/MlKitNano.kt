package com.dero.juststudykorean.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Gemini Nano through ML Kit's Prompt API (genai-prompt 1.0.0-beta5). One client for the app's life. */
@Singleton
class MlKitNano @Inject constructor() : NanoModel {
    private val client: GenerativeModel by lazy { Generation.getClient() }
    private var systemPrompt: Boolean? = null

    override suspend fun status(): NanoStatus = when (client.checkStatus()) {
        FeatureStatus.AVAILABLE -> NanoStatus.Available
        FeatureStatus.DOWNLOADABLE -> NanoStatus.Downloadable
        FeatureStatus.DOWNLOADING -> NanoStatus.Downloading(null)
        else -> NanoStatus.Unavailable
    }

    override fun download(): Flow<NanoStatus> = flow {
        var total = 0L
        client.download().collect { s ->
            when (s) {
                is DownloadStatus.DownloadStarted -> { total = s.bytesToDownload; emit(NanoStatus.Downloading(0)) }
                is DownloadStatus.DownloadProgress ->
                    emit(NanoStatus.Downloading(if (total > 0) (s.totalBytesDownloaded * 100 / total).toInt().coerceIn(0, 100) else null))
                is DownloadStatus.DownloadCompleted -> emit(NanoStatus.Available)
                is DownloadStatus.DownloadFailed -> emit(status())
            }
        }
    }

    override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?): String {
        val withSystem = systemPrompt ?: client.isSystemPromptAvailable().also { systemPrompt = it }
        val request = if (withSystem) {
            generateContentRequest(SystemInstruction(system), TextPart(prompt)) { maxOutputTokens = maxTokens; temperature?.let { this.temperature = it } }
        } else {
            generateContentRequest(TextPart("$system\n\n$prompt")) { maxOutputTokens = maxTokens; temperature?.let { this.temperature = it } }
        }
        return try {
            client.generateContent(request).candidates.firstOrNull()?.text.orEmpty()
        } catch (e: GenAiException) {
            if (e.errorCode == GenAiException.ErrorCode.BUSY) throw NanoBusy() else throw e
        }
    }
}
