package com.dero.juststudykorean.ui.components

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

/** P-28: Again · Hard · Good · Easy in one row (8 dp apart), each with the interval it would give ([next]). */
@Composable
fun RatingButtons(next: Map<Rating, String>, onRate: (Rating) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        looks.forEach { (rating, ko, color) ->
            val interval = next[rating].orEmpty()
            StickerButton(
                rating.name, { onRate(rating) },
                Modifier.weight(1f).semantics { contentDescription = "${rating.name}, next review in $interval" },
                ko = ko, color = color, enabled = enabled, detail = interval,
            )
        }
    }
}
