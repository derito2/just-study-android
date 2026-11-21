package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** design/polish.md P-06: M-1 (progress), M-2 (the figure that counts), M-7 (the banner comes in) and M-8 (a section folds). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MicroAnimationsTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun m2PulseGoesUpAndComesBack() {
        assertEquals(1f, Motion.pulse(0f), 0.001f)
        assertEquals(1.18f, Motion.pulse(0.5f), 0.001f)
        assertEquals(1f, Motion.pulse(1f), 0.001f)
    }

    @Test
    fun m7BannerComesInFromEightDpBelowAndFadesIn() {
        assertEquals(8f, Motion.bannerOffsetDp(0f), 0.001f)
        assertEquals(0f, Motion.bannerOffsetDp(1f), 0.001f)
        assertEquals(0f, Motion.bannerAlpha(0f), 0.001f)
        assertEquals(1f, Motion.bannerAlpha(1f), 0.001f)
        assertEquals(1f, Motion.bannerAlpha(1.2f), 0.001f) // `Spring` overshoots: the alpha does not
    }

    private fun current(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    @Test
    fun m1ProgressBarGrowsOverTime() {
        var p by mutableStateOf(0.1f)
        compose.mainClock.autoAdvance = false
        compose.setContent { JustStudyTheme(darkTheme = false) { ProgressBar(p, Modifier.testTag("bar")) } }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0.1f, current("bar"), 0.001f)
        compose.runOnIdle { p = 0.2f }
        repeat(6) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } // a frame to recompose, the next ones move it
        val midway = current("bar")
        assertTrue("midway $midway", midway > 0.1f && midway < 0.2f)
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(0.2f, current("bar"), 0.001f)
    }

    @Test
    fun m8SectionFoldsAndUnfolds() {
        var open by mutableStateOf(false)
        compose.setContent {
            JustStudyTheme(darkTheme = false) {
                FoldSection("Question types", "문제 유형", "7 of 9 selected", open, { open = !open }) {
                    Column { Text("Fill in the blank") }
                }
            }
        }
        compose.onNodeWithText("Fill in the blank").assertDoesNotExist()
        compose.onNodeWithText("7 of 9 selected").assertIsDisplayed() // closed: the one-line summary
        compose.onNodeWithText("Question types").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Fill in the blank").assertIsDisplayed()
        compose.onNodeWithText("7 of 9 selected").assertDoesNotExist() // open: no summary
    }
}
