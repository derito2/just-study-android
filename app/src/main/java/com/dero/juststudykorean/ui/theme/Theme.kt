package com.dero.juststudykorean.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun JustStudyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalJustStudyColors provides if (darkTheme) DarkJustStudyColors else LightJustStudyColors,
        LocalKoreanTypography provides KoreanTypography(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme else lightScheme,
            typography = JustStudyTypography,
            shapes = JustStudyShapes,
            content = content,
        )
    }
}

object JustStudyTheme {
    val colors: JustStudyColors
        @Composable @ReadOnlyComposable get() = LocalJustStudyColors.current
    val korean: KoreanTypography
        @Composable @ReadOnlyComposable get() = LocalKoreanTypography.current
}
