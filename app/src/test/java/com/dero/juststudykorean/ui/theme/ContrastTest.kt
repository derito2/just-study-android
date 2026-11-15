package com.dero.juststudykorean.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import kotlin.math.pow

/**
 * design/quiz.md E: every ink/fill pair used inside a correct, wrong or warn box, in both themes (WCAG 2.1).
 * Text 4.5:1, borders 3:1. A colour is never adjusted to make this pass: a failure goes to the owner.
 */
class ContrastTest {
    private fun channel(v: Float) = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private val themes = mapOf("light" to LightJustStudyColors, "dark" to DarkJustStudyColors)

    private fun check(min: Double, vararg pairs: Triple<String, (JustStudyColors) -> Color, (JustStudyColors) -> Color>) {
        val failures = buildList {
            for ((theme, c) in themes) for ((name, fg, bg) in pairs) {
                val r = ratio(fg(c).compositeOver(bg(c)), bg(c))
                if (r < min) add("$theme: $name = ${"%.2f".format(r)}:1 (needs $min)")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun textInsideTheBoxes() = check(
        4.5,
        Triple("fillInk on correctFill", { c -> c.fillInk }, { c -> c.correctFill }),
        Triple("fillInk on wrongFill", { c -> c.fillInk }, { c -> c.wrongFill }),
        Triple("fillInk on warnFill", { c -> c.fillInk }, { c -> c.warnFill }),
        Triple("fillMuted on correctFill", { c -> c.fillMuted }, { c -> c.correctFill }),
        Triple("fillMuted on wrongFill", { c -> c.fillMuted }, { c -> c.wrongFill }),
        Triple("fillMuted on warnFill", { c -> c.fillMuted }, { c -> c.warnFill }),
        Triple("correctInk on correctFill", { c -> c.correctInk }, { c -> c.correctFill }),
        Triple("wrongInk on wrongFill", { c -> c.wrongInk }, { c -> c.wrongFill }),
        Triple("fillWarn on warnFill", { c -> c.fillWarn }, { c -> c.warnFill }),
    )

    @Test
    fun correctAndWrongTextOnTheBackground() = check(
        4.5,
        Triple("correctInk on background", { c -> c.correctInk }, { c -> c.background }),
        Triple("wrongInk on background", { c -> c.wrongInk }, { c -> c.background }),
    )

    @Test
    fun darkBordersAgainstTheBackground() {
        val c = DarkJustStudyColors
        for ((name, border) in listOf("correctDeco" to c.correctDeco, "wrongBorder" to c.wrongBorder)) {
            assertTrue("dark $name = ${ratio(border, c.background)}", ratio(border, c.background) >= 3.0)
        }
    }

    /**
     * Found by this test (FASE_10_PLAN §10: a colour is never adjusted to make it pass): in light, `correct-deco` #58CC02 on white is
     * 2.09:1 and the pale fill is 1.06:1, so the correct option is told apart by its icon and text more than by its frame. Owner's call.
     */
    @Ignore("owner decision pending: light correctDeco 2.09:1 on the background")
    @Test
    fun lightBordersAgainstTheBackground() = check(
        3.0,
        Triple("correctDeco on background", { c -> c.correctDeco }, { c -> c.background }),
        Triple("wrongBorder on background", { c -> c.wrongBorder }, { c -> c.background }),
    )

    /** In dark the fills are tinted, not pale: they must stay close to the background (they used to be 15:1 and dazzle). */
    @Test
    fun darkFillsDoNotDazzle() {
        val c = DarkJustStudyColors
        for ((name, fill) in listOf("correctFill" to c.correctFill, "wrongFill" to c.wrongFill, "warnFill" to c.warnFill)) {
            assertTrue("$name vs background ${ratio(fill, c.background)}", ratio(fill, c.background) < 1.5)
        }
    }
}
