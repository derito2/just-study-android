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

    @Test
    fun entryRunsInFromTheLeftAndLandsOnHisSpot() {
        assertEquals(1000, Tracks.A8.total)
        assertEquals(-190f, Tracks.A8.sample(0f, rest).x, 0.01f)
        assertEquals(0f, Tracks.A8.sample(400f, rest).x, 0.01f) // two hops of 200 ms
        assertEquals(12f, Tracks.A8.sample(300f - 100f + 100f - 0f, rest).y.coerceAtMost(12f), 12f) // hops are 12 dp high
        assertEquals(0f, Tracks.A8.sample(1000f, rest).x, 0.01f)
    }

    @Test
    fun theStreakTracksFollowTheSheet() {
        assertEquals(1200, Tracks.DoubleJump.total)
        assertEquals(34f, Tracks.DoubleJump.sample(330f, rest).y, 0.01f)
        assertEquals(24f, Tracks.DoubleJump.sample(680f, rest).y, 0.01f) // the second take-off at ~70 % of the height
        assertEquals(48f, Tracks.Somersault.sample(250f, rest).y, 0.01f)
        assertEquals(-360f, Tracks.Somersault.sample(530f, rest).rot, 0.01f) // 420 ms of flight (110 → 530)
        assertEquals(1.28f, Tracks.Somersault.sample(640f, rest).sx, 0.001f) // lands (1.28, 0.74)
        assertEquals(0.74f, Tracks.Somersault.sample(640f, rest).sy, 0.001f)
    }

    @Test
    fun streakEventsPickTheirTrackAndTheFlameLightsAndGoesOut() {
        val d = StageDirector()
        d.handle(StageEvent.RoundWon, 0f, rest)
        d.handle(StageEvent.Streak(3), 10f, rest) // the flame lights on top of A3: the track stays
        assertEquals("A3", d.playing)
        assertEquals(0f, d.flameScale(10f)!!, 0.01f)
        assertTrue(d.flameScale(10f + 192f)!! > 1.1f) // overshoots to 1.3 on the way
        assertNull(d.flameScale(10f + 330f)) // then the streak decides (steady 1)
        d.handle(StageEvent.Streak(5), 100f, rest)
        assertEquals("A3-double", d.playing)
        d.handle(StageEvent.Streak(20), 2000f, rest)
        assertEquals("A3-flip", d.playing)
        d.handle(StageEvent.Streak(15), 5000f, rest)
        assertEquals("A3-flip", d.playing) // 15 is not a multiple of 10: nothing new
        d.handle(StageEvent.StreakLost, 9000f, rest)
        assertEquals(0f, d.flameScale(9150f) ?: 0f, 0.01f) // out in 150 ms
        assertNotNull(d.smoke(9100f)); assertNull(d.smoke(9400f)) // the ring lasts 300 ms
    }

    @Test
    fun aFingerAnticipatesAndReleasesInTimeAndThinkingNeverInterrupts() {
        val d = StageDirector()
        d.handle(StageEvent.Touch(0.9f), 0f, rest)
        assertEquals("A1", d.playing)
        assertEquals(6f, d.pose(500f)!!.tilt, 0.01f) // holds while the finger is down
        assertTrue(d.lookX > 0f)
        d.handle(StageEvent.Release, 600f, rest)
        assertEquals("A1-release", d.playing)
        assertNull(d.pose(600f + 151f))
        d.handle(StageEvent.RoundWon, 1000f, rest)
        d.handle(StageEvent.Thinking, 1100f, rest) // never cuts a result animation
        assertEquals("A3", d.playing)
    }
}
