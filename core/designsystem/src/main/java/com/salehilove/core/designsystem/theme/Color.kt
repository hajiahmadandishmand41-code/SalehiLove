package com.salehilove.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// One consistent, refined gold palette for light and dark modes.
val SoftGold = Color(0xFFC7A45C)
val DarkGold = Color(0xFF806124)
val DeepGold = Color(0xFF5C4314)
val GoldContainer = Color(0xFFF2E5C5)
val GoldSurface = Color(0xFFFFFCF5)
val GoldCardLight = Color(0xFFF3ECE0)
val WarmBackground = Color(0xFFFAF7F0)
val Ink = Color(0xFF262118)
val MutedInk = Color(0xFF59503F)
val ErrorRed = Color(0xFFB3261E)

val LightColors = androidx.compose.material3.lightColorScheme(
    primary = DarkGold,
    onPrimary = Color.White,
    primaryContainer = GoldContainer,
    onPrimaryContainer = DeepGold,
    secondary = SoftGold,
    onSecondary = Color(0xFF2B200B),
    secondaryContainer = GoldContainer,
    onSecondaryContainer = DeepGold,
    tertiary = DarkGold,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = DeepGold,
    background = WarmBackground,
    onBackground = Ink,
    surface = GoldSurface,
    onSurface = Ink,
    surfaceVariant = GoldCardLight,
    onSurfaceVariant = MutedInk,
    outline = Color(0xFFC8B794),
    outlineVariant = Color(0xFFE5DAC3),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = SoftGold,
    onPrimary = Color(0xFF2B200B),
    primaryContainer = Color(0xFF47371B),
    onPrimaryContainer = GoldContainer,
    secondary = SoftGold,
    onSecondary = Color(0xFF2B200B),
    secondaryContainer = Color(0xFF3E3018),
    onSecondaryContainer = GoldContainer,
    tertiary = SoftGold,
    onTertiary = Color(0xFF2B200B),
    tertiaryContainer = Color(0xFF47371B),
    onTertiaryContainer = GoldContainer,
    background = Color(0xFF15130E),
    onBackground = Color(0xFFF2EDE2),
    surface = Color(0xFF1D1A13),
    onSurface = Color(0xFFF2EDE2),
    surfaceVariant = Color(0xFF28231A),
    onSurfaceVariant = Color(0xFFD3C7AE),
    outline = Color(0xFF8C7A53),
    outlineVariant = Color(0xFF3B3426),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
