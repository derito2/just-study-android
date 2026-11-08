package com.dero.juststudykorean.data.backup

import android.content.Context
import android.net.Uri
import androidx.room3.useReaderConnection
import com.dero.juststudykorean.data.db.JustStudyDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** Writes every table to a `.jsbackup.zip` v1 at a SAF uri, streamed; the manifest goes last with the sha256s. */
class BackupExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: JustStudyDatabase,
) {
    suspend fun export(uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        try {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("cannot open")
            val tables = JSONObject()
            var total = 0
            ZipOutputStream(out.buffered()).use { zos ->
                db.useReaderConnection { c ->
                    for ((table, spec) in SCHEMA) {
                        val cols = spec.columns
                        val sorted = cols.withIndex().sortedBy { it.value.name } // Python sort_keys
                        val sql = "SELECT ${cols.joinToString { BackupFormat.quote(it.name) }} FROM ${BackupFormat.quote(table)} " +
                            "ORDER BY ${spec.primaryKey.joinToString { BackupFormat.quote(it) }}"
                        val digest = MessageDigest.getInstance("SHA-256")
                        zos.putNextEntry(ZipEntry(BackupFormat.tableEntry(table)))
                        val writer = DigestOutputStream(KeepOpen(zos), digest).bufferedWriter()
                        var rows = 0
                        c.usePrepared(sql) { st ->
                            while (st.step()) {
                                writer.write(sorted.joinToString(",", "{", "}\n") { (i, col) ->
                                    BackupFormat.pyString(col.name) + ":" + if (st.isNull(i)) "null" else when (col.kind) {
                                        Kind.INT -> st.getLong(i).toString()
                                        Kind.REAL -> BackupFormat.pyFloat(st.getDouble(i))
                                        Kind.TEXT -> BackupFormat.pyString(st.getText(i))
                                        Kind.BOOL -> if (st.getLong(i) != 0L) "true" else "false"
                                        Kind.DATETIME -> "\"" + BackupFormat.formatUtc(st.getLong(i)) + "\""
                                        Kind.JSON -> st.getText(i)
                                    }
                                })
                                rows++
                            }
                        }
                        writer.flush()
                        zos.closeEntry()
                        total += rows
                        tables.put(table, JSONObject().put("rows", rows).put("sha256", BackupFormat.sha256Hex(digest)).put("columns", JSONArray(cols.map { it.name })))
                    }
                }
                // extracted/ and pdfs/ from the last import, byte for byte, with their sha256 from that manifest.
                val dir = File(context.filesDir, BackupFormat.FILES_DIR)
                val index = File(dir, BackupFormat.FILES_INDEX).takeIf { it.isFile }?.let { JSONObject(it.readText()) }
                val extracted = index?.optJSONObject("extracted") ?: JSONObject()
                val pdfs = index?.optJSONObject("pdfs") ?: JSONObject()
                for ((map, entry) in listOf(extracted to BackupFormat::extractedEntry, pdfs to BackupFormat::pdfEntry)) {
                    for (key in map.keys().asSequence().sorted()) {
                        zos.putNextEntry(ZipEntry(entry(key)))
                        File(dir, entry(key)).inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                val manifest = JSONObject()
                    .put("format", BackupFormat.FORMAT).put("version", BackupFormat.VERSION)
                    .put("web_commit", JSONObject.NULL).put("alembic_head", JSONObject.NULL)
                    .put("exported_at", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
                    .put("source_time_zone", "UTC").put("tables", tables)
                    .put("extracted", extracted).put("pdfs", pdfs)
                zos.putNextEntry(ZipEntry(BackupFormat.MANIFEST))
                zos.write(manifest.toString(1).toByteArray())
                zos.closeEntry()
            }
            ExportResult.Ok(tables = SCHEMA.size, rows = total)
        } catch (e: Exception) {
            ExportResult.Failed(e.javaClass.simpleName) // no file content in messages or logs
        }
    }

    /** The table writer must not close the zip when it is done with its entry. */
    private class KeepOpen(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }
}
