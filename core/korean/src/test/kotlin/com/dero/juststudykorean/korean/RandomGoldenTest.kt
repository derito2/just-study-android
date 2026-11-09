package com.dero.juststudykorean.korean

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

/** PyRandom gives exactly what Python's random.Random gives (golden/random.json). */
class RandomGoldenTest {
    private fun JSONArray.longs() = List(length()) { getLong(it) }
    private fun JSONArray.ints() = List(length()) { getInt(it) }

    @Test
    fun sameAsPython() {
        val cases = Golden.arr("random.json").objects()
        for (c in cases) {
            val seed = c.getLong("seed")
            val rng = PyRandom(seed)
            assertEquals("seed $seed bits", c.getJSONArray("getrandbits").longs(), listOf(32, 1, 5, 31, 17).map(rng::getrandbits))
            val shuffled = (0 until 10).toMutableList().also(rng::shuffle)
            assertEquals("seed $seed shuffle", c.getJSONArray("shuffle10").ints(), shuffled)
            assertEquals("seed $seed sample30", c.getJSONArray("sample30_3").ints(), rng.sample((0 until 30).toList(), 3))
            assertEquals("seed $seed sample5", c.getJSONArray("sample5_3").ints(), rng.sample((0 until 5).toList(), 3))
            assertEquals("seed $seed sample40", c.getJSONArray("sample40_3").ints(), rng.sample((0 until 40).toList(), 3))
        }
        println("random: ${cases.size}")
    }
}
