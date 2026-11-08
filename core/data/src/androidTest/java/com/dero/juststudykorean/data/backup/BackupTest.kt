package com.dero.juststudykorean.data.backup

import android.content.Context
import android.net.Uri
import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.data.db.JustStudyDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Phase 2, task 5: `.jsbackup.zip` v1 import and export, with the synthetic backup (no personal data). */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "backup-test").apply { deleteRecursively(); mkdirs() }
    private lateinit var db: JustStudyDatabase
    private lateinit var importer: BackupImporter
    private lateinit var exporter: BackupExporter

    private val synthetic: File = File(dir, "synthetic.jsbackup.zip").also { out ->
        InstrumentationRegistry.getInstrumentation().context.assets.open("backup/synthetic.jsbackup.zip").use { i -> out.outputStream().use { i.copyTo(it) } }
    }
    private val manifestRows: Map<String, Int> = ZipFile(synthetic).use { z ->
        val tables = JSONObject(z.getInputStream(z.getEntry("manifest.json")).bufferedReader().readText()).getJSONObject("tables")
        tables.keys().asSequence().associateWith { tables.getJSONObject(it).getInt("rows") }
    }

    @Before
    fun open() {
        context.deleteDatabase(DB)
        File(context.filesDir, BackupFormat.FILES_DIR).deleteRecursively()
        db = Room.databaseBuilder(context, JustStudyDatabase::class.java, DB).setDriver(BundledSQLiteDriver()).build()
        importer = BackupImporter(context, db)
        exporter = BackupExporter(context, db)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(DB)
        dir.deleteRecursively()
    }

    private fun counts(): Map<String, Int> = runBlocking {
        db.useReaderConnection { c ->
            SCHEMA.keys.associateWith { t -> c.usePrepared("SELECT COUNT(*) FROM \"$t\"") { s -> s.step(); s.getLong(0).toInt() } }
        }
    }

    private fun import(file: File) = runBlocking { importer.import(Uri.fromFile(file)) }

    /** A copy of the synthetic backup with [change] applied to the bytes of one entry (manifest untouched). */
    private fun tampered(entry: String, change: (ByteArray) -> ByteArray): File = File(dir, "tampered.zip").also { out ->
        ZipFile(synthetic).use { z ->
            ZipOutputStream(out.outputStream()).use { zos ->
                for (e in z.entries()) {
                    val bytes = z.getInputStream(e).readBytes()
                    zos.putNextEntry(ZipEntry(e.name))
                    zos.write(if (e.name == entry) change(bytes) else bytes)
                    zos.closeEntry()
                }
            }
        }
    }

    @Test
    fun importsEveryRow() {
        val result = import(synthetic)
        assertEquals(ImportResult.Ok(tables = 18, rows = manifestRows.values.sum()), result)
        assertEquals(manifestRows, counts())
    }

    @Test
    fun roundTripIsIdentical() {
        import(synthetic)
        val out = File(dir, "export.jsbackup.zip")
        assertEquals(ExportResult.Ok(tables = 18, rows = manifestRows.values.sum()), runBlocking { exporter.export(Uri.fromFile(out)) })
        ZipFile(synthetic).use { a ->
            ZipFile(out).use { b ->
                for (table in SCHEMA.keys) {
                    val name = "tables/$table.jsonl"
                    assertArrayEquals(name, a.getInputStream(a.getEntry(name)).readBytes(), b.getInputStream(b.getEntry(name)).readBytes())
                }
            }
        }
    }

    @Test
    fun roundTripKeepsExtraFiles() {
        import(synthetic)
        val out = File(dir, "export.jsbackup.zip")
        runBlocking { exporter.export(Uri.fromFile(out)) }
        ZipFile(synthetic).use { a ->
            ZipFile(out).use { b ->
                val extras = a.entries().asSequence().map { it.name }.filter { it.startsWith("extracted/") || it.startsWith("pdfs/") }.toList()
                assertEquals(listOf("extracted/1.json", "pdfs/stored_name-1.pdf"), extras.sorted())
                for (name in extras) assertArrayEquals(name, a.getInputStream(a.getEntry(name)).readBytes(), b.getInputStream(b.getEntry(name)).readBytes())
                val ma = JSONObject(a.getInputStream(a.getEntry("manifest.json")).bufferedReader().readText())
                val mb = JSONObject(b.getInputStream(b.getEntry("manifest.json")).bufferedReader().readText())
                for (key in listOf("extracted", "pdfs")) assertEquals(key, ma.getJSONObject(key).toString(), mb.getJSONObject(key).toString())
            }
        }
    }

    @Test
    fun importTwiceDoesNotDuplicate() {
        import(synthetic)
        import(synthetic)
        assertEquals(manifestRows, counts())
    }

    @Test
    fun notABackupLeavesDbUntouched() {
        import(synthetic)
        val pdf = File(dir, "notes.pdf").apply { writeText("%PDF-1.7\n%âãÏÓ\n1 0 obj << >> endobj\n") }
        assertEquals(ImportResult.Failed(BackupError.NotABackup), import(pdf))
        val noManifest = File(dir, "other.zip").also { f ->
            ZipOutputStream(f.outputStream()).use { it.putNextEntry(ZipEntry("hello.txt")); it.write("hi".toByteArray()); it.closeEntry() }
        }
        assertEquals(ImportResult.Failed(BackupError.NotABackup), import(noManifest))
        assertEquals(manifestRows, counts())
    }

    @Test
    fun checksumMismatchRollsBack() {
        import(synthetic)
        val changed = tampered("tables/documents.jsonl") { String(it).replaceFirst("original_name-1", "original_name-9").toByteArray() }
        assertEquals(ImportResult.Failed(BackupError.ChecksumMismatch), import(changed))
        assertEquals(manifestRows, counts())
    }

    private companion object { const val DB = "backup-test.db" }
}
