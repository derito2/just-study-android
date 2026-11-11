package com.dero.juststudykorean.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.data.flashcards.CardDetails
import com.dero.juststudykorean.ui.theme.JustStudyTheme
import com.dero.juststudykorean.ui.theme.Motion
import com.dero.juststudykorean.ui.theme.Tokens
import com.dero.juststudykorean.ui.theme.reduceMotion

@Composable
private fun Modifier.card(padding: androidx.compose.ui.unit.Dp) = this
    .fillMaxWidth()
    .border(Tokens.Border, JustStudyTheme.colors.line, MaterialTheme.shapes.large)
    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
    .padding(padding)

/**
 * The card's turn (400 ms ease-out, in Y, camera at 1000 dp): the front turns to 90°, then the back comes from
 * −90°. Each face keeps its own size. With "Remove animations", the faces just swap.
 */
@Composable
fun FlipCard(flipped: Boolean, front: @Composable (Modifier) -> Unit, back: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
    val reduce = reduceMotion()
    val angle = remember { Animatable(if (flipped) 180f else 0f) }
    LaunchedEffect(flipped) {
        val target = if (flipped) 180f else 0f
        if (reduce || !flipped) angle.snapTo(target) else angle.animateTo(target, tween(400, easing = Motion.EaseOut))
    }
    val density = LocalDensity.current.density
    val showFront by remember { derivedStateOf { angle.value < 90f } } // recomposes only when the face changes
    // The angle is read in the draw phase: the turn does not recompose the faces on every frame.
    val layer = Modifier.graphicsLayer { val a = angle.value; rotationY = if (a < 90f) a else a - 180f; cameraDistance = 1000f * density }
    if (showFront) front(modifier.then(layer)) else back(modifier.then(layer))
}

/** Front: NEW, the word (or its meaning, English → Korean) and, with Type the answer, the field. Fills the free space. */
@Composable
fun CardFront(prompt: String, korean: Boolean, isNew: Boolean, modifier: Modifier = Modifier, typingField: (@Composable () -> Unit)? = null) {
    val colors = JustStudyTheme.colors
    Column(
        modifier.heightIn(min = 256.dp).card(Tokens.Space8).semantics { contentDescription = "Card front" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.Space4, Alignment.CenterVertically),
    ) {
        if (isNew) Text("NEW", Modifier.background(colors.tint, CircleShape).padding(horizontal = Tokens.Space2, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = colors.brandText)
        Text(prompt, style = if (korean) JustStudyTheme.korean.koHero else TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (typingField != null) Box(Modifier.widthIn(max = 320.dp)) { typingField() }
    }
}

/** Back: the Korean, romanization, the meaning and the source; with Type the answer, [typed] below a divider. */
@Composable
fun CardBack(
    korean: String,
    romanization: String?,
    meaning: String,
    source: String?,
    modifier: Modifier = Modifier,
    typed: (@Composable () -> Unit)? = null,
    meaningFirst: Boolean = false,
) {
    val colors = JustStudyTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier.card(Tokens.Space6).semantics { contentDescription = "Card back" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        val ko = @Composable { Text(korean, style = JustStudyTheme.korean.koTitle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = ink, textAlign = TextAlign.Center) }
        val en = @Composable { Text(meaning, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ink, textAlign = TextAlign.Center) }
        if (meaningFirst) { en(); ko() } else {
            ko()
            if (!romanization.isNullOrBlank()) Text(romanization, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            en()
        }
        if (typed != null) {
            Spacer(Modifier.height(Tokens.Space2))
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.line))
            Spacer(Modifier.height(Tokens.Space2))
            typed()
        }
        if (source != null) Text(source, style = MaterialTheme.typography.bodySmall, color = colors.muted, textAlign = TextAlign.Center)
    }
}

/** From your notes (내 노트에서): Structure, Explanation, Examples and Notes, whichever exist. */
@Composable
fun FromYourNotes(details: CardDetails, modifier: Modifier = Modifier) {
    if (details.structure.isEmpty() && details.examples.isEmpty() && details.explanation.isEmpty() && details.notes.isEmpty()) return
    val colors = JustStudyTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier.card(Tokens.Space5), verticalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom) {
            Text("From your notes", Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = ink)
            KoSide("내 노트에서", MaterialTheme.typography.titleMedium.fontSize, Modifier.alignByBaseline(), color = ink)
        }
        @Composable
        fun section(title: String, body: @Composable () -> Unit) = Column {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.muted)
            Spacer(Modifier.height(Tokens.Space1))
            body()
        }
        val ko = JustStudyTheme.korean.koBody
        if (details.structure.isNotEmpty()) section("Structure") { details.structure.forEach { Text("• $it", style = ko, color = ink) } }
        if (details.explanation.isNotEmpty()) section("Explanation") { details.explanation.forEach { Text(it, style = ko, color = ink) } }
        if (details.examples.isNotEmpty()) section("Examples") {
            details.examples.forEach { (k, e) ->
                Text(k, style = ko, color = ink)
                if (e.isNotEmpty()) Text(e, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
        if (details.notes.isNotEmpty()) section("Notes") { details.notes.forEach { Text(it, style = ko, color = ink) } }
    }
}
