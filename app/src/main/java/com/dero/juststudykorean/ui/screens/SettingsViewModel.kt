package com.dero.juststudykorean.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dero.juststudykorean.data.backup.BackupError
import com.dero.juststudykorean.data.backup.BackupExporter
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.backup.BackupImporter
import com.dero.juststudykorean.data.backup.ExportResult
import com.dero.juststudykorean.data.backup.ImportResult
import com.dero.juststudykorean.data.prefs.ThemeMode
import com.dero.juststudykorean.data.prefs.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.dero.juststudykorean.ai.NanoState
import javax.inject.Inject

/** Settings: theme and backup. Messages carry counts only, never note content. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPrefs,
    private val importer: BackupImporter,
    private val exporter: BackupExporter,
    private val indexer: DocumentIndexer,
    private val nanoState: NanoState,
) : ViewModel() {
    /** Gemini Nano (On-device AI): the download only starts with Download. */
    val nano = nanoState.status

    init { nanoState.refresh() }

    fun downloadNano() = nanoState.download()

    val themeMode = prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    /** The last failure, shown in an error box until the next attempt. */
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val snackbar = Channel<String>(Channel.BUFFERED)
    val messages = snackbar.receiveAsFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }

    fun import(uri: Uri) = run {
        when (val result = importer.import(uri)) {
            is ImportResult.Ok -> {
                indexer.rebuildIndex() // the notes index is not in the backup: rebuilt from its extracted/
                snackbar.send("Imported ${result.rows} rows from ${result.tables} tables.")
            }
            is ImportResult.Failed -> _error.value = result.reason.message() + " Nothing was changed."
        }
    }

    fun export(uri: Uri) = run {
        when (exporter.export(uri)) {
            is ExportResult.Ok -> snackbar.send("Backup saved.")
            is ExportResult.Failed -> _error.value = "Couldn't save the backup."
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        _error.value = null
        _busy.value = true
        viewModelScope.launch {
            try { block() } finally { _busy.value = false }
        }
    }

    private fun BackupError.message() = when (this) {
        BackupError.NotABackup -> "That file is not a Just Study backup."
        BackupError.WrongVersion -> "This backup is from another version of the app."
        BackupError.ChecksumMismatch -> "The backup is damaged: a table doesn't match its checksum."
        BackupError.Corrupt -> "The backup is damaged or incomplete."
    }
}
