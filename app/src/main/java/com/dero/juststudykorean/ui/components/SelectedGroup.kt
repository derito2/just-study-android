package com.dero.juststudykorean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/**
 * Buttons where one is chosen (design system, SelectedGroup): Small, equal widths. The chosen one is `action`,
 * sunk to a 2 dp `action-edge`; the others are the gray sticker button with its 3 dp edge.
 */
@Composable
fun SelectedGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, ko: List<String>? = null) {
    val colors = JustStudyTheme.colors
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        options.forEachIndexed { i, label ->
            val chosen = i == selected
            val edge = if (chosen) Tokens.EdgeSm else Tokens.EdgeMd
            Box(
                Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .selectable(chosen, interactionSource = null, indication = null, role = Role.Tab) { onSelect(i) } // R1-23: Tab exposes "selected" (RadioButton only "checked")
                    .padding(bottom = Tokens.EdgeMd), // room for the tallest edge, so all faces line up when not chosen
                propagateMinConstraints = true,
            ) {
                Box(Modifier.matchParentSize().offset(y = Tokens.EdgeMd).background(if (chosen) colors.actionEdge else colors.grayEdge, MaterialTheme.shapes.medium))
                Box(
                    Modifier
                        .offset(y = Tokens.EdgeMd - edge) // chosen: sunk 1 dp, so 2 dp of edge stay visible
                        .background(if (chosen) colors.action else colors.control, MaterialTheme.shapes.medium)
                        .heightIn(min = Tokens.ButtonSmH)
                        .padding(horizontal = Tokens.Space3, vertical = if (ko != null) Tokens.Space1 else 0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label.uppercase(), color = Color.White, style = JustStudyTheme.korean.buttonSm, textAlign = TextAlign.Center, maxLines = 1)
                        if (ko != null) Text(ko[i], color = Color.White, style = JustStudyTheme.korean.koBelow, modifier = Modifier.alpha(0.85f), maxLines = 1)
                    }
                }
            }
        }
    }
}
