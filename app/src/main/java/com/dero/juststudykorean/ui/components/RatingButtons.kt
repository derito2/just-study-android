package com.dero.juststudykorean.ui.components

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dero.juststudykorean.korean.Rating
import com.dero.juststudykorean.ui.theme.Tokens

private val looks = listOf(
    Triple(Rating.Again, "다시", StickerColor.Danger), Triple(Rating.Hard, "어려움", StickerColor.Gray),
    Triple(Rating.Good, "알맞음", StickerColor.Fix), Triple(Rating.Easy, "쉬움", StickerColor.Go),
)

/** Again · Hard · Good · Easy in a 2 × 2 grid (8 × 12 dp apart), each with the interval it would give ([next]). */
@Composable
fun RatingButtons(next: Map<Rating, String>, onRate: (Rating) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val haptic = LocalHapticFeedback.current // R-010: Again = Reject, the others = Confirm
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        looks.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
                row.forEach { (rating, ko, color) ->
                    val interval = next[rating].orEmpty()
                    StickerButton(
                        rating.name, {
                            haptic.performHapticFeedback(if (rating == Rating.Again) HapticFeedbackType.Reject else HapticFeedbackType.Confirm)
                            onRate(rating)
                        },
                        Modifier.weight(1f).semantics { contentDescription = "${rating.name}, next review in $interval" },
                        ko = ko, color = color, enabled = enabled, detail = interval,
                    )
                }
            }
        }
    }
}

