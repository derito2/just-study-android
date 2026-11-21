package com.dero.juststudykorean.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** design/polish.md P-06, M-5: the KPI figures count up from 0 in 400 ms, only the first time after the app starts. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class KpiCountUpTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun freshLaunch() { KpiCountUp.reset() }

    @Test
    fun firstTimeItCountsFromZero() {
        var shown: Long? by mutableStateOf(-1L)
        compose.mainClock.autoAdvance = false
        compose.setContent { JustStudyTheme(darkTheme = false) { shown = rememberKpiCount(100L) } }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        assertEquals("starts at 0", 0L, shown)
        repeat(8) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        assertTrue("counting: $shown", shown!! in 1..99)
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        assertEquals(100L, shown)
    }

    @Test
    fun afterTheDashboardHasShownItItIsThereAtOnce() {
        KpiCountUp.consume()
        var shown: Long? by mutableStateOf(-1L)
        compose.mainClock.autoAdvance = false
        compose.setContent { JustStudyTheme(darkTheme = false) { shown = rememberKpiCount(100L) } }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        assertEquals(100L, shown)
    }

    @Test
    fun whileLoadingThereIsNoNumber() {
        var shown: Long? by mutableStateOf(-1L)
        compose.setContent { JustStudyTheme(darkTheme = false) { shown = rememberKpiCount(null) } }
        compose.waitForIdle()
        assertNull(shown)
    }
}
