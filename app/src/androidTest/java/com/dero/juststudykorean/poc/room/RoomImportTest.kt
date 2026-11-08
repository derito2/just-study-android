package com.dero.juststudykorean.poc.room

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * PoC 3: your real backup (a .jsbackup.zip in files/poc/in, pushed by adb) into Room. Results go to
 * files/poc/room-*.json with numbers only: never data from your notes.
 */
@RunWith(AndroidJUnit4::class)
class RoomImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val poc = File(context.filesDir, "poc").apply { mkdirs() }
    private lateinit var zip: File
    private lateinit var db: PocDatabase

    @Before
    fun setUp() {
        val found = File(poc, "in").listFiles { f -> f.name.endsWith(".jsbackup.zip") }.orEmpty()
        assumeTrue("no backup in files/poc/in", found.isNotEmpty())
        zip = found.maxBy { it.name }
        db = PocDatabase.open(context, "poc-import.db")
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private fun manifest(file: File = zip) =
        ZipFile(file).use { z -> JSONObject(z.getInputStream(z.getEntry("manifest.json")).bufferedReader().readText()) }

    private fun write(name: String, json: JSONObject) = File(poc, name).writeText(json.toString(1))

    @Test
    fun importsEveryRowAndChecksum() = runBlocking {
        val pssBefore = Debug.getPss()
        val report = BackupImporter.import(zip, db)
        val pssAfter = Debug.getPss()
        val tables = manifest().getJSONObject("tables")
        val expected = tables.keys().asSequence().associateWith { tables.getJSONObject(it).getInt("rows") }
        val dbFile = context.getDatabasePath("poc-import.db")
        write("room-import.json", JSONObject()
            .put("millis", report.millis).put("errors", report.errors.size)
            .put("tables", expected.size).put("tablesEqual", expected.count { (t, n) -> report.tables[t] == n })
            .put("rows", expected.values.sum()).put("rowsImported", report.tables.values.sum())
            .put("pssBeforeKb", pssBefore).put("pssPeakKb", maxOf(pssBefore, pssAfter, report.pssPeakKb))
            .put("dbBytes", dbFile.length() + File(dbFile.path + "-wal").let { if (it.exists()) it.length() else 0 })
            .put("zipBytes", zip.length()))
        assertEquals(emptyList<String>(), report.errors)
        assertEquals(expected, report.tables)
    }

    @Test
    fun roundTripIsIdentical() = runBlocking {
        val report = BackupImporter.import(zip, db)
        assertEquals(emptyList<String>(), report.errors)
        val out = File(poc, "roundtrip").apply { deleteRecursively(); mkdirs() }
        BackupExporter.export(db, out)
        val result = JSONObject()
        var same = 0
        ZipFile(zip).use { z ->
            for (table in SCHEMA.keys) {
                val original = z.getInputStream(z.getEntry("tables/$table.jsonl")).readBytes()
                val again = File(out, "tables/$table.jsonl").readBytes()
                val equal = original.contentEquals(again)
                if (equal) same++
                result.put(table, JSONObject().put("equal", equal).put("bytes", original.size).put("bytesOut", again.size)
                    .put("firstDiffAt", firstDiff(original, again)))
            }
        }
        out.deleteRecursively() // your data: only the comparison stays
        write("room-roundtrip.json", JSONObject().put("tables", SCHEMA.size).put("identical", same).put("byTable", result))
        assertEquals(SCHEMA.size, same)
    }

    @Test
    fun corruptZipLeavesDbEmpty() = runBlocking {
        val cases = JSONArray()
        // 1) one JSONL changed but the manifest keeps the old sha256; 2) a table missing (half-written zip).
        val variants = mapOf(
            "bad-sha256" to { name: String, bytes: ByteArray -> if (name == "tables/question_attempts.jsonl") bytes + "\n".toByteArray() else bytes },
            "missing-table" to { name: String, bytes: ByteArray -> if (name == "tables/flashcard_reviews.jsonl") null else bytes },
        )
        for ((label, change) in variants) {
            val broken = File(poc, "broken-$label.zip")
            rewrite(zip, broken, change)
            val report = BackupImporter.import(broken, db)
            broken.delete()
            val rows = count()
            cases.put(JSONObject().put("case", label).put("errors", report.errors.size).put("rowsLeft", rows))
            assertTrue("$label must fail", report.errors.isNotEmpty())
            assertEquals("$label must leave the db empty", 0, rows)
        }
        // 3) a bad row in the LAST table with a valid sha256: the first 17 tables get inserted
        //    and the whole transaction must roll back.
        val last = SCHEMA.keys.last()
        val badDate = Regex("""(\d\d:\d\d:\d\d)Z"""")
        val badBytes = ZipFile(zip).use { z -> z.getInputStream(z.getEntry("tables/$last.jsonl")).readBytes() }
            .toString(Charsets.UTF_8).replaceFirst(badDate, "$1+00:00\"").toByteArray()
        val manifest = manifest().apply {
            getJSONObject("tables").getJSONObject(last).put("sha256",
                java.security.MessageDigest.getInstance("SHA-256").digest(badBytes).joinToString("") { "%02x".format(it) })
        }
        val broken = File(poc, "broken-row.zip")
        rewrite(zip, broken) { name, bytes ->
            when (name) {
                "tables/$last.jsonl" -> badBytes
                "manifest.json" -> manifest.toString().toByteArray()
                else -> bytes
            }
        }
        val report = BackupImporter.import(broken, db)
        broken.delete()
        val rows = count()
        val rolledBack = report.errors.any { "rolled back" in it }
        cases.put(JSONObject().put("case", "bad-row-in-$last").put("errors", report.errors.size)
            .put("rolledBack", rolledBack).put("rowsLeft", rows))
        assertTrue("a bad row must roll back", rolledBack)
        assertEquals("rollback must leave the db empty", 0, rows)
        write("room-corrupt.json", JSONObject().put("cases", cases))
    }

    private suspend fun count(): Int = SCHEMA.keys.sumOf { RoomSql.count(db, it) }

    private fun firstDiff(a: ByteArray, b: ByteArray): Int =
        (0 until minOf(a.size, b.size)).firstOrNull { a[it] != b[it] } ?: if (a.size == b.size) -1 else minOf(a.size, b.size)

    private fun rewrite(from: File, to: File, change: (String, ByteArray) -> ByteArray?) {
        ZipFile(from).use { z ->
            ZipOutputStream(to.outputStream()).use { out ->
                for (e in z.entries()) {
                    val bytes = change(e.name, z.getInputStream(e).readBytes()) ?: continue
                    out.putNextEntry(ZipEntry(e.name)); out.write(bytes); out.closeEntry()
                }
            }
        }
    }

    /** Streak and minutes per day of the last 30 days, in the web's zone, at the moment the
     *  web answered (files/poc/in/web-progress.json). */
    @Test
    fun datesMatchTheWeb() = runBlocking {
        val web = File(poc, "in/web-progress.json")
        assumeTrue("no web-progress.json", web.exists())
        assertEquals(emptyList<String>(), BackupImporter.import(zip, db).errors)
        val json = JSONObject(web.readText())
        val zone = java.time.ZoneId.of(json.getString("timezone_setting"))
        val now = java.time.Instant.parse(json.getString("fetched_at"))
        val local = Progress(db, zone, now)
        val webDays = (0 until json.getJSONArray("calendar").length()).flatMap { m ->
            val days = json.getJSONArray("calendar").getJSONObject(m).getJSONArray("days")
            (0 until days.length()).map { days.getJSONObject(it) }
        }.associateBy { java.time.LocalDate.parse(it.getString("date")) }
        val last30 = (0L until 30L).map { local.today.minusDays(it) }
        val mine = local.days(last30.min(), local.today)
        var minutesEqual = 0; var questionsEqual = 0
        for (day in last30) {
            val w = webDays.getValue(day); val m = mine[day] ?: Progress.Day(0, 0)
            if (w.getInt("minutes") == m.minutes) minutesEqual++
            if (w.getInt("questions") == m.questions) questionsEqual++
        }
        val webStreak = json.getJSONObject("summary").getInt("streak_days")
        val streak = local.streak()
        val utc = local.allUtc()
        write("room-dates.json", JSONObject().put("days", last30.size).put("minutesEqual", minutesEqual)
            .put("questionsEqual", questionsEqual).put("streakWeb", webStreak).put("streakRoom", streak)
            .put("datesChecked", utc.first).put("datesUtc", utc.second))
        assertEquals(webStreak, streak)
        assertEquals(30, minutesEqual)
        assertEquals(30, questionsEqual)
        assertEquals(utc.first, utc.second)
    }
}
