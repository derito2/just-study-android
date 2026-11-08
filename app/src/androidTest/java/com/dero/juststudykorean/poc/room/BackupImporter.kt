package com.dero.juststudykorean.poc.room

import android.os.Debug
import android.os.SystemClock
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteStatement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import org.json.JSONObject
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

data class ImportReport(val tables: Map<String, Int>, val millis: Long, val errors: List<String>, val pssPeakKb: Long = 0)

/** `.jsbackup.zip` v1 into Room: checks everything first, then writes in one transaction. */
object BackupImporter {
    private class BadBackup(message: String) : Exception(message)

    suspend fun import(zip: File, db: PocDatabase): ImportReport {
        val start = SystemClock.elapsedRealtime()
        var pss = Debug.getPss()
        val errors = mutableListOf<String>()
        val counts = linkedMapOf<String, Int>()
        ZipFile(zip).use { z ->
            val manifest = z.getEntry("manifest.json")?.let { JSONObject(z.getInputStream(it).bufferedReader().readText()) }
            if (manifest == null || manifest.optString("format") != "jsbackup" || manifest.optInt("version") != 1) {
                return ImportReport(emptyMap(), elapsed(start), listOf("not a .jsbackup.zip v1"), pss)
            }
            if (manifest.optString("source_time_zone") != "UTC") errors += "source time zone is not UTC"
            val tables = manifest.getJSONObject("tables")
            val listed = tables.keys().asSequence().toSet()
            val inZip = z.entries().asSequence().map { it.name }
                .filter { it.startsWith("tables/") && it.endsWith(".jsonl") }
                .map { it.removePrefix("tables/").removeSuffix(".jsonl") }.toSet()
            (inZip - listed).forEach { errors += "table in the zip but not in the manifest: $it" }
            (listed - inZip).forEach { errors += "table missing from the zip: $it" }
            (listed - SCHEMA.keys).forEach { errors += "unknown table: $it" }
            (SCHEMA.keys - listed).forEach { errors += "table missing from the manifest: $it" }
            for (table in listed intersect inZip intersect SCHEMA.keys) {
                val info = tables.getJSONObject(table)
                val columns = info.getJSONArray("columns").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                if (columns != SCHEMA.getValue(table).columns.map { it.name }.toSet()) errors += "columns differ: $table"
                // Pass 1: sha256 of every JSONL before writing anything (streamed).
                val digest = MessageDigest.getInstance("SHA-256")
                DigestInputStream(z.getInputStream(z.getEntry("tables/$table.jsonl")), digest).use { it.copyTo(NullSink) }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (sha != info.getString("sha256")) errors += "sha256 differs: $table"
            }
            if (errors.isNotEmpty()) return ImportReport(emptyMap(), elapsed(start), errors, pss)

            // Pass 2: one write transaction; any exception rolls everything back.
            try {
                db.withWriteTransaction {
                    for ((table, spec) in SCHEMA) {
                        val cols = spec.columns
                        val sql = "INSERT INTO ${RoomSql.quote(table)} (${cols.joinToString { RoomSql.quote(it.name) }}) " +
                            "VALUES (${cols.joinToString { "?" }})"
                        var rows = 0
                        usePrepared(sql) { st ->
                            z.getInputStream(z.getEntry("tables/$table.jsonl")).bufferedReader().useLines { lines ->
                                for (line in lines) {
                                    bindRow(st, table, cols, line)
                                    st.step(); st.reset(); st.clearBindings()
                                    rows++
                                }
                            }
                        }
                        val expected = tables.getJSONObject(table).getInt("rows")
                        if (rows != expected) throw BadBackup("rows differ: $table ($rows, manifest $expected)")
                        counts[table] = rows
                        pss = maxOf(pss, Debug.getPss())
                    }
                }
            } catch (e: Exception) {
                counts.clear()
                errors += "import rolled back: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
        return ImportReport(counts, elapsed(start), errors, maxOf(pss, Debug.getPss()))
    }

    private fun elapsed(start: Long) = SystemClock.elapsedRealtime() - start

    private fun bindRow(st: SQLiteStatement, table: String, cols: List<Col>, line: String) {
        val obj = Json.parseToJsonElement(line).jsonObject
        if (obj.keys != cols.map { it.name }.toSet()) throw BadBackup("columns differ in a row of $table")
        cols.forEachIndexed { i, col ->
            val index = i + 1
            val value = obj.getValue(col.name)
            if (value is JsonNull) {
                st.bindNull(index); return@forEachIndexed
            }
            val p = value as? JsonPrimitive
            when (col.kind) {
                Kind.INT -> st.bindLong(index, p!!.long)
                Kind.REAL -> st.bindDouble(index, p!!.double)
                Kind.TEXT -> st.bindText(index, p!!.also { require(it.isString) { "${col.name} is not text" } }.content)
                Kind.BOOL -> st.bindLong(index, if (p!!.boolean) 1L else 0L)
                Kind.DATETIME -> st.bindLong(index, RoomSql.parseUtc(p!!.also { require(it.isString) }.content))
                Kind.JSON -> st.bindText(index, RoomSql.pyJson(value))
            }
        }
    }

    private object NullSink : java.io.OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }
}
