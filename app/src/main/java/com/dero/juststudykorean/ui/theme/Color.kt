package com.dero.juststudykorean.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Role → token mapping from design/shell.md ("Tema"). Dynamic Color stays off.
val lightScheme = lightColorScheme(
    primary = Tokens.Action, onPrimary = Color.White,
    primaryContainer = Tokens.TintLight, onPrimaryContainer = Tokens.BrandTextLight,
    secondary = Tokens.Brand, onSecondary = Color.White,
    tertiary = Tokens.Go, onTertiary = Color.White,
    error = Tokens.Danger, onError = Color.White,
    errorContainer = Tokens.WrongFill, onErrorContainer = Tokens.Wrong,
    background = Tokens.BackgroundLight, onBackground = Tokens.InkLight,
    surface = Tokens.SurfaceLight, onSurface = Tokens.InkLight,
    surfaceVariant = Tokens.CanvasLight, onSurfaceVariant = Tokens.MutedLight,
    surfaceContainerLowest = Tokens.CanvasLight, surfaceContainerLow = Tokens.CanvasLight,
    surfaceContainer = Tokens.CanvasLight, surfaceContainerHigh = Tokens.CanvasLight, surfaceContainerHighest = Tokens.CanvasLight,
    outline = Tokens.FieldLight, outlineVariant = Tokens.LineLight,
    inverseSurface = Tokens.Tooltip, inverseOnSurface = Color.White, inversePrimary = Tokens.OnTooltipAction,
    scrim = Tokens.ScrimLight,
)

val darkScheme = darkColorScheme(
    primary = Tokens.Action, onPrimary = Color.White,
    primaryContainer = Tokens.TintDark, onPrimaryContainer = Tokens.BrandTextDark,
    secondary = Tokens.Brand, onSecondary = Color.White,
    tertiary = Tokens.Go, onTertiary = Color.White,
    error = Tokens.Danger, onError = Color.White,
    errorContainer = Tokens.WrongFill, onErrorContainer = Tokens.Wrong,
    background = Tokens.BackgroundDark, onBackground = Tokens.InkDark,
    surface = Tokens.SurfaceDark, onSurface = Tokens.InkDark,
    surfaceVariant = Tokens.CanvasDark, onSurfaceVariant = Tokens.MutedDark,
    surfaceContainerLowest = Tokens.CanvasDark, surfaceContainerLow = Tokens.CanvasDark,
    surfaceContainer = Tokens.CanvasDark, surfaceContainerHigh = Tokens.CanvasDark, surfaceContainerHighest = Tokens.CanvasDark,
    outline = Tokens.FieldDark, outlineVariant = Tokens.LineDark,
    inverseSurface = Tokens.Tooltip, inverseOnSurface = Color.White, inversePrimary = Tokens.OnTooltipAction,
    scrim = Tokens.ScrimDark,
)
