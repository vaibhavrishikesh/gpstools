package com.gpstools.camera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Navy primary on light surfaces (Settings, dialogs); brand stays consistent.
private val LightColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E0F2),
    onPrimaryContainer = BrandNavy,
    secondary = BrandGold,
    onSecondary = Color(0xFF3A2600),
    secondaryContainer = Color(0xFFFDE8C4),
    onSecondaryContainer = Color(0xFF4A3000),
    tertiary = BrandGoldDark,
    onTertiary = Color.White,
    background = Color(0xFFFDFCFB),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFB),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFF45464A),
    outline = Color(0xFF757780),
)

// Camera/Gallery/Map are dark surfaces: gold drives the visible accents,
// navy lives in containers/background so the brand reads on black.
private val DarkColorScheme = darkColorScheme(
    primary = BrandGold,
    onPrimary = BrandNavy,
    primaryContainer = BrandNavy,
    onPrimaryContainer = Color.White,
    secondary = BrandGold,
    onSecondary = BrandNavy,
    secondaryContainer = BrandNavyLight,
    onSecondaryContainer = Color(0xFFEAF0FA),
    tertiary = BrandGoldSoft,
    onTertiary = BrandNavy,
    background = BrandNavyDeep,
    onBackground = Color(0xFFE3E2E6),
    surface = BrandNavySurface,
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF3A4256),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8E9099),
)

@Composable
fun GpstoolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic (Material You) colour is intentionally OFF so the navy+gold brand
    // is consistent on every device; pass true to opt back in.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
