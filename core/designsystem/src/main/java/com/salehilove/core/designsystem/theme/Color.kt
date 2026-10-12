package com.salehilove.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// ---- Brand palette: dark green / cream / soft gold --------------------------

val DeepGreen = Color(0xFF0E2B22)      // primary dark green
val GreenPrimary = Color(0xFF1F5C46)   // main green
val GreenContainer = Color(0xFFB9E7CE)
val Cream = Color(0xFFF7F1E3)          // cream background
val CreamContainer = Color(0xFFEFE6D0)
val SoftGold = Color(0xFFC9A961)       // soft gold accent
val SoftGoldContainer = Color(0xFFF3E6C4)
val DarkGold = Color(0xFF8A6E2F)
val InkGreen = Color(0xFF173329)       // dark text on cream
val MistGreen = Color(0xFF6E8B7E)      // secondary text
val RoseAccent = Color(0xFFB76E79)     // subtle rose for the "love" theme
val ErrorRed = Color(0xFFB3261E)

// Light scheme (cream surfaces, deep green primary)
val LightColors = androidx.compose.material3.lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = Color(0xFF06301F),
    secondary = DarkGold,
    onSecondary = Color.White,
    secondaryContainer = SoftGoldContainer,
    onSecondaryContainer = Color(0xFF3D2F0B),
    tertiary = RoseAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9DCE0),
    onTertiaryContainer = Color(0xFF4A252C),
    background = Cream,
    onBackground = InkGreen,
    surface = Cream,
    onSurface = InkGreen,
    surfaceVariant = CreamContainer,
    onSurfaceVariant = Color(0xFF4C5B53),
    outline = Color(0xFF9CAF9F),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

// Dark scheme (deep green surfaces, gold accents)
val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF8FD6B0),
    onPrimary = Color(0xFF04301F),
    primaryContainer = Color(0xFF1B4A36),
    onPrimaryContainer = GreenContainer,
    secondary = SoftGold,
    onSecondary = Color(0xFF3B2E10),
    secondaryContainer = Color(0xFF554321),
    onSecondaryContainer = SoftGoldContainer,
    tertiary = Color(0xFFE9B7BF),
    onTertiary = Color(0xFF4A252C),
    tertiaryContainer = Color(0xFF63393F),
    onTertiaryContainer = Color(0xFFF9DCE0),
    background = Color(0xFF0B1712),
    onBackground = Color(0xFFDCE8DF),
    surface = Color(0xFF0B1712),
    onSurface = Color(0xFFDCE8DF),
    surfaceVariant = Color(0xFF1B2C24),
    onSurfaceVariant = Color(0xFFBCCCC0),
    outline = Color(0xFF86978C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
