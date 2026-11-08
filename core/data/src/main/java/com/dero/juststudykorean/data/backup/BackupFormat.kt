package com.dero.juststudykorean.data.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * `.jsbackup.zip` v1 (docs/POC_PLAN.md, "Formato del respaldo"): `manifest.json` (format, version, tables with
 * rows, sha256 and columns) and one `tables/<table>.jsonl` per table, one row per line, written like Python's
 * `json.dumps(sort_keys=True, separators=(",", ":"), ensure_ascii=False)` so a round trip is byte-identical.
 */
object BackupFormat {
    const val FORMAT = "jsbackup"
    const val VERSION = 1
    const val MANIFEST = "manifest.json"
    const val MAX_MANIFEST_BYTES = 1 shl 20 // a manifest is a few KB; more means this is not one of ours

    fun tableEntry(table: String) = "tables/$table.jsonl"

    fun tableOf(entry: String): String? =
        entry.takeIf { it.startsWith("tables/") && it.endsWith(".jsonl") }?.removePrefix("tables/")?.removeSuffix(".jsonl")

    fun extractedEntry(documentId: String) = "extracted/$documentId.json"

    fun pdfEntry(name: String) = "pdfs/$name"

    /** A file name from the manifest that is safe to use as a path segment (no folders, no "..", no hidden files). */
    fun isSafeName(name: String) = name.isNotEmpty() && name.length <= 255 && !name.startsWith(".") && '/' !in name && '\\' !in name

    /** Folder (app private storage) with the backup's extracted/ and pdfs/ files, kept as they came. */
    const val FILES_DIR = "backup-files"

    /** In [FILES_DIR]: the manifest's `extracted` and `pdfs` maps (name → sha256), to write them back on export. */
    const val FILES_INDEX = "files.json"

    fun sha256Hex(digest: MessageDigest): String = digest.digest().joinToString("") { "%02x".format(it) }

    fun quote(name: String) = "\"" + name.replace("\"", "\"\"") + "\""

    // ---- dates: UTC only, "2026-01-01T12:30:00Z" or with 6 decimals
    private val utcIso = Regex("""^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d{6})?Z$""")
    private val seconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC)

    fun parseUtc(text: String): Long {
        require(utcIso.matches(text)) { "date not in UTC ISO-8601 with Z" }
        return Instant.parse(text).toEpochMilli()
    }

    fun formatUtc(millis: Long): String {
        val frac = Math.floorMod(millis, 1000L)
        val base = seconds.format(Instant.ofEpochMilli(millis - frac))
        return if (frac == 0L) "${base}Z" else "$base.%06dZ".format(frac * 1000)
    }

    // ---- Python json.dumps
    fun pyString(s: String): String {
        val out = StringBuilder(s.length + 2).append('"')
        for (ch in s) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000c' -> out.append("\\f")
                else -> if (ch < ' ') out.append("\\u%04x".format(ch.code)) else out.append(ch)
            }
        }
        return out.append('"').toString()
    }

    /** Python's repr(float) for the values a backup holds (no exponents). */
    fun pyFloat(d: Double): String {
        require(d.isFinite()) { "non-finite float" }
        if (d == Math.rint(d) && Math.abs(d) < 1e16) return "%d.0".format(d.toLong())
        val text = d.toString()
        require('E' !in text) { "float needs Python's exponent format" }
        return text
    }

    fun pyJson(e: JsonElement): String = when (e) {
        is JsonNull -> "null"
        is JsonPrimitive -> if (e.isString) pyString(e.content) else e.content
        is JsonArray -> e.joinToString(",", "[", "]") { pyJson(it) }
        is JsonObject -> e.entries.sortedBy { it.key }.joinToString(",", "{", "}") { (k, v) -> pyString(k) + ":" + pyJson(v) }
    }
}

enum class BackupError { NotABackup, WrongVersion, ChecksumMismatch, Corrupt }

sealed interface ImportResult {
    data class Ok(val tables: Int, val rows: Int) : ImportResult
    data class Failed(val reason: BackupError) : ImportResult
}

sealed interface ExportResult {
    data class Ok(val tables: Int, val rows: Int) : ExportResult
    data class Failed(val message: String) : ExportResult
}
