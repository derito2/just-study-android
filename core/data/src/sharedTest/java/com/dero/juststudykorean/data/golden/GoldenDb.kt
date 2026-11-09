package com.dero.juststudykorean.data.golden

import android.content.Context
import android.net.Uri
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.backup.BackupImporter
import com.dero.juststudykorean.data.backup.ImportResult
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.time.FixedClock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Base of the database golden tests: Room in memory, a golden/db backup imported, the golden's fixed clock. */
abstract class GoldenDb(private val zip: String = "fixture") {
    companion object {
        val ZONE: ZoneId = ZoneId.of("America/Mexico_City")
        /** 18:30 on 8 Nov 2026 in Mexico City (already 9 Nov in UTC), as tools/golden/export_db_golden.py. */
        val NOW: Instant = ZonedDateTime.of(2026, 11, 8, 18, 30, 0, 0, ZONE).toInstant()
        /** A golden/db file: from the repo on the JVM; on the phone, from the test APK's assets (copied to the cache). */
        fun file(name: String): File {
            if (android.os.Build.FINGERPRINT == "robolectric") return File("../../golden/db/$name")
            val out = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "golden-$name")
            if (!out.exists()) androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { i -> out.outputStream().use { i.copyTo(it) } }
            return out
        }
        fun golden(name: String): JsonElement = Json.parseToJsonElement(file("$name.json").readText())
    }

    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val clock = FixedClock(NOW, ZONE)
    protected lateinit var db: JustStudyDatabase

    @Before
    fun openGolden() {
        File(context.filesDir, BackupFormat.FILES_DIR).deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        val result = runBlocking { BackupImporter(context, db).import(Uri.fromFile(file("$zip.jsbackup.zip"))) }
        check(result is ImportResult.Ok) { "golden import: $result" }
    }

    @After
    fun closeGolden() = db.close()
}

// ---- Kotlin values as the web's JSON (Python's isoformat and json.dumps), and a tolerant comparison.

private val AWARE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")
private val NAIVE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

/** Python's datetime.isoformat(): seconds always, microseconds only when not zero. */
fun pyIso(t: ZonedDateTime): String = if (t.nano == 0) AWARE.format(t) else t.toOffsetDateTime().toString()

/** A naive UTC datetime (how the web keeps them): "2026-11-08T15:00:00". */
fun pyNaive(millis: Long): String = NAIVE.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC))

fun js(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is ZonedDateTime -> JsonPrimitive(pyIso(value))
    is LocalDate -> JsonPrimitive(value.toString())
    is ZoneId -> JsonPrimitive(value.id)
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to js(v) })
    is Iterable<*> -> JsonArray(value.map(::js))
    else -> error("no JSON for ${value::class}")
}

fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to js(v) })

/** Same keys and values; numbers compared as doubles (Python 1 == 1.0). */
fun assertJson(where: String, expected: JsonElement, actual: JsonElement) {
    when {
        expected is JsonObject && actual is JsonObject -> {
            assertEquals("$where keys", expected.keys, actual.keys)
            for (k in expected.keys) assertJson("$where.$k", expected.getValue(k), actual.getValue(k))
        }
        expected is JsonArray && actual is JsonArray -> {
            assertEquals("$where size", expected.size, actual.size)
            expected.indices.forEach { assertJson("$where[$it]", expected[it], actual[it]) }
        }
        expected is JsonPrimitive && actual is JsonPrimitive && !expected.isString && !actual.isString &&
            expected.booleanOrNull == null && expected.doubleOrNull != null -> assertEquals(where, expected.doubleOrNull!!, actual.doubleOrNull ?: Double.NaN, 1e-12)
        else -> assertEquals(where, expected, actual)
    }
}
