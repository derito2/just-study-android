package com.dero.juststudykorean.ui.flashcards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.FlashcardsEntity
import com.dero.juststudykorean.data.flashcards.CardDetails
import com.dero.juststudykorean.data.flashcards.CardSource
import com.dero.juststudykorean.data.flashcards.Deck
import com.dero.juststudykorean.data.flashcards.FlashcardStats
import com.dero.juststudykorean.data.flashcards.LastCard
import com.dero.juststudykorean.data.flashcards.StudyCard
import com.dero.juststudykorean.data.prefs.CardDirection
import com.dero.juststudykorean.korean.Scheduling
import com.dero.juststudykorean.ui.components.JsBottomBar
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import com.dropbox.differ.ImageComparator
import com.dropbox.differ.Mask
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import kotlin.math.abs
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Flashcards with fixed fake data, at 411 dp. Compare with design/png/flashcards-*.png. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h905dp-420dpi")
class FlashcardsScreenshotTest {
    private fun deck(id: Long, name: String, total: Int, new: Int, learning: Int, due: Int, word: String?, custom: Boolean = false) =
        Deck(id, custom, false, emptyList(), name, total, new, learning, due, new + due, null, word?.let { LastCard(it, "") }, null)

    private val decks = listOf(
        deck(1, "Unit 3 · At school", 120, 12, 5, 8, "도서관"),
        deck(2, "Lesson 5 · Verbs", 86, 30, 2, 4, "바쁘다"),
        deck(-1, "Practice: -아서/어서", 10, 10, 0, 0, "바빠서", custom = true),
        deck(3, "Café and food", 64, 0, 0, 0, "메뉴판"),
    )
    private val ui = DecksUi(decks, FlashcardStats(475, 48, 12, 0, 305, null))

    private fun card(front: String, back: String, roman: String?, example: String? = null, exampleEn: String? = null) =
        FlashcardsEntity(7, null, front, back, front, back, roman, example, null, 1, 2.5, 0, 0, 0, "1:4:0", exampleEn, null, null)

    private val near = StudyCard(card("근처", "near, nearby", "geuncheo"), true, CardDirection.KoToEn)
    private val library = StudyCard(card("도서관", "library", null).copy(id = 8, sourceChunkId = "1:2:0"), false, CardDirection.EnToKo)
    private val details = mapOf(
        7L to CardDetails(examples = listOf("학교 근처에 살아요." to "I live near the school.")),
        8L to CardDetails(examples = listOf("도서관에서 공부해요." to "I study at the library.")),
    )
    private val sources = mapOf(7L to CardSource("Unit 3 · At school", 4), 8L to CardSource("Unit 3 · At school", 2))

    // Edit mode greys out the buttons: their small semi-transparent Korean text is antialiased a bit differently
    // on Linux (CI) and macOS (up to 8/255 per channel, invisible). Only these two captures ignore differences that
    // small and still allow at most 0.1 % of changed pixels; every other capture stays exact.
    private val koreanAlphaTolerance = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(imageComparator = ChannelToleranceComparator(maxSteps = 8), changeThreshold = 0.001f),
    )

    @Test
    fun toleranceIgnoresOnlySmallChanges() {
        fun image(vararg grey: Int) = object : com.dropbox.differ.Image {
            override val width = grey.size
            override val height = 1
            override fun getPixel(x: Int, y: Int) = com.dropbox.differ.Color(grey[x] / 255f, grey[x] / 255f, grey[x] / 255f, 1f)
        }
        val result = ChannelToleranceComparator(maxSteps = 8).compare(image(100, 100, 100), image(100, 108, 109), null)
        org.junit.Assert.assertEquals(1, result.pixelDifferences) // 8 steps ignored, 9 counted
    }

    private fun snap(name: String, fontScale: Float = 1f, bar: Boolean = true, options: RoborazziOptions = RoborazziOptions(), content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png", options) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalFrozenMotion provides true) {
                JustStudyTheme(darkTheme = false) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { content() }
                        if (bar) JsBottomBar(current = TopLevel.Flashcards, onSelect = {})
                    }
                }
            }
        }

    @Composable
    private fun Decks(ui: DecksUi, editing: Boolean = false, merge: Pair<Int, Int>? = null) = DecksContent(
        ui, Scheduling(), remember { SnackbarHostState() }, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> true }, {}, { null },
        editingAtStart = editing, mergeAtStart = merge, mergeNameAtStart = if (merge != null) "School + -아서" else "",
    )

    @Composable
    private fun Study(ui: StudyUi, direction: CardDirection = CardDirection.KoToEn, typing: Boolean = false) =
        StudyContent(ui, "Unit 3 · At school", direction, typing, Scheduling(), {}, {}, {}, {}, {}, {})

    @Test
    fun decks() = snap("flashcards_decks") { Decks(ui) }

    @Test
    fun decksEdit() = snap("flashcards_decks_edit", options = koreanAlphaTolerance) { Decks(ui, editing = true) }

    @Test
    fun mergeDialog() = snap("flashcards_merge", options = koreanAlphaTolerance) { Decks(ui, editing = true, merge = 0 to 2) }

    @Test
    fun decksAt130Percent() = snap("flashcards_decks_130", fontScale = 1.3f) { Decks(ui) }

    @Test
    fun front() = snap("flashcards_front", bar = false) {
        Study(StudyUi(loading = false, queue = List(12) { near }, details = details, sources = sources))
    }

    @Test
    fun back() = snap("flashcards_back", bar = false) {
        Study(StudyUi(loading = false, queue = List(12) { near }, flipped = true, details = details, sources = sources))
    }

    @Test
    fun typedAlmost() = snap("flashcards_typed", bar = false) {
        Study(StudyUi(loading = false, queue = List(11) { library }, flipped = true, typed = "도서간", details = details, sources = sources), CardDirection.EnToKo, typing = true)
    }

    @Test
    fun noCardsDue() = snap("flashcards_none_due", bar = false) { Study(StudyUi(loading = false)) }
}

/** A pixel counts as changed only if one of its channels moved more than [maxSteps] of 255. */
private class ChannelToleranceComparator(private val maxSteps: Int) : ImageComparator {
    private fun steps(a: Float, b: Float) = Math.round(abs(a - b) * 255f) // differ's channels are byte / 255f

    override fun compare(left: com.dropbox.differ.Image, right: com.dropbox.differ.Image, diff: Mask?): ImageComparator.ComparisonResult {
        val w = minOf(left.width, right.width)
        val h = minOf(left.height, right.height)
        var changed = maxOf(left.width, right.width) * maxOf(left.height, right.height) - w * h // a size change counts fully
        for (y in 0 until h) for (x in 0 until w) {
            val a = left.getPixel(x, y)
            val b = right.getPixel(x, y)
            if (maxOf(steps(a.r, b.r), steps(a.g, b.g), steps(a.b, b.b), steps(a.a, b.a)) > maxSteps) {
                changed++
                diff?.setValue(x, y, 1f)
            }
        }
        return ImageComparator.ComparisonResult(changed, maxOf(left.width, right.width) * maxOf(left.height, right.height), w, h)
    }
}
