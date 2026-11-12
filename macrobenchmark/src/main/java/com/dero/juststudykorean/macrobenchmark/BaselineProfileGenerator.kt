package com.dero.juststudykorean.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The baseline profile: startup and the main paths (copied to app/src/main/baseline-prof.txt and committed). */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(TARGET, maxIterations = 3) {
        pressHome()
        startActivityAndWait()
        flashcards()
        teacher()
        tap("Library")
        tap("Dashboard")
    }
}
