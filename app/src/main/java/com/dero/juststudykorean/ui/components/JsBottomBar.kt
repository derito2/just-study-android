package com.dero.juststudykorean.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.theme.reduceMotion

/** One place in the bar: [target] null is Games, which opens on top and is never active. [name]: what TalkBack says. */
private data class BarItem(val label: String, val icon: Lucide, val target: TopLevel?, val name: String = label)

private val items = listOf(
    BarItem("Dash", Lucide.LayoutGrid, TopLevel.Dashboard, "Dashboard"),
    BarItem("Library", Lucide.BookText, TopLevel.Library),
    BarItem("Flashcards", Lucide.Layers, TopLevel.Flashcards),
    BarItem("Quiz", Lucide.Rocket, TopLevel.Quiz),
    BarItem("Teacher", Lucide.GraduationCap, TopLevel.Teacher),
    BarItem("Games", Lucide.Gamepad2, null),
    BarItem("Grammar", Lucide.Languages, TopLevel.Grammar),
)

private val ItemW = 76.dp

/**
 * Bottom bar on `action` (design/ajustes.md AJ-01): seven 76 dp destinations in a row that scrolls sideways (at 411 dp,
 * five and half of Games show: the hint that there is more). 80 dp plus the navigation inset, white icons and Title Case
 * labels; the active one has the 64 × 32 dp `nav-active` indicator and a 2.5 stroke. Choosing one that is not whole on
 * screen scrolls it into view (240 ms; at once with reduce motion).
 */
@Composable
fun JsBottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit, modifier: Modifier = Modifier, onGames: () -> Unit = {}) {
    val colors = JustStudyTheme.colors
    val picks = remember { mutableStateMapOf<Int, Int>() } // each pick plays the icon's animation once
    val scroll = rememberScrollState()
    val reduce = reduceMotion()
    val itemPx = with(LocalDensity.current) { ItemW.roundToPx() } // each item is laid out at whole pixels
    LaunchedEffect(current, scroll.viewportSize) {
        val i = items.indexOfFirst { it.target == current }
        if (i < 0 || scroll.viewportSize == 0) return@LaunchedEffect
        val left = i * itemPx
        val right = (i + 1) * itemPx
        val to = when {
            left < scroll.value -> left
            right > scroll.value + scroll.viewportSize -> right - scroll.viewportSize
            else -> return@LaunchedEffect
        }
        if (reduce) scroll.scrollTo(to) else scroll.animateScrollTo(to, tween(240))
    }
    Box(modifier.fillMaxWidth().background(colors.action).navigationBarsPadding()) {
        Row(Modifier.horizontalScroll(scroll).selectableGroup()) {
            items.forEachIndexed { index, item ->
                val selected = item.target != null && item.target == current
                Column(
                    Modifier
                        .width(ItemW)
                        .heightIn(min = Tokens.BottomBarH)
                        .selectable(selected, role = Role.Tab) {
                            picks[index] = (picks[index] ?: 0) + 1
                            if (item.target == null) onGames() else onSelect(item.target)
                        }
                        .clearAndSetSemantics { contentDescription = item.name; this.selected = selected; role = Role.Tab }
                        .padding(top = 12.dp, bottom = Tokens.Space2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space1),
                ) {
                    Box(
                        Modifier.size(64.dp, 32.dp).background(if (selected) colors.navActive else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        LucideIcon(item.icon, contentDescription = null, tint = Color.White, strokeWidth = if (selected) 2.5f else 2f, animateKey = picks[index])
                    }
                    // At 130 % "Flashcards" may take two lines (hyphenated) instead of being cut.
                    Text(
                        item.label, style = JustStudyTheme.korean.navLabel.copy(hyphens = Hyphens.Auto), color = Color.White,
                        maxLines = 2, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
