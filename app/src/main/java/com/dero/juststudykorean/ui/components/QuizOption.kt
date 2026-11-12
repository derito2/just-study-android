package com.dero.juststudykorean.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.theme.reduceMotion

enum class OptionState { Open, Correct, Chosen, Other }

/**
 * QuizOption (design/quiz.md C): full width, 48 dp, radius 12, `canvas`; the number and the text. Tapping sends it.
 * After answering: the right one in `correct-fill` with `check` (pop), the wrong choice in `wrong-fill` with `x` (shake).
 */
@Composable
fun QuizOption(number: Int, text: String, state: OptionState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = JustStudyTheme.colors
    val reduce = reduceMotion()
    val scale = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    // R-010: the option you picked answers with a tap: Confirm if right, Reject if wrong (also with reduced motion).
    val haptic = LocalHapticFeedback.current
    var picked by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when {
            state == OptionState.Open -> picked = false
            state == OptionState.Chosen -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
            state == OptionState.Correct && picked -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    LaunchedEffect(state) {
        if (reduce) return@LaunchedEffect
        if (state == OptionState.Correct) { scale.animateTo(1.04f, tween(120, easing = Motion.Spring)); scale.animateTo(1f, tween(120, easing = Motion.Spring)) }
        if (state == OptionState.Chosen) shake.animateTo(0f, keyframes { durationMillis = 320; 6f at 53; -6f at 107; 6f at 160; -6f at 213; 3f at 267 })
    }
    val density = LocalDensity.current.density
    val (bg, ink) = when (state) {
        OptionState.Correct -> c.correctFill to c.correct
        OptionState.Chosen -> c.wrongFill to c.wrong
        else -> c.canvas to MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; translationX = shake.value * density }
            .heightIn(min = 48.dp)
            .background(bg, MaterialTheme.shapes.medium)
            .clickable(enabled = state == OptionState.Open, role = Role.Button) { picked = true; onClick() }
            .padding(horizontal = Tokens.Space4, vertical = Tokens.Space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space3),
    ) {
        Text("$number", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ink)
        when (state) {
            OptionState.Correct -> { LucideIcon(Lucide.Check, contentDescription = null, size = 18.dp, tint = c.correct); Text("correct", style = MaterialTheme.typography.bodySmall, color = c.correct) }
            OptionState.Chosen -> { LucideIcon(Lucide.X, contentDescription = null, size = 18.dp, tint = c.wrong); Text("your answer", style = MaterialTheme.typography.bodySmall, color = c.wrong) }
            else -> Unit
        }
    }
}
