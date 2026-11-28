package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.coach.belongs
import com.dero.juststudykorean.korean.englishSenses
import com.dero.juststudykorean.korean.games.rounds.countPaths
import com.dero.juststudykorean.korean.games.rounds.neighbours
import com.dero.juststudykorean.korean.games.rounds.wordType
import com.dero.juststudykorean.korean.hangul.compose
import com.dero.juststudykorean.korean.hangul.decompose
import com.dero.juststudykorean.korean.hangul.jamoDistance
import com.dero.juststudykorean.korean.norm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** FASE_8_PLAN task 2: every factory, with an invented bank (never the owner's notes), in 500 seeds and 3 levels. */
class FactoriesTest {
    companion object {
        private fun w(k: String, m: String) = GameItem("w:${norm(k)}", ItemKind.WORD, k, k.split(" / "), m, k.split(" / ")[0].map { it.toString() }, 1, "invented.pdf", 1)
        private fun s(k: String, m: String) = GameItem("s:${norm(k)}", ItemKind.SENTENCE, k, listOf(k), m, k.filter { it in '가'..'힣' }.map { it.toString() }, 1, "invented.pdf", 2)

        val WORDS = listOf(
            w("사과", "Apple"), w("우유", "Milk"), w("빵", "Bread"), w("커피", "Coffee"), w("주스", "Juice"), w("과자", "Snack"),
            w("빨강", "Red"), w("파랑", "Blue"), w("노랑", "Yellow"), w("하양", "White"),
            w("고양이", "Cat"), w("강아지", "Puppy"), w("토끼", "Rabbit"), w("호랑이", "Tiger"),
            w("엄마", "Mom"), w("아빠", "Dad"), w("동생", "Younger sibling"),
            w("가다", "To go"), w("먹다", "To eat"), w("읽다", "To read"), w("앉다", "To sit"), w("자다", "To sleep"), w("마시다", "To drink"),
            w("크다", "To be big"), w("작다", "To be small"), w("좋다", "To be good"), w("싸다", "To be cheap"),
            w("학교", "School"), w("책", "Book"), w("물", "Water"), w("집", "House"), w("닭", "Chicken"), w("달", "Moon"), w("딸", "Daughter"),
            w("교실", "Classroom"), w("선생님", "Teacher"), w("친구", "Friend"), w("도서관", "Library"), w("병원", "Hospital"),
            w("가장 / 제일", "The most"), w("화장실", "Bathroom"), w("자동차", "Car"), w("비행기", "Airplane"), w("지하철역", "Subway station"),
            w("눈", "Eye"), w("손", "Hand"), w("발", "Foot"), w("꽃", "Flower"), w("왜", "Why"), w("의자", "Chair"),
        )
        val SENTENCES = listOf(
            s("사과를 먹어요.", "I eat an apple"), s("책을 읽어요.", "I read a book"), s("고양이가 자요.", "The cat sleeps"),
            s("물이 차가워요.", "The water is cold"), s("친구와 학교에 가요.", "I go to school with a friend"),
            s("엄마는 커피를 마셔요.", "Mom drinks coffee"), s("저는 학교로 가요.", "I go to school"), s("동생이 우유를 마셔요.", "My younger sibling drinks milk"),
            s("선생님이 교실에서 책을 읽어요.", "The teacher reads a book in the classroom"), s("아빠는 집으로 와요.", "Dad comes home"),
            s("친구랑 도서관에 가요.", "I go to the library with a friend"), s("강아지가 빵을 먹어요.", "The puppy eats bread"),
            s("우리 집은 학교 옆에 있어요.", "Our house is next to the school"), s("동생은 토끼를 좋아해요.", "My younger sibling likes rabbits"),
        )
        val BANK = GameBank(WORDS, SENTENCES)
        /** Crossword needs words that share syllables: an invented bank of common words (never the owner's notes). */
        val CROSS_BANK = GameBank(listOf(
            "가방" to "Bag", "가수" to "Singer", "가구" to "Furniture", "수박" to "Watermelon", "박수" to "Applause", "수영" to "Swimming",
            "영어" to "English", "어머니" to "Mother", "머리" to "Head", "리본" to "Ribbon", "사자" to "Lion", "자리" to "Seat",
            "기차" to "Train", "차표" to "Ticket", "표지" to "Cover", "지도" to "Map", "도시" to "City", "시계" to "Clock",
            "계단" to "Stairs", "단어" to "Word", "어제" to "Yesterday", "제주도" to "Jeju Island", "도로" to "Road", "로봇" to "Robot",
            "구두" to "Shoes", "두부" to "Tofu", "부모" to "Parents", "모자" to "Hat", "자전거" to "Bicycle", "거울" to "Mirror",
            "산책" to "Walk", "책상" to "Desk", "상자" to "Box", "자유" to "Freedom", "유리" to "Glass", "가위" to "Scissors",
            "위치" to "Location", "치마" to "Skirt", "마음" to "Heart", "음악" to "Music", "악기" to "Instrument", "기자" to "Reporter",
        ).map { (k, m) -> w(k, m) }, emptyList())
        val SEEDS = 0 until 500
        fun ctx(level: Int, seed: Int, bank: GameBank = BANK) = RoundContext(bank, level, Random(seed))
    }

    private fun each(game: GameType, check: (RoundSpec, Int) -> Unit) {
        val factory = FACTORIES.getValue(game)
        for (level in 1..3) {
            var made = 0
            for (seed in SEEDS) {
                val spec = factory.build(ctx(level, seed)) ?: continue
                made++
                assertEquals(game, spec.game)
                assertEquals(level, spec.level)
                assertEquals("same seed, same round", spec, factory.build(ctx(level, seed)))
                check(spec, level)
            }
            assertEquals("$game level $level: a round for every seed", SEEDS.count(), made)
        }
        val empty = factory.build(ctx(1, 0, GameBank(emptyList(), emptyList())))
        // D8-6: Sound Detective's mode A has the fixed list even without notes (marked extra); the rest need your notes.
        if (game == GameType.SoundDetective) assertTrue(empty == null || (empty as RoundSpec.SoundDetective.SameOrDifferent).first.extra)
        else assertNull("$game without content", empty)
    }

    private fun noSharedSense(items: List<GameItem>) {
        val senses = items.map { englishSenses(it.meaning) }
        for (i in senses.indices) for (j in i + 1 until senses.size) assertTrue("${items[i].meaning} / ${items[j].meaning}", (senses[i] intersect senses[j]).isEmpty())
        assertEquals(items.size, items.map { flatKorean(it.korean) }.toSet().size)
    }

    @Test fun hangman() = each(GameType.Hangman) { spec, level ->
        spec as RoundSpec.Hangman
        assertEquals(level > 1, spec.jamoMode)
        if (level == 1) {
            assertTrue(spec.keys.size <= 10 && spec.item.blocks.all { it in spec.keys })
            assertTrue(spec.keys.filter { it !in spec.item.blocks }.all { k -> spec.item.blocks.any { b -> decompose(b.single())!!.let { x -> decompose(k.single())!!.let { y -> x.lead == y.lead || x.vowel == y.vowel } } } })
        } else assertTrue(spec.answer.filter { it != " " }.all { it in spec.keys })
    }

    @Test fun wordMatch() = each(GameType.WordMatch) { spec, level ->
        spec as RoundSpec.WordMatch
        assertEquals(if (level == 1) 5 else 6, spec.items.size)
        assertTrue(spec.items.all { it.kind == if (level == 3) ItemKind.SENTENCE else ItemKind.WORD })
        noSharedSense(spec.items)
        assertEquals(spec.items.indices.toSet(), spec.meaningOrder.toSet())
    }

    @Test fun sentenceBuilder() = each(GameType.SentenceBuilder) { spec, level ->
        spec as RoundSpec.SentenceBuilder
        assertTrue(spec.answer.size in when (level) { 1 -> 3..4; 2 -> 4..5; else -> 5..7 })
        assertNotEquals(spec.answer, spec.tiles)
        assertEquals(spec.answer.sorted(), spec.tiles.sorted())
        assertEquals(Outcome.WON, Evaluators.sentence(spec, spec.answer))
    }

    @Test fun memoryFlip() = each(GameType.MemoryFlip) { spec, level ->
        spec as RoundSpec.MemoryFlip
        assertEquals(if (level == 1) 6 else 8, spec.items.size)
        assertEquals(spec.items.size * 2, spec.cards.toSet().size)
        assertEquals(if (level == 3) 700 else 1000, spec.shownMs)
        noSharedSense(spec.items)
    }

    @Test fun wordScramble() = each(GameType.WordScramble) { spec, level ->
        spec as RoundSpec.WordScramble
        assertTrue(spec.item.blocks.size in when (level) { 1 -> 2..3; 2 -> 3..4; else -> 4..5 })
        assertNotEquals(spec.item.blocks, spec.tiles.filter { it != spec.extra })
        assertEquals(level == 3, spec.extra != null)
        assertTrue(spec.extra == null || spec.extra !in spec.item.blocks)
        assertTrue(BANK.words.none { it.key != spec.item.key && it.blocks != spec.item.blocks && it.blocks.sorted() == spec.item.blocks.sorted() })
    }

    @Test fun listenGuess() = each(GameType.ListenGuess) { spec, level ->
        spec as RoundSpec.ListenGuess
        assertEquals(if (level == 3) 4 else 6, spec.cards.size)
        assertEquals(if (level == 3) 2 else 3, spec.played.distinct().size)
        assertTrue(spec.played.all { it in spec.cards })
        noSharedSense(spec.cards)
    }

    @Test fun hangulBuilder() = each(GameType.HangulBuilder) { spec, level ->
        spec as RoundSpec.HangulBuilder
        assertEquals(if (level == 3) 8 else 6, spec.tray.size)
        assertTrue(spec.pieces.groupingBy { it }.eachCount().all { (p, n) -> spec.tray.count { it == p } >= n })
        assertTrue(Evaluators.builder(spec, spec.pieces))
        val b = decompose(spec.item.blocks[spec.missing].single())!!
        when (level) {
            1 -> assertTrue(b.tail.isEmpty())
            2 -> assertTrue(b.tail.length == 1 || b.lead in "ㄲㄸㅃㅆㅉ")
            else -> assertTrue(b.vowel in "ㅘㅙㅚㅝㅞㅟㅢ" || b.tail.length == 2)
        }
    }

    @Test fun wordSort() = each(GameType.WordSort) { spec, level ->
        spec as RoundSpec.WordSort
        assertEquals(if (level == 1) 2 else 3, spec.bins.size)
        assertEquals(when (level) { 1 -> 6; 2 -> 8; else -> 9 }, spec.tiles.size)
        val byType = spec.bins.all { it in setOf("Verbs", "Adjectives", "Nouns") }
        for ((item, bin) in spec.tiles) {
            if (byType) assertEquals(spec.bins[bin], wordType(item))
            else assertEquals(listOf(bin), spec.bins.indices.filter { belongs(spec.bins[it].lowercase(), item.meaning) })
        }
    }

    @Test fun grammarFixer() = each(GameType.GrammarFixer) { spec, level ->
        spec as RoundSpec.GrammarFixer
        assertEquals(if (level == 1) 2 else 3, spec.options.size)
        assertTrue(spec.correct in spec.options && spec.words[spec.wrong].removePrefix(spec.noun) !in spec.options)
        assertTrue(spec.words.size in when (level) { 1 -> 2..4; 2 -> 4..6; else -> 2..8 })
        // Only the right particle is grammatical after the noun; the wrong one is in the sentence.
        val pair = PARTICLE_PAIRS.single { spec.correct in listOf(it.vowel, it.consonant) }
        assertEquals(spec.correct, pair.correctAfter(spec.noun))
        assertEquals(spec.noun + pair.wrongAfter(spec.noun), spec.words[spec.wrong])
        assertTrue(spec.options.filter { it != spec.correct }.all { o -> PARTICLE_PAIRS.any { it != pair && it.wrongAfter(spec.noun) == o } })
    }

    @Test fun wordRecall() = each(GameType.WordRecall) { spec, level ->
        spec as RoundSpec.WordRecall
        assertEquals(level + 2, spec.sequence.size)
        assertEquals(spec.sequence.size, spec.sequence.map { it.blocks.first() }.toSet().size)
        assertTrue(spec.sequence.all { it.blocks.size in 1..3 })
        assertNotEquals(spec.sequence, spec.tiles)
    }

    @Test fun hangulTyping() = each(GameType.HangulTyping) { spec, level ->
        spec as RoundSpec.HangulTyping
        assertTrue(Evaluators.typing(spec, spec.item.korean))
        if (level < 3) assertEquals(ItemKind.WORD, spec.item.kind)
    }

    @Test fun wordPath() = each(GameType.WordPath) { spec, level ->
        spec as RoundSpec.WordPath
        assertEquals(if (level == 1) 3 else 4, spec.size)
        assertEquals(1, countPaths(spec.grid, spec.size, spec.item.blocks))
        spec.path.zipWithNext().forEach { (a, b) -> assertTrue(b in neighbours(a, spec.size)) }
        assertEquals(spec.path.size, spec.path.toSet().size)
        assertEquals(Evaluators.PathCheck.Target, Evaluators.path(spec, spec.path, BANK))
    }

    @Test fun soundDetective() = each(GameType.SoundDetective) { spec, _ ->
        when (spec) {
            is RoundSpec.SoundDetective.SameOrDifferent -> {
                assertTrue(spec.same || jamoDistance(spec.first.korean, spec.second.korean) == 1)
                assertEquals(spec.same, spec.explanation == null)
            }
            is RoundSpec.SoundDetective.CameTwice -> {
                assertEquals(4, spec.played.size)
                assertEquals(2, spec.played.count { it == spec.twice })
                noSharedSense(spec.words)
            }
            is RoundSpec.SoundDetective.WhatOrder -> assertNotEquals(spec.order, spec.tiles)
            else -> error("not a Sound Detective round")
        }
    }

    @Test
    fun theFixedListIsUsedOnlyWithoutPairsInTheNotes() {
        val paired = minimalPairsFromNotes(BANK).flatMap { listOf(it.first.key, it.second.key) }.toSet()
        val noPairs = GameBank(WORDS.filter { it.key !in paired }, SENTENCES)
        assertTrue(minimalPairsFromNotes(noPairs).isEmpty())
        val modes = SEEDS.mapNotNull { FACTORIES.getValue(GameType.SoundDetective).build(ctx(1, it, noPairs)) as? RoundSpec.SoundDetective.SameOrDifferent }
        assertTrue(modes.isNotEmpty() && modes.all { it.first.extra && it.first.key == null })
    }

    @Test
    fun everyGameIsAvailableWithThisBankAndTheVoice() {
        for (game in GameType.entries) assertTrue("$game", available(game, BANK, voice = true, mic = true))
        assertTrue(GameType.entries.filter { it.needsVoice }.none { available(it, BANK, voice = false, mic = true) })
        // PR 11: Speak It only with the speech recognizer ready and the microphone not refused.
        assertEquals(listOf(GameType.SpeakIt), GameType.entries.filter { it.needsMic })
        assertTrue(GameType.entries.filter { it.needsMic }.none { available(it, BANK, voice = true, mic = false) })
        assertTrue(GameType.entries.none { available(it, GameBank(WORDS.take(5), SENTENCES), voice = true, mic = true) })
    }

    @Test
    fun roundRandomIsTheSameForTheSameSeedAndRound() {
        assertEquals(roundRandom(42, 3).nextLong(), roundRandom(42, 3).nextLong())
        assertNotEquals(roundRandom(42, 3).nextLong(), roundRandom(42, 4).nextLong())
        assertNotNull(compose('ㄱ', 'ㅏ'))
    }

    // FASE_11_PLAN PR 10 (F7): Hangul Wordle and Crossword.

    @Test fun hangulWordle() = each(GameType.HangulWordle) { spec, level ->
        spec as RoundSpec.HangulWordle
        assertTrue(spec.item.blocks.size in 2..3)
        assertEquals(spec.item.blocks.flatMap { com.dero.juststudykorean.korean.hangul.jamoOf(it.single()) }, spec.jamo)
        assertEquals(wordleLength(BANK), spec.jamo.size) // one length for the whole session: a fixed grid
        assertEquals(if (level == 3) 5 else 6, spec.attempts)
        assertEquals(if (level == 1) spec.item.blocks.first() else null, spec.firstSyllable)
    }

    @Test
    fun crossword() {
        for (level in 1..3) {
            val made = SEEDS.mapNotNull { seed -> FACTORIES.getValue(GameType.Crossword).build(ctx(level, seed, CROSS_BANK))?.let { it as RoundSpec.Crossword } }
            println("Crossword level $level: ${made.size}/${SEEDS.count()} seeds fit")
            assertTrue("level $level: most seeds fit (${made.size})", made.size >= SEEDS.count() * 9 / 10)
            for (spec in made) {
                assertEquals(level, spec.level)
                assertEquals(when (level) { 1 -> 4; 2 -> 5; else -> 7 }, spec.entries.size)
                assertTrue(spec.rows <= 7 && spec.cols <= 7)
                noSharedSense(spec.entries.map { it.item })
                val cells = spec.solution() // every crossing agrees, no word overwrites another
                for (e in spec.entries) assertEquals(e.item.blocks, e.cells(spec.cols).map { cells.getValue(it) })
                assertTrue("every word crosses another", spec.entries.all { e -> spec.entries.any { o -> o != e && (o.cells(spec.cols) intersect e.cells(spec.cols).toSet()).isNotEmpty() } })
                assertEquals(if (level == 1) spec.entries.size else 0, spec.givens.size)
                assertEquals(spec.entries.map { it.number }.distinct().sorted(), spec.entries.map { it.number }.distinct())
            }
            assertEquals(made.first(), FACTORIES.getValue(GameType.Crossword).build(ctx(level, SEEDS.first { s -> FACTORIES.getValue(GameType.Crossword).build(ctx(level, s, CROSS_BANK)) != null }, CROSS_BANK)))
        }
        assertNull(FACTORIES.getValue(GameType.Crossword).build(ctx(1, 0, GameBank(emptyList(), emptyList()))))
    }

    // FASE_11_PLAN PR 11 (F7): Conjugation Runner and Speak It.

    @Test fun conjugationRunner() = each(GameType.ConjugationRunner) { spec, level ->
        spec as RoundSpec.ConjugationRunner
        assertEquals(RUNNER_FORMS, spec.questions.size)
        assertEquals(when (level) { 1 -> 6000; 2 -> 4500; else -> 3500 }, spec.fallMs)
        for (q in spec.questions) {
            val right = com.dero.juststudykorean.korean.grammar.conjugate(q.item.korean, q.ending, com.dero.juststudykorean.korean.grammar.wordKindOf(q.item.korean, q.item.meaning))
            assertTrue("${q.item.korean} ${q.ending}: only verbs and endings conjugate knows", q.answer in right)
            assertEquals(if (level == 1) 3 else 4, q.options.size)
            assertEquals(q.options.size, q.options.distinct().size)
            assertEquals("one right form on screen", 1, q.options.count { it in right })
            if (level == 1) assertTrue(q.ending in RUNNER_LEVEL_1)
        }
        assertEquals("no pair twice", spec.questions.size, spec.questions.map { it.item.key to it.ending }.distinct().size)
        assertTrue("several verbs", spec.questions.map { it.item.key }.distinct().size >= 3)
        assertEquals(spec.questions.map { it.item.key }.distinct(), spec.targets)
    }

    @Test fun speakIt() = each(GameType.SpeakIt) { spec, level ->
        spec as RoundSpec.SpeakIt
        assertTrue(spec.item.blocks.size <= when (level) { 1 -> 2; 2 -> 3; else -> 5 })
        val mine = englishSenses(spec.item.meaning)
        assertTrue("the English points to one word of the notes", WORDS.none { it != spec.item && (englishSenses(it.meaning) intersect mine).isNotEmpty() })
        assertEquals(listOf(spec.item.key), spec.targets)
    }
}
