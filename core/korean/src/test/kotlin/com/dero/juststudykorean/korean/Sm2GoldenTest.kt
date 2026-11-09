package com.dero.juststudykorean.korean

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class Sm2GoldenTest {
    private val golden = Golden.obj("sm2.json")
    private val configs = golden.getJSONObject("configs").let { c ->
        c.keys().asSequence().associateWith { k ->
            c.getJSONObject(k).let { Scheduling(it.getInt("again_minutes"), it.getInt("hard_hours"), it.getInt("good_hours"), it.getInt("easy_hours")) }
        }
    }

    private fun rating(s: String) = Rating.valueOf(s.replaceFirstChar(Char::uppercase))

    private fun check(where: String, want: JSONObject, got: Next) {
        assertEquals("$where ease", want.getDouble("ease"), got.ease, 0.0)
        assertEquals("$where interval", want.getInt("interval_hours"), got.intervalHours)
        assertEquals("$where repetitions", want.getInt("repetitions"), got.repetitions)
        assertEquals("$where due", want.getLong("due_in_minutes"), got.dueInMinutes)
        assertEquals("$where label", want.getString("label"), label(got.dueInMinutes))
    }

    @Test
    fun everySequenceMatches() {
        val sequences = golden.getJSONArray("sequences").objects()
        assertEquals(680, sequences.size)
        sequences.forEachIndexed { i, seq ->
            val cfg = configs.getValue(seq.getString("config"))
            var state = CardState()
            seq.getJSONArray("steps").objects().forEachIndexed { j, step ->
                val next = schedule(state, rating(step.getString("rating")), cfg)
                check("sequence $i step $j", step, next)
                state = CardState(next.ease, next.intervalHours, next.repetitions)
            }
        }
    }

    @Test
    fun halfRoundsToEven() {
        golden.getJSONArray("forced").objects().forEach { c ->
            val s = c.getJSONObject("state")
            val state = CardState(s.getDouble("ease"), s.getInt("interval_hours"), s.getInt("repetitions"))
            check("forced", c.getJSONObject("next"), schedule(state, rating(c.getString("rating")), configs.getValue(c.getString("config"))))
        }
    }
}
