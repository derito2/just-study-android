package com.dero.juststudykorean.ui.games

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.games.stage.DalFace
import com.dero.juststudykorean.ui.games.stage.GameStage
import com.dero.juststudykorean.ui.games.stage.STAGE_DP
import com.dero.juststudykorean.ui.games.stage.StageMood
import com.dero.juststudykorean.ui.games.stage.StageScope
import com.dero.juststudykorean.ui.games.stage.beomIdle
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/games-characters.md 2–5.1 and 8: the stage v2 (196 dp), Beom's three poses, Deung with and without the flame, Dal's faces; all still. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h300dp-420dpi")
class StageScreenshotTest {
    private fun snap(name: String, setup: StageScope.() -> Unit = {}) {
        val scope = StageScope().apply(setup)
        captureRoboImage("src/test/screenshots/$name.png") {
            CompositionLocalProvider(LocalFrozenMotion provides true) {
                JustStudyTheme(darkTheme = false) { GameStage(scope, null, 0, Modifier.fillMaxWidth().height(STAGE_DP.dp)) }
            }
        }
    }

    @Test fun stageRest() = snap("stage_rest")
    @Test fun beomWon() = snap("stage_won") { mood = StageMood.Won }
    @Test fun beomLost() = snap("stage_lost") { mood = StageMood.Lost }
    @Test fun deungWithFlame() = snap("stage_streak") { streak = 4 }
    @Test fun dalNervous() = snap("stage_dal_nervous") { timer = { 0.2f } }
    @Test fun dalSleepy() = snap("stage_dal_sleepy") { dal = DalFace.Sleepy }
    @Test fun dalProud() = snap("stage_dal_proud") { dal = DalFace.Proud }

    @Test
    fun theStageIs196DpAndTheIdleLoopStaysInItsRanges() {
        assertEquals(196f, STAGE_DP)
        var blinked = false
        var t = 0f
        while (t < 120f) {
            val p = beomIdle(t)
            assertTrue(p.sx in 0.985f..1f && p.sy in 1f..1.03f && Math.abs(p.tail) <= 0.4f + 1e-3f)
            if (p.blink < 0.5f) blinked = true
            assertTrue(p.blink in 0.1f - 1e-3f..1f && Math.abs(p.earR) <= 25f + 1e-3f)
            t += 0.01f
        }
        assertTrue("a blink happens within two minutes", blinked)
    }
}
