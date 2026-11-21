package com.dero.juststudykorean.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Tokens

enum class TrendDirection { Up, Down, Same, None, New }

/** Change against last week: arrow and "+12%" (the web's wordsTrend / accuracyTrend). */
data class Trend(val direction: TrendDirection, val label: String)

/** Streak look (design/dashboard.md, the web's streakLook): border and title color by level, and 🔥 from 100 days. */
data class StreakLook(val border: Color?, val title: Color?, val fires: Int)

@Composable
fun streakLook(days: Int): StreakLook {
    val c = JustStudyTheme.colors
    val (border, title) = when {
        days >= 70 -> c.streak70 to c.streak70
        days >= 50 -> c.streak50 to c.streak50Title
        days >= 30 -> c.streak30 to c.streak30Title
        days >= 15 -> c.streak15 to c.streak15Title
        days >= 7 -> c.streak7 to c.streak7Title
        else -> null to null
    }
    return StreakLook(border, title, if (days >= 200) 3 else if (days >= 150) 2 else if (days >= 100) 1 else 0)
}

/**
 * KPI card (StatTile): 2 dp border, radius 16, everything centered; `label-caps` title, 22 sp value and, below,
 * the trend or the 🔥. [bump] makes the value jump once (scale 1.15, 280 ms, spring).
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trend: Trend? = null,
    look: StreakLook? = null,
    bump: Boolean = false,
) {
    val colors = JustStudyTheme.colors
    val scale = remember { Animatable(1f) }
    LaunchedEffect(bump) {
        if (bump) {
            scale.animateTo(1.15f, tween(140, easing = Motion.Spring))
            scale.animateTo(1f, tween(140, easing = Motion.Spring))
        }
    }
    val trendText = trend?.let {
        when (it.direction) {
            TrendDirection.None, TrendDirection.New -> it.label
            else -> "${if (it.direction == TrendDirection.Up) "Up" else if (it.direction == TrendDirection.Down) "Down" else "Same"} vs last week: ${it.label}"
        }
    }
    Column(
        modifier
            .border(Tokens.Border, look?.border ?: colors.line, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(start = Tokens.Space3, end = Tokens.Space3, top = 14.dp, bottom = Tokens.Space3)
            .clearAndSetSemantics { contentDescription = listOfNotNull(label, value, trendText).joinToString(". ") },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = look?.title ?: colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Tokens.Space1))
        Text(value, Modifier.scale(scale.value), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (trend != null) {
            when (trend.direction) {
                // P-12: nothing this week, or the first week with something: words, no arrow and no alarm.
                TrendDirection.None -> Text(trend.label, style = JustStudyTheme.korean.trend, color = colors.muted)
                TrendDirection.New -> Text(trend.label, style = JustStudyTheme.korean.trend, color = colors.correctInk)
                else -> {
                    val tint = when (trend.direction) { TrendDirection.Up -> colors.correctInk; TrendDirection.Down -> colors.wrongInk; else -> colors.muted }
                    val icon = when (trend.direction) { TrendDirection.Up -> Lucide.TrendingUp; TrendDirection.Down -> Lucide.TrendingDown; else -> Lucide.Minus }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space1)) {
                        LucideIcon(icon, contentDescription = null, size = 16.dp, tint = tint)
                        if (trend.label.isNotEmpty()) Text(trend.label, style = JustStudyTheme.korean.trend, color = tint)
                    }
                }
            }
        }
        if (look != null && look.fires > 0) Text("🔥".repeat(look.fires), style = JustStudyTheme.korean.trend)
    }
}

/** The web's wordsTrend: words that moved to Learning this week against last week. */
fun wordsTrend(thisWeek: Int, lastWeek: Int?): Trend {
    if (thisWeek == 0) return Trend(TrendDirection.None, "none yet this week")
    if (lastWeek == null || lastWeek == 0) return Trend(TrendDirection.New, "new this week")
    if (thisWeek == lastWeek) return Trend(TrendDirection.Same, "0%")
    val change = Math.round((thisWeek - lastWeek).toDouble() / lastWeek * 100).toInt()
    return Trend(if (change > 0) TrendDirection.Up else TrendDirection.Down, "${if (change > 0) "+" else ""}$change%")
}

/** The web's accuracyTrend: points of accuracy against last week. */
fun accuracyTrend(thisWeek: Double?, lastWeek: Double?): Trend {
    if (thisWeek == null) return Trend(TrendDirection.None, "none yet this week")
    if (lastWeek == null) return Trend(TrendDirection.New, "new this week")
    val points = Math.round((thisWeek - lastWeek) * 100).toInt()
    if (points == 0) return Trend(TrendDirection.Same, "0%")
    return Trend(if (points > 0) TrendDirection.Up else TrendDirection.Down, "${if (points > 0) "+" else ""}$points%")
}
