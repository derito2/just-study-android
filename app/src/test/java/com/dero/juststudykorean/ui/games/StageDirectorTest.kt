package com.dero.juststudykorean.ui.games

import com.dero.juststudykorean.ui.games.stage.BeomEyes
import com.dero.juststudykorean.ui.games.stage.BeomPose
import com.dero.juststudykorean.ui.games.stage.StageDirector
import com.dero.juststudykorean.ui.games.stage.StageEvent
import com.dero.juststudykorean.ui.games.stage.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** design/games-characters.md 5 and 9: each track lasts what the sheet says and ends at rest (or holds); events chain. */
class StageDirectorTest {
    private val rest = BeomPose.Rest

    @Test
    fun everyTrackLastsWhatTheSheetSays() {
        assertEquals(400, Tracks.A2.total)
        assertEquals(900, Tracks.A3.total)
        assertEquals(1000, Tracks.A4.keys[4].ms) // A4: 1000 ms, then it holds
        assertTrue(Tracks.A4.hold)
        assertEquals(330, Tracks.A13.total)
        assertEquals(240, Tracks.Resume.total)
        assertEquals(3000, Tracks.Dance.total) // 2 × 4 beats of 300 ms, then the bow in two 300 ms steps
    }

    @Test
    fun theTracksThatReturnEndAtRestWithTheirOwnFinalDetails() {
        val a2 = Tracks.A2.sample(400f, rest)
        assertEquals(1f, a2.sx, 0f); assertEquals(0f, a2.y, 0f)
        val a3 = Tracks.A3.sample(900f, rest)
        assertEquals(1f, a3.sx, 0f); assertEquals(1f, a3.blush, 0f) // blush 1 at the end (620–900)
        assertEquals(rest.copy(), Tracks.A13.sample(330f, rest))
    }

    @Test
    fun aJumpPeaksAtTheSheetsHeight() {
        assertEquals(34f, Tracks.A3.sample(330f, rest).y, 0.01f)
        assertEquals(0f, Tracks.A3.sample(520f, rest).y, 0.01f)
    }

    @Test
    fun anA2AfterWinningStartsFromTheCurrentPose() {
        val d = StageDirector()
        d.handle(StageEvent.RoundWon, 0f, rest)
        val midJump = d.pose(330f)!!
        assertEquals(34f, midJump.y, 0.01f)
        d.handle(StageEvent.PieceCorrect, 330f, rest) // interrupts A3 in the air
        assertEquals("A2", d.playing)
        assertEquals(34f, d.pose(330f)!!.y, 0.01f) // starts where Beom is, not from rest
        assertNull(d.pose(330f + 401f)) // and A0 takes over once A2 is over
    }

    @Test
    fun losingHoldsUntilTheNextRoundAndPausingDuringAWinIsAllowed() {
        val d = StageDirector()
        d.handle(StageEvent.RoundLost, 0f, rest)
        val held = d.pose(5000f)
        assertNotNull(held)
        assertEquals(BeomEyes.Open, held!!.eyes); assertEquals(120f, held.armR, 0f) // pointing at the answer
        d.handle(StageEvent.NextRound, 5100f, rest)
        assertNull(d.pose(5100f))
        d.handle(StageEvent.RoundWon, 6000f, rest)
        d.handle(StageEvent.Paused, 6300f, rest) // pause in the middle of A3
        assertEquals("A9", d.playing)
        assertEquals(BeomEyes.Sleepy, d.pose(7000f)!!.eyes)
        d.handle(StageEvent.Resumed, 7000f, rest)
        assertEquals("Resume", d.playing)
    }

    @Test
    fun theEndPicksTheTrackByHowTheSessionWent() {
        for ((rounds, won, id) in listOf(Triple(0, 0, "A10-none"), Triple(10, 6, "A10-won"), Triple(10, 5, "A10-some"))) {
            val d = StageDirector()
            d.handle(StageEvent.SessionEnded(rounds, won), 0f, rest)
            assertEquals(id, d.playing)
        }
    }
}
