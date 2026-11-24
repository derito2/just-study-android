package com.dero.juststudykorean.data.backup

import android.net.Uri
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.MIGRATION_1_2
import com.dero.juststudykorean.data.db.MIGRATION_2_3
import com.dero.juststudykorean.data.db.MIGRATION_3_4
import com.dero.juststudykorean.data.db.MIGRATION_4_5
import com.dero.juststudykorean.data.db.MIGRATION_5_6
import com.dero.juststudykorean.data.golden.GoldenDb
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * FASE_10 PR G: MIGRATION_4_5 (Games' tables) on the phone's own SQLite, which the JVM test can't prove. A database filled from the
 * golden fixture is taken back to v4 (the three game tables dropped, user_version 4: exactly what v4 was) and opened by Room again:
 * it must migrate to v5 keeping the 18 backup tables row for row, sha256 for sha256, and create the three game tables.
 * Synthetic data only. Run with `am instrument -e class ...MigrationV4ToV5Check` (never connected*).
 */
@RunWith(AndroidJUnit4::class)
class MigrationV4ToV5Check {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "mig45").apply { deleteRecursively(); mkdirs() }
    private val dbFile = File(dir, "juststudy.db")

    private fun open() = Room.databaseBuilder(context, JustStudyDatabase::class.java, dbFile.path)
        .setDriver(BundledSQLiteDriver()).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()

    private fun tables(db: JustStudyDatabase, name: String): JSONObject = runBlocking {
        val zip = File(dir, "$name.jsbackup.zip")
        assertTrue(BackupExporter(context, db).export(Uri.fromFile(zip)) is ExportResult.Ok)
        ZipFile(zip).use { z -> JSONObject(z.getInputStream(z.getEntry(BackupFormat.MANIFEST)).bufferedReader().readText()).getJSONObject("tables") }
    }

    private fun raw(sql: String): Long = BundledSQLiteDriver().open(dbFile.path).let { c ->
        try { c.prepare(sql).use { it.step(); it.getLong(0) } } finally { c.close() }
    }

    @Test
    fun v4ToV5KeepsTheBackupTablesAndCreatesTheGameOnes() {
        val before: JSONObject
        runBlocking {
            val db = open()
            try {
                assertTrue(BackupImporter(context, db).import(Uri.fromFile(GoldenDb.file("fixture.jsbackup.zip"))) is ImportResult.Ok)
                before = tables(db, "before")
            } finally { db.close() }
        }
        // Back to v4: Games' tables did not exist, and user_version said 4.
        BundledSQLiteDriver().open(dbFile.path).let { c ->
            try {
                for (t in listOf("game_rounds", "game_sessions", "game_item_progress")) c.prepare("DROP TABLE $t").use { it.step() }
                c.prepare("PRAGMA user_version = 4").use { it.step() }
            } finally { c.close() }
        }
        assertEquals(4L, raw("PRAGMA user_version"))
        val db = open() // Room migrates here, with this phone's SQLite
        try {
            val after = tables(db, "after")
            File(dir, "result.txt").writeText(SCHEMA.keys.joinToString("\n") { t ->
                "$t before=${before.getJSONObject(t).getInt("rows")}/${before.getJSONObject(t).getString("sha256").take(12)} after=${after.getJSONObject(t).getInt("rows")}/${after.getJSONObject(t).getString("sha256").take(12)}"
            } + "\n")
            for (t in SCHEMA.keys) assertEquals(t, before.getJSONObject(t).toString(), after.getJSONObject(t).toString())
        } finally { db.close() }
        assertEquals(6L, raw("PRAGMA user_version")) // and on to v6 (phase 11), which only adds the word tables
        for (t in listOf("game_sessions", "game_rounds", "game_item_progress")) assertEquals(t, 0L, raw("SELECT COUNT(*) FROM $t"))
    }
}
