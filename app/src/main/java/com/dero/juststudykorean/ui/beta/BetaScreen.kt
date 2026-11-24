package com.dero.juststudykorean.ui.beta

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import com.dero.juststudykorean.ui.components.EmptyState
import com.dero.juststudykorean.ui.components.JsTopBar
import com.dero.juststudykorean.ui.components.KoSide
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.navigation.Screen
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Tokens

/** design/beta.md 1: the four BETA functions, in the design's order. */
enum class BetaFeature(val title: String, val ko: String, val line: String, val icon: Lucide, val tag: String, val screen: Screen) {
    Listening("Listening Lab", "듣기 연습", "Dictation, shadowing and hands-free review with your sentences.", Lucide.Headphones, "listening", Screen.Listening),
    Grammar("Grammar Lab", "문법 연습", "Conjugate your verbs with your grammar points.", Lucide.Dumbbell, "grammar", Screen.GrammarLab(null)),
    Mistakes("My Mistakes", "내 실수", "Your mistakes grouped into patterns, ready to practice.", Lucide.NotebookPen, "mistakes", Screen.Mistakes),
    Reader("Reader", "읽기", "Read your PDFs and tap a word to see your notes.", Lucide.BookOpen, "reader", Screen.Reader(null));

    companion object {
        fun of(screen: Screen): BetaFeature = entries.first { it.screen::class == screen::class }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(Tokens.Space4),
    ) { content() }
}

/** BETA (D11-5): opened from More, over the current tab, without the bottom bar. Time here doesn't count (D11-2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaScreen(onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = JustStudyTheme.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar("Beta", "베타", "New ways to study. They may still change.", null, scroll, onMore = null, onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Tokens.Space4, end = Tokens.Space4, bottom = Tokens.Space6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
        ) {
            items(BetaFeature.entries, key = { it.name }) { f ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(f.icon, contentDescription = null, tint = colors.control)
                        Spacer(Modifier.width(Tokens.Space3))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(f.title, Modifier.alignByBaseline(), style = JustStudyTheme.korean.bodyBold, color = MaterialTheme.colorScheme.onSurface)
                                KoSide(f.ko, JustStudyTheme.korean.bodyBold.fontSize, Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(f.line, style = MaterialTheme.typography.bodySmall, color = colors.muted) // Caption: 12 sp
                        }
                        Spacer(Modifier.width(Tokens.Space3))
                        StickerButton("Open", { onOpen(f.screen) }, Modifier.testTag("beta-open-${f.tag}"), ko = "열기")
                    }
                }
            }
            item(key = "note") { Text("Time here doesn't count toward your streak.", style = MaterialTheme.typography.bodySmall, color = colors.muted) }
        }
    }
}

/** PR 3: each BETA function shows this until its own PR replaces it (design/beta.md 1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(feature: BetaFeature, onBack: () -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(scroll.nestedScrollConnection)) {
        JsTopBar(feature.title, feature.ko, null, null, scroll, onMore = null, onBack = onBack)
        EmptyState(Lucide.FlaskConical, "Coming soon", "준비 중", null, Modifier.padding(horizontal = Tokens.Space4))
    }
}
