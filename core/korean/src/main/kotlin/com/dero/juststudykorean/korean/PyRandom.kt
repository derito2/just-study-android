package com.dero.juststudykorean.korean

import kotlin.random.Random

/**
 * Python's random.Random (Mersenne Twister MT19937, seeded like CPython 3.12) with the two methods the quiz
 * builders use, shuffle and sample, so the same seed gives the same options as the web (golden/random.json).
 * It is also a kotlin.random.Random, for anything else.
 */
class PyRandom(seed: Long) : Random() {
    private val mt = LongArray(N)
    private var index = N

    init {
        // random.seed(int): the absolute value as 32-bit words, least significant first (0 -> [0]).
        var n = Math.abs(seed)
        val key = mutableListOf<Long>()
        do { key += n and MASK; n = n ushr 32 } while (n != 0L)
        initByArray(key)
    }

    private fun initGenrand(s: Long) {
        mt[0] = s and MASK
        for (i in 1 until N) mt[i] = (1812433253L * (mt[i - 1] xor (mt[i - 1] ushr 30)) + i) and MASK
    }

    private fun initByArray(key: List<Long>) {
        initGenrand(19650218L)
        var i = 1
        var j = 0
        repeat(maxOf(N, key.size)) {
            mt[i] = ((mt[i] xor ((mt[i - 1] xor (mt[i - 1] ushr 30)) * 1664525L)) + key[j] + j) and MASK
            i++; j++
            if (i >= N) { mt[0] = mt[N - 1]; i = 1 }
            if (j >= key.size) j = 0
        }
        repeat(N - 1) {
            mt[i] = ((mt[i] xor ((mt[i - 1] xor (mt[i - 1] ushr 30)) * 1566083941L)) - i) and MASK
            i++
            if (i >= N) { mt[0] = mt[N - 1]; i = 1 }
        }
        mt[0] = 0x80000000L
    }

    private fun next32(): Long {
        if (index >= N) {
            for (k in 0 until N) {
                val y = (mt[k] and 0x80000000L) or (mt[(k + 1) % N] and 0x7fffffffL)
                mt[k] = mt[(k + M) % N] xor (y ushr 1) xor (if (y and 1L != 0L) 0x9908b0dfL else 0L)
            }
            index = 0
        }
        var y = mt[index++]
        y = y xor (y ushr 11)
        y = y xor ((y shl 7) and 0x9d2c5680L)
        y = y xor ((y shl 15) and 0xefc60000L)
        y = y xor (y ushr 18)
        return y and MASK
    }

    /** random.getrandbits(k) for k ≤ 32. */
    fun getrandbits(k: Int): Long {
        require(k in 0..32) { "only up to 32 bits" }
        return if (k == 0) 0 else next32() ushr (32 - k)
    }

    override fun nextBits(bitCount: Int): Int = getrandbits(bitCount).toInt()

    /** random._randbelow: getrandbits(n.bit_length()) until it is below n. */
    fun randbelow(n: Int): Int {
        if (n <= 0) return 0
        val k = 32 - Integer.numberOfLeadingZeros(n)
        var r = getrandbits(k)
        while (r >= n) r = getrandbits(k)
        return r.toInt()
    }

    /** random.shuffle, in place. */
    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.lastIndex downTo 1) {
            val j = randbelow(i + 1)
            list[i] = list[j].also { list[j] = list[i] }
        }
    }

    /** random.sample(population, k). */
    fun <T> sample(population: List<T>, k: Int): List<T> {
        val n = population.size
        require(k in 0..n) { "sample larger than population" }
        var setsize = 21
        if (k > 5) setsize += Math.pow(4.0, Math.ceil(Math.log(k * 3.0) / Math.log(4.0))).toInt()
        val result = ArrayList<T>(k)
        if (n <= setsize) {
            val pool = population.toMutableList()
            for (i in 0 until k) {
                val j = randbelow(n - i)
                result += pool[j]
                pool[j] = pool[n - i - 1]
            }
        } else {
            val selected = HashSet<Int>()
            for (i in 0 until k) {
                var j = randbelow(n)
                while (j in selected) j = randbelow(n)
                selected += j
                result += population[j]
            }
        }
        return result
    }

    private companion object {
        const val N = 624
        const val M = 397
        const val MASK = 0xffffffffL
    }
}
