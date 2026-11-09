package com.dero.juststudykorean.ui.testing

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.dero.juststudykorean.data.backup.BackupImporter
import com.dero.juststudykorean.data.backup.ImportResult
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.time.FixedClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The real repositories on golden/db/fixture (synthetic notes, fixed clock) in an in-memory Room, for the
 * ViewModels' behaviour tests. Close it at the end.
 */
class GoldenApp {
    val context: Context = ApplicationProvider.getApplicationContext()
    val zone: ZoneId = ZoneId.of("America/Mexico_City")
    val clock = FixedClock(ZonedDateTime.of(2026, 11, 8, 18, 30, 0, 0, zone).toInstant(), zone)
    val db: JustStudyDatabase = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
    private val prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val prefs = UserPrefs(PreferenceDataStoreFactory.create(scope = prefsScope) { File(Files.createTempDirectory("prefs").toFile(), "p.preferences_pb") })
    val progress = ProgressRepository(db, clock)
    val notes = NotesSource(context)
    val flashcards = FlashcardRepository(db, clock, progress, notes)
    val quiz = QuizRepository(db, clock, progress, notes)

    init {
        val r = runBlocking { BackupImporter(context, db).import(Uri.fromFile(File("../golden/db/fixture.jsbackup.zip"))) }
        check(r is ImportResult.Ok) { "import: $r" }
    }

    fun close() { prefsScope.cancel(); db.close() }
}

/** Runs the main looper (viewModelScope) until [done], waiting for the IO work in between. */
fun idleUntil(timeoutMs: Long = 10_000, done: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        shadowOf(Looper.getMainLooper()).idle()
        if (done()) return
        Thread.sleep(10)
    }
    error("timed out")
}

