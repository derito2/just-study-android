package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.screens.OnDeviceAiRow
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shell components at 411 dp (OPPO Find X8 Pro), light and dark. Compare with design/png/shell-*.png. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class ShellScreenshotTest {

    private fun snap(name: String, fontScale: Float = 1f, content: @Composable (dark: Boolean) -> Unit) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$theme.png") {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    JustStudyTheme(darkTheme = dark) {
                        Column(Modifier.width(Tokens.ScreenW).background(MaterialTheme.colorScheme.background)) { content(dark) }
                    }
                }
            }
        }
    }

    @Test
    fun buttons() = snap("button") {
        Column(Modifier.padding(Tokens.Space4), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            StickerColor.entries.forEach { color ->
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                    StickerButton(color.name, {}, Modifier, null, color, StickerSize.Default, enabled = true, forcePressed = false)
                    StickerButton("Pressed", {}, Modifier, null, color, StickerSize.Default, enabled = true, forcePressed = true)
                    StickerButton("Disabled", {}, Modifier, null, color, StickerSize.Default, enabled = false, forcePressed = false)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                StickerButton("Settings", {}, ko = "설정", size = StickerSize.Small)
                StickerButton("Clear answers", {}, color = StickerColor.Gray, size = StickerSize.Small)
                StickerButton("Continue", {}, ko = "계속")
            }
            StickerButton("Generate quiz", {}, Modifier.width(379.dp), ko = "퀴즈 만들기", color = StickerColor.Go)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TopBar(folded: Boolean, onBack: Boolean = false) {
        val state = if (folded) rememberTopAppBarState(-10_000f, -10_000f) else rememberTopAppBarState()
        JsTopBar(
            title = "Flashcards", ko = "단어 카드",
            subtitle = "Words from your own notes. Review them before you forget them, again.",
            subtitleKo = "네 노트에서 나온 단어. 또 잊어버리기 전에 복습해.",
            scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state),
            onMore = {}, onBack = if (onBack) ({}) else null,
        )
    }

    @Test
    fun topBarOpen() = snap("topbar_open") { TopBar(folded = false) }

    @Test
    fun topBarFolded() = snap("topbar_folded") { TopBar(folded = true, onBack = true) }

    @Test
    fun topBarAt130Percent() = snap("topbar_130", fontScale = 1.3f) { TopBar(folded = false) }

    @Test
    fun bottomBar() = snap("bottombar") {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            TopLevel.entries.forEach { JsBottomBar(current = it, onSelect = {}) }
        }
    }

    @Test
    fun bottomBarAt130Percent() = snap("bottombar_130", fontScale = 1.3f) { JsBottomBar(current = TopLevel.Flashcards, onSelect = {}) }

    @Test
    fun moreSheet() = snap("moresheet") { dark -> MoreSheetContent(darkMode = dark, onDarkMode = {}, onOpen = {}) }

    @Test
    fun themeSwitch() = snap("themeswitch") {
        Row(Modifier.padding(Tokens.Space4), horizontalArrangement = Arrangement.spacedBy(Tokens.Space4)) {
            ThemeSwitch(checked = false, onCheckedChange = {})
            ThemeSwitch(checked = true, onCheckedChange = {})
        }
    }

    @Test
    fun states() = snap("states") {
        Column(Modifier.padding(Tokens.Space4), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            EmptyState(
                Lucide.Layers, "You don't have any cards yet", "아직 카드가 없어요",
                "Create cards from a processed document and its deck will show up here.",
                action = "Create cards",
            )
            LoadingState("Loading decks…", Modifier, delayMs = 0, progress = 0.4f)
            ErrorState("Something went wrong reading the database.", onRetry = {}, title = "Couldn't load your decks")
        }
    }

    /** AiMark (design/shell.md, phase 6): the full mark and the short one. */
    @Test
    fun aiMark() = snap("aimark") {
        Column(Modifier.padding(Tokens.Space4), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            AiMark()
            AiMark(short = true)
        }
    }

    /** Settings → On-device AI in its 4 states (design/grammar-calendar-settings.md). */
    @Test
    fun onDeviceAi() = snap("settings_ai") {
        Column {
            for (status in listOf(NanoStatus.Available, NanoStatus.Downloadable, NanoStatus.Downloading(42), NanoStatus.Unavailable)) OnDeviceAiRow(status) {}
        }
    }
}
