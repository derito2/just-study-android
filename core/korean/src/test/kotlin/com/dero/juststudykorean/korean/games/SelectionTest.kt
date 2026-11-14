package com.dero.juststudykorean.korean.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** FASE_8_PLAN 5.2–5.4: the selector, the level and the item picker. */
class SelectionTest {
    private val all = GameType.entries.associateWith { 40 }
    private val levels = GameType.entries.associateWith { 1 }

    @Test
    fun neverTheSameGameTwiceInARowNorTheSameFamilyWhenThereAreOthers() {
        repeat(500) { seed ->
            val last = GameType.entries[seed % 13]
            val next = selectGame(all, listOf(PlayedRound(last, Outcome.WON, emptyList())), levels, Random(seed))!!
            assertNotEquals(last, next)
            assertNotEquals(last.gesture, next.gesture)
        }
    }

    @Test
    fun withOneGameTheSessionStillPlays() {
        val one = mapOf(GameType.WordMatch to 40)
        assertEquals(GameType.WordMatch, selectGame(one, listOf(PlayedRound(GameType.WordMatch, Outcome.WON, emptyList())), levels, Random(1)))
    }

    @Test
    fun afterTwoLostRoundsTheGentleGamesWeighMore() {
        val lost = listOf(PlayedRound(GameType.WordPath, Outcome.LOST, emptyList()), PlayedRound(GameType.Hangman, Outcome.LOST, emptyList()))
        val won = lost.map { it.copy(outcome = Outcome.WON) }
        fun gentleShare(history: List<PlayedRound>) = (0 until 2000).count { selectGame(all, history, levels, Random(it))!!.gentle } / 2000.0
        assertTrue("${gentleShare(lost)} vs ${gentleShare(won)}", gentleShare(lost) > gentleShare(won) + 0.1)
    }

    @Test
    fun theLevelGoesUpAfterThreeWonAndDownAfterTwoLost() {
        val w = Outcome.WON
        val l = Outcome.LOST
        assertEquals(1, levelFor(emptyList()))
        assertEquals(2, levelFor(listOf(w, w, w))) // newest first
        assertEquals(3, levelFor(listOf(w, w, w, w, w, w)))
        assertEquals(1, levelFor(listOf(l, l, w, w, w)))
        assertEquals(1, levelFor(listOf(l, l)))
        assertEquals(1, sessionLevel(2, listOf(PlayedRound(GameType.Hangman, l, emptyList()), PlayedRound(GameType.WordMatch, l, emptyList()))))
        assertEquals(2, sessionLevel(2, listOf(PlayedRound(GameType.Hangman, w, emptyList()))))
    }

    @Test
    fun thePickerWeighsWeakNewAndMasteredItemsAndRespectsTheCooldown() {
        val day = 86_400_000L
        val now = 100 * day
        fun item(k: String) = GameItem(k, ItemKind.WORD, k, listOf(k), k, listOf(k), 1, "d", 1)
        val weak = item("weak"); val fresh = item("new"); val mastered = item("mastered"); val cold = item("cold")
        val progress = mapOf(
            "weak" to ItemStats(seen = 4, correct = 1, wrong = 3, streak = 0, lastSeenAt = now - 10 * day, lastWrongAt = now - 10 * day),
            "mastered" to ItemStats(seen = 5, correct = 5, wrong = 0, streak = 3, lastSeenAt = now - day, lastWrongAt = null),
            "cold" to ItemStats(seen = 1, correct = 1, wrong = 0, streak = 1, lastSeenAt = now - 10 * day, lastWrongAt = null),
        )
        assertEquals(3.0, itemWeight(weak, progress, now), 1e-9)
        assertEquals(1.5, itemWeight(fresh, progress, now), 1e-9)
        assertEquals(0.3, itemWeight(mastered, progress, now), 1e-9)
        assertEquals(1.0, itemWeight(cold, progress, now), 1e-9)
        val picker = ItemPicker(progress, now)
        val firsts = (0 until 2000).map { picker.choose(listOf(weak, fresh, mastered, cold), 1, Random(it)).single().key }
        assertTrue(firsts.count { it == "weak" } > firsts.count { it == "cold" } * 2)
        assertTrue(firsts.count { it == "mastered" } < firsts.count { it == "cold" })
        assertEquals(4, picker.choose(listOf(weak, fresh, mastered, cold), 4, Random(1)).toSet().size) // without repeats
        val ctx = RoundContext(GameBank(listOf(weak, fresh), emptyList()), 1, Random(1), picker, avoid = setOf("weak"))
        repeat(50) { assertEquals("new", ctx.pick(listOf(weak, fresh), 1).single().key) }
    }

    @Test
    fun progressAfterARound() {
        val p = progressAfter(null, wrong = false, at = 5)
        assertEquals(ItemStats(1, 1, 0, 1, 5, null), p)
        assertEquals(ItemStats(2, 1, 1, 0, 9, 9), progressAfter(p, wrong = true, at = 9))
    }
}
