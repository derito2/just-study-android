package com.dero.juststudykorean.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserPrefsTest {
    @get:Rule val tmp = TemporaryFolder()

    /** A UserPrefs on [file]; the scope is cancelled so a second instance can open the same file. */
    private fun <T> withPrefs(file: File, block: suspend (UserPrefs) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            return runBlocking { block(UserPrefs(store)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun defaultsAreSystemKoToEnFalse() {
        withPrefs(File(tmp.root, "prefs.preferences_pb")) { prefs ->
            assertEquals(ThemeMode.System, prefs.themeMode.first())
            assertEquals(CardDirection.KoToEn, prefs.cardDirection.first())
            assertEquals(false, prefs.typeAnswer.first())
        }
    }

    @Test
    fun themeModePersists() {
        val file = File(tmp.root, "prefs.preferences_pb")
        withPrefs(file) { it.setThemeMode(ThemeMode.Dark) }
        assertEquals(ThemeMode.Dark, withPrefs(file) { it.themeMode.first() })
    }
}
