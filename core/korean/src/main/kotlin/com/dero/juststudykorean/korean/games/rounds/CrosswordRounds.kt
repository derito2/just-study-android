package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.CrossEntry
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.RoundContext
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import kotlin.random.Random

private const val SIZE = 7
private const val TRIES = 200
private const val POOL = 60

private class Placed(val item: GameItem, val row: Int, val col: Int, val across: Boolean)

/** One greedy try: [first] across in the middle, then each word of [rest] (in order) that crosses a placed one. */
private fun tryGrid(first: GameItem, rest: List<GameItem>, n: Int, random: Random): List<Placed>? {
    val grid = arrayOfNulls<String>(SIZE * SIZE)
    val dirs = IntArray(SIZE * SIZE) // 1: across, 2: down
    val placed = mutableListOf<Placed>()
    fun at(r: Int, c: Int) = if (r in 0 until SIZE && c in 0 until SIZE) grid[r * SIZE + c] else null
    fun fits(w: GameItem, r: Int, c: Int, across: Boolean): Boolean {
        val (dr, dc) = if (across) 0 to 1 else 1 to 0
        val len = w.blocks.size
        if (r < 0 || c < 0 || r + dr * (len - 1) >= SIZE || c + dc * (len - 1) >= SIZE) return false
        if (at(r - dr, c - dc) != null || at(r + dr * len, c + dc * len) != null) return false
        var crosses = 0
        for (i in 0 until len) {
            val (rr, cc) = r + dr * i to c + dc * i
            val g = grid[rr * SIZE + cc]
            if (g != null) {
                if (g != w.blocks[i] || dirs[rr * SIZE + cc] and (if (across) 1 else 2) != 0) return false
                crosses++
            } else if (at(rr + dc, cc + dr) != null || at(rr - dc, cc - dr) != null) return false // never side by side
        }
        return crosses > 0
    }
    fun place(w: GameItem, r: Int, c: Int, across: Boolean) {
        val (dr, dc) = if (across) 0 to 1 else 1 to 0
        for (i in w.blocks.indices) { val k = (r + dr * i) * SIZE + c + dc * i; grid[k] = w.blocks[i]; dirs[k] = dirs[k] or (if (across) 1 else 2) }
        placed += Placed(w, r, c, across)
    }
    place(first, SIZE / 2, (SIZE - first.blocks.size) / 2, true)
    for (w in rest) {
        if (placed.size == n) break
        val options = placed.flatMap { p ->
            p.item.blocks.indices.flatMap { i ->
                val (pr, pc) = if (p.across) p.row to p.col + i else p.row + i to p.col
                w.blocks.indices.filter { w.blocks[it] == p.item.blocks[i] }.map { k -> if (p.across) Triple(pr - k, pc, false) else Triple(pr, pc - k, true) }
            }
        }.filter { (r, c, across) -> fits(w, r, c, across) }
        options.randomOrNull(random)?.let { (r, c, across) -> place(w, r, c, across) }
    }
    return placed.takeIf { it.size == n }
}

/**
 * F7 (FASE_11_PLAN PR 10): 4, 5 or 7 of your words (by level) in a grid of syllables of at most 7 × 7, with the session's
 * Random: the longest in the middle, then others crossing at a shared syllable, never side by side. Up to 200 tries
 * (each a new order of the words); if they don't fit, no round. Level 1 shows one syllable of each word.
 */
val CrosswordRounds = RoundFactory { ctx: RoundContext ->
    val n = level(ctx, 4, 5, 7)
    val words = ctx.bank.words.filter { it.blocks.size in 2..5 && it.variants.size == 1 && it.blocks.all { b -> b.length == 1 && b[0] in '가'..'힣' } }
    val pool = distinctPool(ctx, words, POOL)
    if (pool.size < n) return@RoundFactory null
    val first = pool.maxBy { it.blocks.size }
    var rest = pool - first
    var placed: List<Placed>? = null
    for (t in 0 until TRIES) {
        placed = tryGrid(first, rest, n, ctx.random) ?: run { rest = rest.shuffled(ctx.random); null }
        if (placed != null) break
    }
    if (placed == null) return@RoundFactory null

    val top = placed.minOf { it.row }
    val left = placed.minOf { it.col }
    val rows = placed.maxOf { it.row + if (it.across) 0 else it.item.blocks.size - 1 } - top + 1
    val cols = placed.maxOf { it.col + if (it.across) it.item.blocks.size - 1 else 0 } - left + 1
    val starts = placed.map { (it.row - top) * cols + it.col - left }.distinct().sorted()
    val entries = placed.map { CrossEntry(it.item, it.row - top, it.col - left, it.across, starts.indexOf((it.row - top) * cols + it.col - left) + 1) }
        .sortedWith(compareBy({ it.number }, { !it.across }))
    val givens = mutableSetOf<Int>()
    if (ctx.level == 1) for (e in entries) e.cells(cols).firstOrNull { it !in givens }?.let { givens += it }
    RoundSpec.Crossword(ctx.level, rows, cols, entries, givens)
}
