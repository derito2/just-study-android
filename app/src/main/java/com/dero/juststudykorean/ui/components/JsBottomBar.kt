package com.dero.juststudykorean.ui.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import com.dero.juststudykorean.ui.navigation.TopLevel
import com.dero.juststudykorean.ui.theme.JustStudyTheme

private val TopLevel.icon
    get() = when (this) {
        TopLevel.Dashboard -> Lucide.LayoutGrid
        TopLevel.Library -> Lucide.BookText
        TopLevel.Flashcards -> Lucide.Layers
        TopLevel.Quiz -> Lucide.CircleHelp
        TopLevel.Teacher -> Lucide.GraduationCap
    }

/**
 * Bottom bar on `action`: 80 dp plus the navigation inset, white icons and Title Case labels. The active
 * destination has the 64 × 32 dp `nav-active` indicator and a 2.5 stroke (the indicator is only 1.8:1 on `action`).
 */
@Composable
fun JsBottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit, modifier: Modifier = Modifier) {
    val colors = JustStudyTheme.colors
    val picks = remember { mutableStateMapOf<TopLevel, Int>() } // each pick plays the icon's animation once
    NavigationBar(modifier, containerColor = colors.action, contentColor = Color.White) {
        TopLevel.entries.forEach { item ->
            val selected = item == current
            NavigationBarItem(
                selected = selected,
                onClick = { picks[item] = (picks[item] ?: 0) + 1; onSelect(item) },
                icon = {
                    LucideIcon(item.icon, contentDescription = null, strokeWidth = if (selected) 2.5f else 2f, animateKey = picks[item])
                },
                // At 130 % "Flashcards" may take two lines (hyphenated) instead of being cut.
                label = {
                    Text(
                        item.name,
                        style = JustStudyTheme.korean.navLabel.copy(hyphens = Hyphens.Auto),
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White, selectedTextColor = Color.White, indicatorColor = colors.navActive,
                    unselectedIconColor = Color.White, unselectedTextColor = Color.White,
                ),
            )
        }
    }
}
