package com.dero.juststudykorean.ui.games.pieces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.korean.hangul.VowelShape
import com.dero.juststudykorean.korean.hangul.compose
import com.dero.juststudykorean.korean.hangul.joinTails
import com.dero.juststudykorean.korean.hangul.joinVowels
import com.dero.juststudykorean.korean.hangul.shapeOf
import com.dero.juststudykorean.ui.components.Lucide
import com.dero.juststudykorean.ui.components.LucideIcon
import com.dero.juststudykorean.ui.components.StickerButton
import com.dero.juststudykorean.ui.components.StickerColor
import com.dero.juststudykorean.ui.theme.JustStudyTheme

/** The Korean name of each jamo, for TalkBack (games-minigames.md 0). */
val JAMO_NAMES: Map<Char, String> = mapOf(
    'ㄱ' to "기역", 'ㄲ' to "쌍기역", 'ㄴ' to "니은", 'ㄷ' to "디귿", 'ㄸ' to "쌍디귿", 'ㄹ' to "리을", 'ㅁ' to "미음", 'ㅂ' to "비읍", 'ㅃ' to "쌍비읍",
    'ㅅ' to "시옷", 'ㅆ' to "쌍시옷", 'ㅇ' to "이응", 'ㅈ' to "지읒", 'ㅉ' to "쌍지읒", 'ㅊ' to "치읓", 'ㅋ' to "키읔", 'ㅌ' to "티읕", 'ㅍ' to "피읖", 'ㅎ' to "히읗",
    'ㅏ' to "아", 'ㅐ' to "애", 'ㅑ' to "야", 'ㅒ' to "얘", 'ㅓ' to "어", 'ㅔ' to "에", 'ㅕ' to "여", 'ㅖ' to "예", 'ㅗ' to "오", 'ㅘ' to "와", 'ㅙ' to "왜",
    'ㅚ' to "외", 'ㅛ' to "요", 'ㅜ' to "우", 'ㅝ' to "워", 'ㅞ' to "웨", 'ㅟ' to "위", 'ㅠ' to "유", 'ㅡ' to "으", 'ㅢ' to "의", 'ㅣ' to "이",
)

private val ROWS = listOf("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ")
private val SHIFTED = mapOf('ㅂ' to 'ㅃ', 'ㅈ' to 'ㅉ', 'ㄷ' to 'ㄸ', 'ㄱ' to 'ㄲ', 'ㅅ' to 'ㅆ', 'ㅐ' to 'ㅒ', 'ㅔ' to 'ㅖ')
private val KEY_SHAPE = RoundedCornerShape(8.dp)

/**
 * The games' own 두벌식 keyboard (games-minigames.md 0): no game opens the system keyboard. ⇧ turns ㅂㅈㄷㄱㅅ and ㅐㅔ
 * into their doubles for one press. Keys 34 × 46 dp; the last row: space and Check.
 */
@Composable
fun HangulKeyboard(onJamo: (Char) -> Unit, onSpace: () -> Unit, onDelete: () -> Unit, onCheck: () -> Unit, checkEnabled: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    var shift by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ROWS.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r == 2) KeyCap("⇧", "Shift", enabled, selected = shift, width = 44.dp) { shift = !shift }
                row.forEach { base ->
                    val jamo = if (shift) SHIFTED[base] ?: base else base
                    KeyCap(jamo.toString(), JAMO_NAMES[jamo] ?: jamo.toString(), enabled) { onJamo(jamo); shift = false }
                }
                if (r == 2) KeyCap(null, "Delete", enabled, width = 44.dp, onClick = onDelete)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KeyCap("space", "Space", enabled, width = 0.dp, modifier = Modifier.weight(1f), onClick = onSpace)
            StickerButton("Check", onCheck, Modifier.width(96.dp), ko = "확인", color = StickerColor.Go, enabled = checkEnabled && enabled)
        }
    }
}

