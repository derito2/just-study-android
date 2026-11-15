package com.dero.juststudykorean.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * design/quiz.md E: inside a box with a `correctFill`, `wrongFill` or `warnFill` background the text is `fillInk`/`fillMuted`
 * or `correctInk`/`wrongInk`/`fillWarn`, never the theme's `onSurface`, `ink` or `muted` (in dark they are light on a tinted fill
 * and, before the fix, light on a pale one: 1.06:1). A text search over the sources, like NoMlKitInScreensTest.
 */
class FillInkSourceTest {
    private val ui = File("src/main/java/com/dero/juststudykorean/ui")
    private val fillBackground = Regex("""background\([^)]*\b(correctFill|wrongFill|warnFill)\b""")
    private val forbidden = Regex("""onSurface\b|onSurfaceVariant|\.ink\b|\.muted\b""")

    @Test
    fun noThemeInkInsideAFillBox() {
        assertTrue("run from the app module", ui.isDirectory)
        val offenders = buildList {
            ui.walkTopDown().filter { it.extension == "kt" && "theme" !in it.path }.forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { i, line ->
                    if (!fillBackground.containsMatchIn(line)) return@forEachIndexed
                    // The box and what it holds: the next lines until a line that closes at the box's own indent.
                    val indent = line.takeWhile { it == ' ' }.length
                    for (j in i until minOf(lines.size, i + 14)) {
                        val l = lines[j]
                        if (j > i + 1 && l.isNotBlank() && l.takeWhile { it == ' ' }.length < indent) break
                        if (forbidden.containsMatchIn(l)) add("${file.name}:${j + 1}: ${l.trim().take(110)}")
                    }
                }
            }
        }
        assertTrue("Theme ink inside a fill box:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
