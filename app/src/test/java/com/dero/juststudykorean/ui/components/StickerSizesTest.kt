package com.dero.juststudykorean.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ui.testing.RegisterComponentActivity
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * design/polish.md P-01: the box of a button is its face plus the edge that hangs below it. With an icon the face holds
 * no text (this mode of Robolectric does not measure fonts), so only the minimum heights count.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StickerSizesTest {
    @get:Rule(order = 0) val activity = RegisterComponentActivity()
    @get:Rule(order = 1) val compose = createComposeRule()

    private fun heightOf(size: StickerSize) {
        compose.setContent { JustStudyTheme(darkTheme = false) { StickerButton("Go", {}, Modifier.testTag("b"), size = size, icon = Lucide.Check) } }
        compose.onNodeWithTag("b").assertHeightIsEqualTo(
            when (size) { StickerSize.Large -> 56.dp; StickerSize.Default -> 48.dp; else -> 39.dp },
        )
    }

    @Test fun large() = heightOf(StickerSize.Large) // 52 + 4
    @Test fun default() = heightOf(StickerSize.Default) // 44 + 4
    @Test fun small() = heightOf(StickerSize.Small) // 36 + 3
}