@Composable
private fun KeyCap(text: String?, description: String, enabled: Boolean, selected: Boolean = false, width: Dp = 34.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = JustStudyTheme.colors
    Box(
        modifier
            .then(if (width > 0.dp) Modifier.width(width) else Modifier)
            .height(46.dp)
            .drawBehind { drawRoundRect(c.field, topLeft = androidx.compose.ui.geometry.Offset(0f, 2.dp.toPx()), size = size, cornerRadius = CornerRadius(8.dp.toPx())) }
            .background(if (selected) c.tint else c.canvas, KEY_SHAPE)
            .border(if (selected) 2.dp else 0.dp, if (selected) c.brand else c.canvas, KEY_SHAPE)
            .selectable(selected, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (text == null) LucideIcon(Lucide.Delete, contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)
        else Text(text, fontSize = if (text.length > 1) 13.sp else 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * SyllableFrame (games-minigames.md 0): a square with the cells of a block by its shape — vertical vowel: initial left,
 * vowel right; horizontal: initial above, vowel below; mixed: initial top left, vowel around; a final below (two cells
 * if compound). [cells]: what each cell holds (null: empty, dashed). When all cells are full, the real glyph shows.
 */
@Composable
fun SyllableFrame(vowel: Char, vowelCells: Int, tailCells: Int, cells: List<Char?>, size: Dp, modifier: Modifier = Modifier, onCell: ((Int) -> Unit)? = null) {
    val c = JustStudyTheme.colors
    val full = cells.all { it != null }
    val glyph = if (full) composeCells(cells, vowelCells) else null
    Box(modifier.size(size).border(2.dp, c.line, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (glyph != null) {
            Text(glyph.toString(), fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            return@Box
        }
        val shape = shapeOf(vowel)
        val gap = 4.dp
        val inner = size - 12.dp
        val tailH = if (tailCells > 0) inner * 0.3f else 0.dp
        val top = inner - tailH - if (tailCells > 0) gap else 0.dp
        @Composable
        fun cell(i: Int, w: Dp, h: Dp) = Cell(cells.getOrNull(i), w, h, if (onCell != null) ({ onCell(i) }) else null, i, cells.size)
        Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
            when (shape) {
                VowelShape.Vertical -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    cell(0, (inner - gap) * 0.55f, top)
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) { repeat(vowelCells) { cell(1 + it, (inner - gap) * 0.45f, (top - gap * (vowelCells - 1)) / vowelCells) } }
                }
                VowelShape.Horizontal -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    cell(0, inner, (top - gap) * 0.55f)
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) { repeat(vowelCells) { cell(1 + it, (inner - gap * (vowelCells - 1)) / vowelCells, (top - gap) * 0.45f) } }
                }
                VowelShape.Mixed -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        cell(0, (inner - gap) * 0.6f, (top - gap) * 0.55f)
                        cell(1, (inner - gap) * 0.6f, (top - gap) * 0.45f)
                    }
                    if (vowelCells == 2) cell(2, (inner - gap) * 0.4f, top) else Box(Modifier.width((inner - gap) * 0.4f))
                }
            }
            if (tailCells > 0) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(tailCells) { cell(1 + vowelCells + it, (inner - gap * (tailCells - 1)) / tailCells, tailH) }
            }
        }
    }
}

/** The glyph of full cells: [lead, vowel or its two parts, final parts]. */
internal fun composeCells(cells: List<Char?>, vowelCells: Int): Char? {
    val lead = cells[0] ?: return null
    val vowel = if (vowelCells == 2) cells[1]?.let { a -> cells[2]?.let { b -> joinVowels(a, b) } } else cells[1]
    val tail = cells.drop(1 + vowelCells).map { it ?: return null }
    val tailText = if (tail.size == 2) joinTails(tail[0], tail[1])?.let { tail.joinToString("") } ?: return null else tail.joinToString("")
    return vowel?.let { compose(lead, it, tailText) }
}

@Composable
private fun Cell(jamo: Char?, w: Dp, h: Dp, onClick: (() -> Unit)?, index: Int, total: Int) {
    val c = JustStudyTheme.colors
    val kind = when { index == 0 -> "Initial consonant"; else -> "Cell ${index + 1} of $total" }
    Box(
        Modifier.size(w, h)
            .drawBehind {
                if (jamo == null) drawRoundRect(c.field, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
            }
            .then(if (onClick != null && jamo != null) Modifier.selectable(false, onClick = onClick, role = Role.Button) else Modifier)
            .semantics { contentDescription = "$kind, ${jamo ?: "empty"}" },
        contentAlignment = Alignment.Center,
    ) {
        if (jamo != null) Text(jamo.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
