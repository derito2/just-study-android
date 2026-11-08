package com.dero.juststudykorean.poc.room

import androidx.room3.useReaderConnection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** SQL helpers and the JSON format the web writes (`json.dumps(sort_keys, separators=(",", ":"),
 *  ensure_ascii=False)`), so the export is byte-identical. */
object RoomSql {
    fun quote(name: String) = "\"" + name.replace("\"", "\"\"") + "\""

    suspend fun count(db: PocDatabase, table: String): Int =
        db.useReaderConnection { c -> c.usePrepared("SELECT COUNT(*) FROM ${quote(table)}") { s -> s.step(); s.getLong(0).toInt() } }

    // ------------------------------------------------------------ dates (UTC only)
    private val UTC_ISO = Regex("""^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d{6})?Z$""")
    private val SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC)

    fun parseUtc(text: String): Long {
        require(UTC_ISO.matches(text)) { "date not in UTC ISO-8601 with Z" }
        return Instant.parse(text).toEpochMilli()
    }

    fun formatUtc(millis: Long): String {
        val frac = Math.floorMod(millis, 1000L)
        val base = SECONDS.format(Instant.ofEpochMilli(millis - frac))
        return if (frac == 0L) "${base}Z" else "$base.%06dZ".format(frac * 1000)
    }

    // ------------------------------------------------------------ Python json.dumps
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

    /** Python's repr(float) for the values a backup can hold. */
    fun pyFloat(d: Double): String {
        require(d.isFinite()) { "non-finite float" }
        if (d == Math.rint(d) && Math.abs(d) < 1e16) return "%d.0".format(d.toLong())
        val text = d.toString()
        require('E' !in text) { "float needs Python's exponent format: $text" }
        return text
    }

    fun pyJson(e: JsonElement): String = when (e) {
        is JsonNull -> "null"
        is JsonPrimitive -> if (e.isString) pyString(e.content) else e.content
        is JsonArray -> e.joinToString(",", "[", "]") { pyJson(it) }
        is JsonObject -> e.entries.sortedBy { it.key }.joinToString(",", "{", "}") { (k, v) -> pyString(k) + ":" + pyJson(v) }
    }
}
