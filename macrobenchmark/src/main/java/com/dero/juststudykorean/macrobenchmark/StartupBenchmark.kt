package com.dero.juststudykorean.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Point 9 of the plan: cold start under 1 s; frames in Flashcards, Quiz, Teacher and Games (phase 8). Before and after the profile. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun startup(mode: CompilationMode) = rule.measureRepeated(TARGET, listOf(StartupTimingMetric()), mode, StartupMode.COLD, iterations = 5) {
        pressHome()
        startActivityAndWait()
    }

    @Test fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun frames(mode: CompilationMode, flow: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit) =
        rule.measureRepeated(TARGET, listOf(FrameTimingMetric()), mode, StartupMode.WARM, iterations = 2, setupBlock = { startActivityAndWait() }) { flow() }

    @Test fun flashcardsFrames() = frames(CompilationMode.Partial(BaselineProfileMode.Require)) { flashcards() }

    @Test fun quizFrames() = frames(CompilationMode.Partial(BaselineProfileMode.Require)) { quiz() }

    @Test fun teacherFrames() = frames(CompilationMode.Partial(BaselineProfileMode.Require)) { teacher() }

    @Test fun gamesFrames() = frames(CompilationMode.Partial(BaselineProfileMode.Require)) { games() }
}
