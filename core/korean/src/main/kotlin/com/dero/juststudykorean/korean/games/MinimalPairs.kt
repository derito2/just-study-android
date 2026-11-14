package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.hangul.decompose
import com.dero.juststudykorean.korean.hangul.jamoDistance

// FASE_8_PLAN 4.10 (D8-6): minimal pairs from your notes, or the fixed list (marked Extra · not from your notes).

/** The fixed list: 21 groups of real Korean words, each with its meaning (shown when you answer). */
val FIXED_MINIMAL_GROUPS: List<List<Pair<String, String>>> = listOf(
    listOf("불" to "fire", "풀" to "grass", "뿔" to "horn"),
    listOf("달" to "moon", "탈" to "mask", "딸" to "daughter"),
    listOf("자다" to "to sleep", "차다" to "to kick", "짜다" to "to be salty"),
    listOf("살" to "flesh", "쌀" to "rice (uncooked)"),
    listOf("방" to "room", "빵" to "bread"),
    listOf("굴" to "oyster", "꿀" to "honey"),
    listOf("비" to "rain", "피" to "blood"),
    listOf("발" to "foot", "팔" to "arm"),
    listOf("공" to "ball", "콩" to "bean"),
    listOf("사다" to "to buy", "싸다" to "to be cheap"),
    listOf("개" to "dog", "깨" to "sesame"),
    listOf("도끼" to "axe", "토끼" to "rabbit"),
    listOf("섬" to "island", "솜" to "cotton"),
    listOf("벌" to "bee", "볼" to "cheek"),
    listOf("강" to "river", "간" to "liver"),
    listOf("밤" to "night", "밥" to "rice (cooked)"),
    listOf("산" to "mountain", "상" to "table"),
    listOf("문" to "door", "물" to "water"),
    listOf("곰" to "bear", "공" to "ball"),
    listOf("눈" to "eye", "논" to "rice paddy"),
    listOf("거리" to "street", "고리" to "ring"),
)

/** Two words of your notes with the same number of blocks and one jamo apart. */
fun minimalPairsFromNotes(bank: GameBank): List<Pair<GameItem, GameItem>> {
    val words = bank.words.filter { it.variants.size == 1 }
    return words.indices.flatMap { i -> (i + 1 until words.size).map { j -> words[i] to words[j] } }
        .filter { (a, b) -> a.blocks.size == b.blocks.size && jamoDistance(a.korean, b.korean) == 1 }
}

/** Where two minimal-pair words differ: the first sound, the vowel, the final consonant, or one sound. */
fun minimalDifference(a: String, b: String): String {
    if (a.length == b.length) {
        val i = a.indices.firstOrNull { a[it] != b[it] }
        val x = i?.let { decompose(a[it]) }
        val y = i?.let { decompose(b[it]) }
        if (x != null && y != null) when {
            i == 0 && x.lead != y.lead && x.vowel == y.vowel && x.tail == y.tail -> return "the first sound"
            x.vowel != y.vowel && x.lead == y.lead && x.tail == y.tail -> return "the vowel"
            x.tail != y.tail && x.lead == y.lead && x.vowel == y.vowel -> return "the final consonant"
        }
    }
    return "one sound"
}
