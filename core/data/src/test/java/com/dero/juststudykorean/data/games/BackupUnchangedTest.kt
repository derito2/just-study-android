package com.dero.juststudykorean.data.games

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.backup.BackupExporter
import com.dero.juststudykorean.data.backup.ExportResult
import com.dero.juststudykorean.data.db.GameSessionsEntity
import com.dero.juststudykorean.data.golden.GoldenDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.dero.juststudykorean.data.db.QuestionWordsEntity
import com.dero.juststudykorean.data.db.WordStateEntity
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** FASE_8_PLAN 6 (D8-3): Games' tables are not in the backup; exporting golden/db gives the same tables as before. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupUnchangedTest : GoldenDb() {
    private fun sha(zip: File): Map<String, String> = ZipFile(zip).use { z ->
        z.entries().toList().filter { it.name.startsWith("tables/") }.associate { e ->
            e.name to MessageDigest.getInstance("SHA-256").digest(z.getInputStream(e).readBytes()).joinToString("") { "%02x".format(it) }
        }
    }

    @Test
    fun theExportedTablesKeepTheirSha256WithGamesRows() = runBlocking {
        db.gameSessionsDao().insert(GameSessionsEntity(1, 42, NOW.toEpochMilli(), null, 60, 3, 2, 2))
        // Phase 11 (H-03 B): word_state and question_words are phone-only too.
        db.wordStateDao().upsert(listOf(WordStateEntity("가방", 1, 2, 0, 0, 1, 1, null, false)))
        db.questionWordsDao().insert(listOf(QuestionWordsEntity(1, "가방")))
        val out = File(context.cacheDir, "export.jsbackup.zip")
        assertTrue(BackupExporter(context, db).export(Uri.fromFile(out)) is ExportResult.Ok)
        val exported = sha(out)
        assertEquals(18, exported.size)
        assertEquals(sha(file("fixture.jsbackup.zip")), exported)
    }
}
