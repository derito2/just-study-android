package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** FilterPill (design system): a pill; the chosen one is `action` with white text, the others `pill` with `ink`. */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = JustStudyTheme.colors
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .selectable(selected, interactionSource = null, indication = null, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            Modifier.background(if (selected) colors.action else colors.pill, CircleShape).heightIn(min = 32.dp)
                .padding(horizontal = Tokens.Space3, vertical = 6.dp),
            style = JustStudyTheme.korean.bodyBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
