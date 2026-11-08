package com.dero.juststudykorean.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Tokens

/**
 * The web's dark-mode switch: 36 × 20 dp track, 16 dp knob, 48 dp touch target. [onCheckedChange] null when a
 * whole row toggles it (MoreSheet). Dark theme: gray track on or off and a `background` knob, as in the web.
 */
@Composable
fun ThemeSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val colors = JustStudyTheme.colors
    val dark = colors.background == Tokens.BackgroundDark
    val track by animateColorAsState(
        when { dark -> colors.disabledFill; checked -> colors.navActive; else -> colors.line },
        tween(Motion.Fast, easing = Motion.EaseOut), label = "track",
    )
    val knobX by animateDpAsState(if (checked) 18.dp else 2.dp, tween(Motion.Fast, easing = Motion.EaseOut), label = "knob")
    val toggle = if (onCheckedChange != null) {
        Modifier.minimumInteractiveComponentSize().toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
    } else Modifier
    Box(modifier.then(toggle), contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp, 20.dp).background(track, CircleShape)) {
            Box(
                Modifier
                    .offset(x = knobX)
                    .align(Alignment.CenterStart)
                    .size(16.dp)
                    // The web's Tailwind `shadow` (≈1 dp, black 10 %), light theme only.
                    .then(if (dark) Modifier else Modifier.shadow(1.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f)))
                    .background(if (dark) colors.background else Color.White, CircleShape),
            )
        }
    }
}
