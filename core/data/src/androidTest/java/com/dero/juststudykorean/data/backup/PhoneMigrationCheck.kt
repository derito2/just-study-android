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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Checks a migration on the owner's phone without shared storage. Skipped unless copies of the app's database were put
 * in this test package's `files/phone-check/{before,after}/` (juststudy.db + -wal, via `run-as`): `before` from before
 * installing the new version, `after` once the app has migrated it. Both are exported with [BackupExporter]; every table
 * must keep its rows and sha256, `before` must have the same rows read raw (not opened by Room), and `after` must already
 * be at the new version. Writes only counts and hashes to `phone-check/result.txt`. Delete `phone-check/` when done.
 */
@RunWith(AndroidJUnit4::class)
class PhoneMigrationCheck {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.filesDir, "phone-check")

    /** Read without Room (which would migrate it): the schema version and the rows per table. */
    private fun raw(db: File): Pair<Long, Map<String, Int>> = BundledSQLiteDriver().open(db.path).let { c ->
        try {
            c.prepare("PRAGMA user_version").use { it.step(); it.getLong(0) } to
                SCHEMA.keys.associateWith { t -> c.prepare("SELECT COUNT(*) FROM \"$t\"").use { it.step(); it.getLong(0).toInt() } }
        } finally {
            c.close()
        }
    }

    private fun export(name: String): JSONObject = runBlocking {
        val db = Room.databaseBuilder(context, JustStudyDatabase::class.java, File(root, "$name/juststudy.db").path)
            .setDriver(BundledSQLiteDriver()).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        val zip = File(root, "$name.jsbackup.zip")
        try {
            assertEquals(BackupExporter(context, db).export(Uri.fromFile(zip)).javaClass, ExportResult.Ok::class.java)
        } finally {
            db.close()
        }
        ZipFile(zip).use { z -> JSONObject(z.getInputStream(z.getEntry(BackupFormat.MANIFEST)).bufferedReader().readText()).getJSONObject("tables") }
    }

    @Test
    fun sameRowsAndSha256() {
        assumeTrue(File(root, "before/juststudy.db").isFile && File(root, "after/juststudy.db").isFile)
        val (_, raw) = raw(File(root, "before/juststudy.db"))
        // The app itself migrated `after`: opened here, Room would migrate an old copy and the check would prove nothing.
        assertEquals("after: schema version", MIGRATION_3_4.endVersion.toLong(), raw(File(root, "after/juststudy.db")).first)
        val before = export("before")
        val after = export("after")
        File(root, "result.txt").writeText(SCHEMA.keys.joinToString("\n") { t ->
            val b = before.getJSONObject(t); val a = after.getJSONObject(t)
            "$t raw=${raw[t]} before=${b.getInt("rows")}/${b.getString("sha256").take(12)} after=${a.getInt("rows")}/${a.getString("sha256").take(12)}"
        } + "\n")
        for (t in SCHEMA.keys) {
            assertEquals("$t rows (raw)", raw.getValue(t), after.getJSONObject(t).getInt("rows"))
            assertEquals("$t", before.getJSONObject(t).toString(), after.getJSONObject(t).toString())
        }
    }
}
