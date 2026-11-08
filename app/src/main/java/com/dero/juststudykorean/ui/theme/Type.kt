package com.dero.juststudykorean.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dero.juststudykorean.R

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
)

// Poppins has no Hangul: Korean uses the system font (Noto Sans CJK KR), line height 1.6, no tracking.
private fun TypeToken.style(family: FontFamily) = TextStyle(
    fontFamily = family,
    fontSize = size,
    lineHeight = (size.value * lineHeight).sp,
    fontWeight = FontWeight(weight),
    letterSpacing = letterSpacingEm.em,
)

// Mapping from the design system README ("Material 3 en Compose"). labelMedium takes label-caps
// (button-sm is identical; nav-label differs only in tracking and line height and is applied where used).
val JustStudyTypography = Typography(
    headlineLarge = Tokens.PageTitle.style(Poppins),
    headlineSmall = Tokens.StatValue.style(Poppins),
    titleLarge = Tokens.KpiValue.style(Poppins),
    titleMedium = Tokens.SectionTitle.style(Poppins),
    bodyMedium = Tokens.Body.style(Poppins),
    bodySmall = Tokens.Caption.style(Poppins),
    labelLarge = Tokens.Button.style(Poppins),
    labelMedium = Tokens.LabelCaps.style(Poppins),
    labelSmall = Tokens.KoBelow.style(FontFamily.Default),
)

/** Styles with no Material role: the rest of tokens.json → type.groups. */
@Immutable
data class KoreanTypography(
    val koHero: TextStyle = Tokens.KoHero.style(FontFamily.Default),
    val koPrompt: TextStyle = Tokens.KoPrompt.style(FontFamily.Default),
    val koTitle: TextStyle = Tokens.KoTitle.style(FontFamily.Default),
    val koBody: TextStyle = Tokens.KoBody.style(FontFamily.Default),
    val koBelow: TextStyle = Tokens.KoBelow.style(FontFamily.Default),
    val bodyBold: TextStyle = Tokens.BodyBold.style(Poppins),
    val trend: TextStyle = Tokens.Trend.style(Poppins),
    val buttonSm: TextStyle = Tokens.ButtonSm.style(Poppins),
    val chip: TextStyle = Tokens.Chip.style(Poppins),
    val navLabel: TextStyle = Tokens.NavLabel.style(Poppins),
)

val LocalKoreanTypography = staticCompositionLocalOf { KoreanTypography() }
