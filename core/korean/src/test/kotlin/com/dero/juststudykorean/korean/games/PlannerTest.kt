package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.games.FactoriesTest.Companion.BANK
import org.junit.Assert.assertEquals
import org.junit.Test

/** 5.1/5.5: a session with a single game available still plays; the plan is a pure function of the state. */
class PlannerTest {
    private fun inputs(available: Map<GameType, Int>) = SessionInputs(BANK, 7, 0, available, emptyList(), emptyMap(), emptyMap())

    @Test
    fun aSingleGameKeepsPlaying() {
        var state = inputs(mapOf(GameType.WordMatch to 40))
        repeat(30) { n ->
            val plan = planRound(state, n)!!
            assertEquals(GameType.WordMatch, plan.game)
            state = state.after(plan.game, RoundResult(Outcome.WON, 0, 1000, plan.spec.targets))
        }
    }

    @Test
    fun theSameStateGivesTheSameRound() {
        val state = inputs(availableGames(BANK, voice = true))
        repeat(20) { assertEquals(planRound(state, it), planRound(state, it)) }
    }
}
