package com.dero.juststudykorean.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** design/polish.md P-12: a trend without drama: no arrow or percentage when there is nothing this week, "new" when last week was empty. */
class TrendTest {
    @Test fun nothingThisWeekIsNotMinusOneHundredPercent() {
        assertEquals(Trend(TrendDirection.None, "none yet this week"), wordsTrend(0, 5))
        assertEquals(Trend(TrendDirection.None, "none yet this week"), wordsTrend(0, null))
        assertEquals(Trend(TrendDirection.None, "none yet this week"), accuracyTrend(null, 0.8))
    }

    @Test fun somethingAfterAnEmptyWeekIsNew() {
        assertEquals(Trend(TrendDirection.New, "new this week"), wordsTrend(3, 0))
        assertEquals(Trend(TrendDirection.New, "new this week"), accuracyTrend(0.9, null))
    }

    @Test fun theRestStaysAsTheWebHasIt() {
        assertEquals(Trend(TrendDirection.Up, "+100%"), wordsTrend(6, 3))
        assertEquals(Trend(TrendDirection.Down, "-50%"), wordsTrend(3, 6))
        assertEquals(Trend(TrendDirection.Same, "0%"), wordsTrend(3, 3))
        assertEquals(Trend(TrendDirection.Up, "+10%"), accuracyTrend(0.9, 0.8))
        assertEquals(Trend(TrendDirection.Down, "-10%"), accuracyTrend(0.7, 0.8))
        assertEquals(Trend(TrendDirection.Same, "0%"), accuracyTrend(0.8, 0.8))
    }
}
