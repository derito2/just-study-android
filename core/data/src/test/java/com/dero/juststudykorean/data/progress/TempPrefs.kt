package com.dero.juststudykorean.data.progress

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dero.juststudykorean.data.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.Files

/** A UserPrefs on a fresh temporary file; [close] stops its DataStore. */
class TempPrefs {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val prefs = UserPrefs(PreferenceDataStoreFactory.create(scope = scope) { File(Files.createTempDirectory("prefs").toFile(), "p.preferences_pb") })
    fun close() = scope.cancel()
}
