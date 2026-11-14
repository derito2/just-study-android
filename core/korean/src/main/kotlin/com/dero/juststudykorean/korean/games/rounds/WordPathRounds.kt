package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import kotlin.random.Random

private const val TRIES = 20

/** The cells next to [cell] in the 8 directions. */
internal fun neighbours(cell: Int, size: Int): List<Int> {
    val r = cell / size
    val c = cell % size
    return (-1..1).flatMap { dr -> (-1..1).map { dc -> dr to dc } }.filter { it != 0 to 0 }
        .map { (dr, dc) -> (r + dr) to (c + dc) }.filter { (y, x) -> y in 0 until size && x in 0 until size }.map { (y, x) -> y * size + x }
}

/** How many paths of contiguous cells, without repeating one, spell [word] (stops counting at [limit]). */
internal fun countPaths(grid: List<String>, size: Int, word: List<String>, limit: Int = 2): Int {
    var found = 0
    fun go(cell: Int, i: Int, used: Set<Int>) {
        if (found >= limit || grid[cell] != word[i]) return
        if (i == word.lastIndex) { found++; return }
        for (n in neighbours(cell, size)) if (n !in used) go(n, i + 1, used + n)
    }
    for (start in grid.indices) go(start, 0, setOf(start))
    return found
}

private fun randomPath(length: Int, size: Int, random: Random): List<Int>? {
    repeat(50) {
        val path = mutableListOf(random.nextInt(size * size))
        while (path.size < length) {
            val next = neighbours(path.last(), size).filter { it !in path }
            if (next.isEmpty()) break
            path += next.random(random)
        }
        if (path.size == length) return path
    }
    return null
}

/**
 * 4.7. Level 1: 3 × 3, a word of 3 blocks; 2: 4 × 4, 3–4 blocks; 3: 4 × 4, 4–5 blocks with look-alike fillers.
 * The word is spelt by exactly one path; else another derived seed (up to 20), then null.
 */
val WordPathRounds = RoundFactory { ctx ->
    val size = if (ctx.level == 1) 3 else 4
    val sizes = when (ctx.level) { 1 -> 3..3; 2 -> 3..4; else -> 4..5 }
    val item = ctx.pick(ctx.bank.words.filter { it.blocks.size in sizes }, 1).firstOrNull() ?: return@RoundFactory null
    val word = item.blocks
    val others = ctx.bank.allBlocks.filter { it !in word }.sorted()
    val fillers = if (ctx.level == 3) others.filter { o -> word.any { similarBlock(it, o) } }.ifEmpty { others } else others
    if (fillers.isEmpty()) return@RoundFactory null
    repeat(TRIES) { attempt ->
        val random = Random(ctx.random.nextLong() xor attempt.toLong())
        val path = randomPath(word.size, size, random) ?: return@repeat
        val grid = MutableList(size * size) { fillers.random(random) }
        path.forEachIndexed { i, cell -> grid[cell] = word[i] }
        if (countPaths(grid, size, word) == 1) return@RoundFactory RoundSpec.WordPath(ctx.level, item, size, grid, path)
    }
    null
}
