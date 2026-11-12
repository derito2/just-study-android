package com.dero.juststudykorean.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The benchmark app (its own data, filled from a backup): never your app. */
const val TARGET = "com.dero.juststudykorean.benchmark"

private fun any(text: String) = By.text(Pattern.compile("(?i)" + Pattern.quote(text)))

fun MacrobenchmarkScope.tap(text: String, timeoutMs: Long = 5_000): Boolean {
    val node = device.wait(Until.findObject(any(text)), timeoutMs) ?: return false
    node.click()
    device.waitForIdle()
    return true
}

/** Flashcards: the first deck with cards due, then show and rate 10 cards (Good). */
fun MacrobenchmarkScope.flashcards() {
    tap("Flashcards")
    if (!tap("Continue")) return
    repeat(10) {
        if (!tap("Show answer", 3_000)) return
        if (!tap("Good", 3_000)) return
    }
    device.pressBack()
}

/** Quiz: a quiz from the saved form, 5 questions answered with the first option or a typed word. */
fun MacrobenchmarkScope.quiz() {
    tap("Quiz")
    if (!tap("Generate quiz", 5_000)) return
    repeat(5) {
        device.wait(Until.hasObject(any("Check").clickable(true)), 15_000)
        val option = device.findObject(By.res("option-1")) ?: device.findObject(any("1"))
        if (device.hasObject(any("Check"))) {
            device.findObject(By.clazz("android.widget.EditText"))?.text = "answer"
            tap("Check")
        } else option?.click()
        if (!tap("Next question", 3_000)) tap("See results", 3_000)
    }
    device.pressBack()
}

/** Teacher: 3 questions by the rules, each answered before the next. */
fun MacrobenchmarkScope.teacher() {
    tap("Teacher")
    for (q in listOf("What does near mean?", "How do I say school?", "Example with because")) {
        device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000)
        // The field is recomposed while the previous answer comes in: look it up again right before typing.
        repeat(3) { runCatching { device.findObject(By.clazz("android.widget.EditText"))?.text = q }.onSuccess { return@repeat } }
        tap("Send")
        device.wait(Until.hasObject(any("Send").enabled(true)), 10_000)
    }
}
