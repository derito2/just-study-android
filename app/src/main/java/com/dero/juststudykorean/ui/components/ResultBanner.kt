package com.dero.juststudykorean.ui.components

import com.dero.juststudykorean.ui.theme.reduceMotion
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.LocalFrozenMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.ui.theme.JustStudyTheme

/** design/polish.md P-02: the four tones of a [ResultBanner]. */
enum class BannerTone(val icon: Lucide) {
    Correct(Lucide.Check), Wrong(Lucide.X), Neutral(Lucide.Lightbulb), Warn(Lucide.BadgeAlert),
}

/**
 * The one box that says how something went: after a typed answer, in Games' ribbon, for an error. `*-fill` background, 2 dp
 * border, 16 dp padding, a 20 dp icon and the [title] (16 sp, 700) in the tone's ink. Inside, only [body]'s own colours
 * (`fillInk`, `fillMuted`), never the theme's `onSurface`/`muted` (H-01). [action] sits on the right (Replay, Try again).
 * [cornerRadius] is 12, and 16 in Games.
 */
@Composable
fun ResultBanner(
    tone: BannerTone,
    title: String?,
    modifier: Modifier = Modifier,
    ko: String? = null,
    icon: Lucide = tone.icon,
    cornerRadius: Dp = 12.dp,
    iconAnimateKey: Any? = null,
    action: (@Composable () -> Unit)? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = JustStudyTheme.colors
    val (fill, border, ink) = when (tone) {
        BannerTone.Correct -> Triple(c.correctFill, c.correctDeco, c.correctInk)
        BannerTone.Wrong -> Triple(c.wrongFill, c.wrongBorder, c.wrongInk)
        BannerTone.Neutral -> Triple(c.tint, c.tintBorder, c.brandText)
        BannerTone.Warn -> Triple(c.warnFill, c.warn, c.fillWarn)
    }
    val shape = RoundedCornerShape(cornerRadius)
    // M-7: it comes in from 8 dp below, fading in (240 ms, `Spring`). With "Remove animations", and in screenshot tests, it is just there.
    val still = reduceMotion() || LocalFrozenMotion.current
    val enter = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { if (!still) enter.animateTo(1f, tween(Motion.Base, easing = Motion.Spring)) }
    Row(
        modifier.graphicsLayer { alpha = Motion.bannerAlpha(enter.value); translationY = Motion.bannerOffsetDp(enter.value).dp.toPx() }.fillMaxWidth().background(fill, shape).border(2.dp, border, shape).padding(16.dp),
        // With a body the icon stays beside the title; alone, the title is centred in the box.
        verticalAlignment = if (body != null) Alignment.Top else Alignment.CenterVertically,
    ) {
        LucideIcon(icon, contentDescription = null, Modifier.padding(top = if (body != null) 1.dp else 0.dp), size = 20.dp, tint = ink, animateKey = iconAnimateKey)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) Row(verticalAlignment = Alignment.Bottom) {
                Text(title, Modifier.alignByBaseline(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink)
                if (ko != null) KoSide(ko, 16.sp, Modifier.alignByBaseline(), color = ink)
            }
            body?.invoke(this)
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.align(Alignment.CenterVertically)) { action() }
        }
    }
}
