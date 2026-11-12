package com.dero.juststudykorean.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Nano's state for the whole app (Settings → On-device AI and every AI part of a screen). */
@Singleton
class NanoState @Inject constructor(private val model: NanoModel) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow<NanoStatus>(NanoStatus.Unavailable)
    val status: StateFlow<NanoStatus> = _status.asStateFlow()
    private var downloading: Job? = null

    /** Asks AICore again (the app's start and Settings). Never starts a download. */
    fun refresh() {
        if (downloading?.isActive == true) return
        scope.launch { check() }
    }

    internal suspend fun check() {
        _status.value = try { model.status() } catch (e: CancellationException) { throw e } catch (e: Exception) { NanoStatus.Unavailable }
    }

    /** Only from the Download button: AICore downloads the model (the app stays without internet). */
    fun download() {
        if (downloading?.isActive == true || _status.value != NanoStatus.Downloadable) return
        Log.i("JustStudy", "ai download requested by the user")
        downloading = scope.launch {
            try {
                model.download().collect { _status.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("JustStudy", "ai download failed error=${e.javaClass.simpleName}")
            }
            check()
        }
    }
}
