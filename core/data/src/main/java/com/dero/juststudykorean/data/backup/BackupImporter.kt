package com.dero.juststudykorean.data.backup

import android.content.Context
import android.net.Uri
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteStatement
import com.dero.juststudykorean.data.db.JustStudyDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Imports a `.jsbackup.zip` v1 chosen with SAF. First pass: manifest and the sha256 of every table, without
 * writing. Second pass: everything is replaced in one write transaction; any error rolls it all back.
 */
class BackupImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: JustStudyDatabase,
) {
    private class Bad(val error: BackupError) : Exception(error.name)

    /** [files]: zip entry → sha256 for extracted/ and pdfs/; [index]: the manifest's two maps, as they came. */
    private class Manifest(val rows: Map<String, Int>, val files: Map<String, String>, val index: JSONObject)

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val manifest = try {
            verify(uri)
        } catch (e: Bad) {
            return@withContext ImportResult.Failed(e.error)
        } catch (e: IOException) {
            return@withContext ImportResult.Failed(BackupError.NotABackup) // not a zip, or unreadable
        }
        // extracted/ and pdfs/ go to a staging folder; it replaces the old one only if the transaction commits.
        val dir = File(context.filesDir, BackupFormat.FILES_DIR)
        val staging = File(context.filesDir, BackupFormat.FILES_DIR + ".new").apply { deleteRecursively(); mkdirs() }
        try {
            db.withWriteTransaction {
                for (table in SCHEMA.keys) usePrepared("DELETE FROM ${BackupFormat.quote(table)}") { it.step() }
                // Phone-only tables: the notes index is rebuilt from the new extracted/, and no PDF is half processed.
                for (table in listOf("notes_fts", "processing_jobs")) usePrepared("DELETE FROM $table") { it.step() }
                zip(uri) { zis ->
                    while (true) {
                        val name = zis.nextEntry?.name ?: break
                        if (name in manifest.files) {
                            File(staging, name).apply { parentFile!!.mkdirs() }.outputStream().use { zis.copyTo(it) }
                            continue
                        }
                        val table = BackupFormat.tableOf(name) ?: continue
                        val cols = SCHEMA.getValue(table).columns
                        val sql = "INSERT INTO ${BackupFormat.quote(table)} (${cols.joinToString { BackupFormat.quote(it.name) }}) " +
                            "VALUES (${cols.joinToString { "?" }})"
                        var rows = 0
                        usePrepared(sql) { st ->
                            zis.bufferedReader().lineSequence().forEach { line ->
                                bindRow(st, cols, line)
                                st.step(); st.reset(); st.clearBindings()
                                rows++
                            }
                        }
                        if (rows != manifest.rows.getValue(table)) throw Bad(BackupError.Corrupt)
                    }
                }
            }
            File(staging, BackupFormat.FILES_INDEX).writeText(manifest.index.toString())
            dir.deleteRecursively()
            if (!staging.renameTo(dir)) throw IOException("cannot move the backup files")
            ImportResult.Ok(tables = manifest.rows.size, rows = manifest.rows.values.sum())
        } catch (e: Bad) {
            ImportResult.Failed(e.error)
        } catch (e: Exception) {
            ImportResult.Failed(BackupError.Corrupt) // a bad row or value: the transaction was rolled back
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Reads the whole zip once without writing: manifest, tables and their sha256. */
    private fun verify(uri: Uri): Manifest {
        var manifestText: String? = null
        val shas = mutableMapOf<String, String>()
        val others = mutableMapOf<String, String>() // extracted/, pdfs/ and anything else
        zip(uri) { zis ->
            while (true) {
                val name = zis.nextEntry?.name ?: break
                if (name == BackupFormat.MANIFEST) {
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zis.read(buffer).takeIf { it >= 0 } ?: break
                        bytes.write(buffer, 0, n)
                        if (bytes.size() > BackupFormat.MAX_MANIFEST_BYTES) throw Bad(BackupError.NotABackup)
                    }
                    manifestText = bytes.toString(Charsets.UTF_8.name())
                } else {
                    val digest = MessageDigest.getInstance("SHA-256")
                    DigestInputStream(zis, digest).copyTo(Discard) // InputStream.nullOutputStream is API 33; minSdk is 31
                    val sha = BackupFormat.sha256Hex(digest)
                    BackupFormat.tableOf(name)?.let { shas[it] = sha } ?: run { others[name] = sha }
                }
            }
        }
        val json = try {
            JSONObject(manifestText ?: throw Bad(BackupError.NotABackup))
        } catch (e: JSONException) {
            throw Bad(BackupError.NotABackup)
        }
        if (json.optString("format") != BackupFormat.FORMAT) throw Bad(BackupError.NotABackup)
        if (json.optInt("version") != BackupFormat.VERSION) throw Bad(BackupError.WrongVersion)
        if (json.optString("source_time_zone") != "UTC") throw Bad(BackupError.Corrupt)
        val tables = json.optJSONObject("tables") ?: throw Bad(BackupError.Corrupt)
        val listed = tables.keys().asSequence().toSet()
        if (listed != SCHEMA.keys || shas.keys != SCHEMA.keys) throw Bad(BackupError.Corrupt) // missing or unknown table
        val rows = linkedMapOf<String, Int>()
        for ((table, spec) in SCHEMA) {
            val info = tables.getJSONObject(table)
            val columns = info.getJSONArray("columns").let { a -> List(a.length()) { a.getString(it) }.toSet() }
            if (columns != spec.columns.map { it.name }.toSet()) throw Bad(BackupError.Corrupt)
            if (shas[table] != info.optString("sha256")) throw Bad(BackupError.ChecksumMismatch)
            rows[table] = info.getInt("rows")
        }
        // extracted/<document id>.json and pdfs/<name>: every one listed with its sha256, nothing unlisted.
        val extracted = json.optJSONObject("extracted") ?: JSONObject()
        val pdfs = json.optJSONObject("pdfs") ?: JSONObject()
        val files = mutableMapOf<String, String>()
        for ((map, entry) in listOf(extracted to BackupFormat::extractedEntry, pdfs to BackupFormat::pdfEntry)) {
            for (key in map.keys()) {
                if (!BackupFormat.isSafeName(key)) throw Bad(BackupError.Corrupt)
                files[entry(key)] = map.getString(key)
            }
        }
        if (others.keys != files.keys) throw Bad(BackupError.Corrupt)
        if (others != files) throw Bad(BackupError.ChecksumMismatch)
        return Manifest(rows, files, JSONObject().put("extracted", extracted).put("pdfs", pdfs))
    }

    private inline fun <T> zip(uri: Uri, block: (ZipInputStream) -> T): T {
        val input: InputStream = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open")
        return ZipInputStream(input.buffered()).use(block)
    }

    private fun bindRow(st: SQLiteStatement, cols: List<Col>, line: String) {
        val obj = Json.parseToJsonElement(line).jsonObject
        require(obj.keys == cols.map { it.name }.toSet()) { "columns differ in a row" }
        cols.forEachIndexed { i, col ->
            val index = i + 1
            val value = obj.getValue(col.name)
            if (value is JsonNull) {
                st.bindNull(index)
                return@forEachIndexed
            }
            val p = value as? JsonPrimitive
            when (col.kind) {
                Kind.INT -> st.bindLong(index, p!!.long)
                Kind.REAL -> st.bindDouble(index, p!!.double)
                Kind.TEXT -> st.bindText(index, p!!.also { require(it.isString) { "not text" } }.content)
                Kind.BOOL -> st.bindLong(index, if (p!!.boolean) 1L else 0L)
                Kind.DATETIME -> st.bindLong(index, BackupFormat.parseUtc(p!!.also { require(it.isString) }.content))
                Kind.JSON -> st.bindText(index, BackupFormat.pyJson(value))
            }
        }
    }

    private object Discard : OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }
}
