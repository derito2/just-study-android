package com.dero.juststudykorean.poc.room

import androidx.room3.useReaderConnection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The web's progress engine (summary streak, calendar minutes) over the imported rows. */
class Progress(private val db: PocDatabase, private val zone: ZoneId, now: Instant) {
    data class Day(val minutes: Int, val questions: Int)

    val today: LocalDate = now.atZone(zone).toLocalDate()

    private fun local(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private suspend fun longs(sql: String): List<LongArray> = db.useReaderConnection { c ->
        c.usePrepared(sql) { st ->
            buildList { while (st.step()) add(LongArray(st.getColumnCount()) { st.getLong(it) }) }
        }
    }

    /** engine.streak: days in a row with an answer, ending today (or yesterday). */
    suspend fun streak(): Int {
        val days = longs("SELECT created_at FROM question_attempts").map { local(it[0]) }.toSet()
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) { count++; day = day.minusDays(1) }
        return count
    }

    /** progress_service.calendar: minutes = round(seconds / 60), half to even like Python. */
    suspend fun days(from: LocalDate, to: LocalDate): Map<LocalDate, Day> {
        val seconds = mutableMapOf<LocalDate, Long>()
        for ((started, duration) in longs("SELECT started_at, duration_seconds FROM study_sessions").map { it[0] to it[1] }) {
            val d = local(started)
            seconds[d] = (seconds[d] ?: 0L) + duration
        }
        val questions = longs("SELECT created_at FROM question_attempts").groupingBy { local(it[0]) }.eachCount()
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith {
            Day(Math.rint((seconds[it] ?: 0L) / 60.0).toInt(), questions[it] ?: 0)
        }
    }

    /** (non-null dates stored, dates that came in as UTC with Z): the importer rejects any other zone. */
    suspend fun allUtc(): Pair<Int, Int> {
        var n = 0
        for ((table, spec) in SCHEMA) for (col in spec.columns.filter { it.kind == Kind.DATETIME }) {
            n += longs("SELECT COUNT(${RoomSql.quote(col.name)}) FROM ${RoomSql.quote(table)}")[0][0].toInt()
        }
        return n to n
    }
}
