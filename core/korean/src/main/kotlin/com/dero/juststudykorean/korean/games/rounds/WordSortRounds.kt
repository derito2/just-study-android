package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.coach.CATEGORIES
import com.dero.juststudykorean.korean.coach.belongs
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec

private val NEVER_TYPED = setOf("이다", "아니다", "있다", "없다", "되다")

/** 4.8: Verbs, Adjectives or Nouns; null if the word is out or its meanings are of different types. */
internal fun wordType(item: GameItem): String? {
    if (item.variants.any { it in NEVER_TYPED }) return null
    val senses = item.meaning.split(Regex("[/;,]")).map { it.trim() }.filter { it.isNotEmpty() }
    val types = senses.map { m ->
        val ends = item.variants.all { it.endsWith("다") }
        when {
            ends && m.startsWith("To be ") && m.length > 6 -> "Adjectives"
            ends && m.startsWith("To ") && !m.startsWith("To be ") -> "Verbs"
            !ends && !m.startsWith("To ") -> "Nouns"
            else -> null
        }
    }.toSet()
    return types.singleOrNull()
}

/**
 * 4.8. Bins by theme (a word in exactly one of the round's categories) or by word type. Level 1: 2 bins, 6 words, the
 * meaning visible; 2: 3 bins, 8 words; 3: 3 bins, 9 words. A random choice among the combinations that have enough.
 */
val WordSortRounds = RoundFactory { ctx ->
    val binCount = level(ctx, 2, 3, 3)
    val total = level(ctx, 6, 8, 9)
    val perBin = List(binCount) { total / binCount + if (it < total % binCount) 1 else 0 }
    val words = ctx.bank.words.filter { it.key !in ctx.avoid }
    val options = mutableListOf<Pair<List<String>, List<List<GameItem>>>>()
    // By theme: every combination of categories where each has enough words that belong to it and to no other of the round.
    fun combos(names: List<String>, k: Int): List<List<String>> =
        if (k == 0) listOf(emptyList()) else names.indices.flatMap { i -> combos(names.drop(i + 1), k - 1).map { listOf(names[i]) + it } }
    for (combo in combos(CATEGORIES.keys.toList(), binCount)) {
        val bins = combo.map { c -> words.filter { w -> belongs(c, w.meaning) && combo.none { o -> o != c && belongs(o, w.meaning) } } }
        if (bins.indices.all { bins[it].size >= maxOf(3, perBin[it]) }) options += combo.map { it.replaceFirstChar(Char::uppercase) } to bins
    }
    val typed = words.groupBy(::wordType).filterKeys { it != null }.mapKeys { it.key!! }
    for (combo in combos(listOf("Verbs", "Adjectives", "Nouns"), binCount)) {
        val bins = combo.map { typed[it].orEmpty() }
        if (bins.indices.all { bins[it].size >= maxOf(3, perBin[it]) }) options += combo to bins
    }
    if (options.isEmpty()) return@RoundFactory null
    val (labels, pools) = options.random(ctx.random)
    val tiles = pools.flatMapIndexed { bin, pool -> ctx.pick(pool, perBin[bin]).map { it to bin } }
    if (tiles.size != total) return@RoundFactory null
    RoundSpec.WordSort(ctx.level, labels, tiles.shuffled(ctx.random), showMeaning = ctx.level == 1)
}
