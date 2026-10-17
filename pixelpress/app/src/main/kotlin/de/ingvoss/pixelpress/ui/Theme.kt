package de.ingvoss.pixelpress.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Markenfarben wie bei PixelSweeper: Dunkelrot und Anthrazit.
private val BrandRed = Color(0xFF8E1B20)
private val BrandRedLight = Color(0xFFE4575C)

private val LightScheme = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF3C0508),
    secondary = Color(0xFF3B4045),
    secondaryContainer = Color(0xFFDDE1E4),
    onSecondaryContainer = Color(0xFF14181B),
    background = Color(0xFFF6F4F3),
    onBackground = Color(0xFF1C1B1B),
    surface = Color(0xFFFAF8F7),
    onSurface = Color(0xFF1C1B1B),
    surfaceVariant = Color(0xFFE7E3E2),
    onSurfaceVariant = Color(0xFF4B4645),
    surfaceContainer = Color(0xFFEFEBEA),
    surfaceContainerHigh = Color(0xFFE9E5E4),
    outline = Color(0xFF857E7D),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkScheme = darkColorScheme(
    primary = BrandRedLight,
    onPrimary = Color(0xFF3A0709),
    primaryContainer = Color(0xFF7A1A1F),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFFB9C0C7),
    secondaryContainer = Color(0xFF343A40),
    onSecondaryContainer = Color(0xFFDCE1E6),
    background = Color(0xFF14171A),
    onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF181B1E),
    onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF3A3F44),
    onSurfaceVariant = Color(0xFFBFC4C9),
    surfaceContainer = Color(0xFF202427),
    surfaceContainerHigh = Color(0xFF2A2E32),
    outline = Color(0xFF8A9096),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp)
    )
}

@Composable
fun PixelPressTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
