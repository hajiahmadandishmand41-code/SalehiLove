package com.salehilove.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// A restrained, consistent gold palette for light and dark modes.
val SoftGold = Color(0xFFC7A45C)
val DarkGold = Color(0xFF806124)
val GoldContainer = Color(0xFFF2E5C5)
val GoldSurface = Color(0xFFFFFCF5)
val WarmBackground = Color(0xFFFAF7F0)
val Ink = Color(0xFF29251E)
val ErrorRed = Color(0xFFB3261E)

val LightColors = androidx.compose.material3.lightColorScheme(
    primary = DarkGold,
    onPrimary = Color.White,
    primaryContainer = GoldContainer,
    onPrimaryContainer = Color(0xFF35270D),
    secondary = SoftGold,
    onSecondary = Color(0xFF30240D),
    secondaryContainer = GoldContainer,
    onSecondaryContainer = Color(0xFF35270D),
    tertiary = DarkGold,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = Color(0xFF35270D),
    background = WarmBackground,
    onBackground = Ink,
    surface = GoldSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0E9D9),
    onSurfaceVariant = Color(0xFF514A3C),
    outline = Color(0xFFB7A57F),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = SoftGold,
    onPrimary = Color(0xFF2D220C),
    primaryContainer = Color(0xFF4D3D1F),
    onPrimaryContainer = Color(0xFFF2E5C5),
    secondary = SoftGold,
    onSecondary = Color(0xFF2D220C),
    secondaryContainer = Color(0xFF4D3D1F),
    onSecondaryContainer = Color(0xFFF2E5C5),
    tertiary = SoftGold,
    onTertiary = Color(0xFF2D220C),
    tertiaryContainer = Color(0xFF4D3D1F),
    onTertiaryContainer = Color(0xFFF2E5C5),
    background = Color(0xFF181611),
    onBackground = Color(0xFFF1EDE4),
    surface = Color(0xFF201D16),
    onSurface = Color(0xFFF1EDE4),
    surfaceVariant = Color(0xFF353025),
    onSurfaceVariant = Color(0xFFD1C6AD),
    outline = Color(0xFF9B885E),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
