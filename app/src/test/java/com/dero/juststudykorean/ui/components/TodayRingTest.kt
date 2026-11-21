package com.dero.juststudykorean.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** design/polish.md P-13 and M-6: the ring of today sits outside the cell, and is drawn all the way round as progress goes 0 → 1. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TodayRingTest {
    private fun painted(progress: Float): Pair<Int, Boolean> {
        val bitmap = ImageBitmap(120, 120)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(120f, 120f)) {
            translate(40f, 40f) { drawTodayRing(Color.Red, progress, Size(40f, 40f), cornerRadiusPx = 8f, gapPx = 2f, strokePx = 2f) } // a 40 px cell at (40, 40)
        }
        val px = bitmap.asAndroidBitmap()
        var n = 0
        var insideCell = false
        for (y in 0 until 120) for (x in 0 until 120) if (px.getPixel(x, y) ushr 24 != 0) {
            n++
            if (x in 42..77 && y in 42..77) insideCell = true // the cell's own area, away from its border
        }
        return n to insideCell
    }

    @Test
    fun nothingAtTheStartAndTheWholeRingAtTheEnd() {
        assertEquals(0, painted(0f).first)
        val half = painted(0.5f).first
        val full = painted(1f).first
        assertTrue("half $half full $full", half > 0 && full > half * 1.6f)
    }

    @Test
    fun theRingStaysOutsideTheCell() {
        assertEquals(false, painted(1f).second)
    }
}
