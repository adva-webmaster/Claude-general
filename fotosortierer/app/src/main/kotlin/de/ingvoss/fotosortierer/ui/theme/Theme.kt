package de.ingvoss.fotosortierer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.core.ProgressState
import de.ingvoss.fotosortierer.data.settings.ThemeMode

/**
 * Statusfarben nach der farbenblind-sicheren Okabe-Ito-Palette. Farbe ist nie das einzige Merkmal:
 * Kalender/Orte zeigen zusätzlich Form (Ring, Teil-Ring, Haken) und Zahl, Medien Symbol und Beschriftung.
 */
@Immutable
data class FotoColors(
    val open: Color,
    val partial: Color,
    val complete: Color,
    val keep: Color,
    val trash: Color,
    val deferred: Color,
    val unprocessed: Color
) {
    fun forState(state: ProgressState): Color = when (state) {
        ProgressState.LEER -> unprocessed
        ProgressState.UNBEARBEITET -> open
        ProgressState.TEILWEISE -> partial
        ProgressState.VOLLSTAENDIG -> complete
    }

    fun forStatus(status: MediaStatus): Color = when (status) {
        MediaStatus.UNBEARBEITET -> unprocessed
        MediaStatus.BEHALTEN -> keep
        MediaStatus.PAPIERKORB -> trash
        MediaStatus.ZURUECKGESTELLT -> deferred
        MediaStatus.GELOESCHT -> unprocessed
    }

    companion object {
        val Light = FotoColors(
            open = Color(0xFFD55E00),
            partial = Color(0xFFE0B800),
            complete = Color(0xFF0072B2),
            keep = Color(0xFF009E73),
            trash = Color(0xFFD55E00),
            deferred = Color(0xFFCC79A7),
            unprocessed = Color(0xFF8A8F98)
        )
        val Dark = FotoColors(
            open = Color(0xFFFF8A3D),
            partial = Color(0xFFF0E442),
            complete = Color(0xFF56B4E9),
            keep = Color(0xFF2FD1A2),
            trash = Color(0xFFFF8A3D),
            deferred = Color(0xFFE59ACB),
            unprocessed = Color(0xFF9AA0AA)
        )
    }
}

val LocalFotoColors = staticCompositionLocalOf { FotoColors.Light }

/*
 * Markenfarben: Dunkelrot und Anthrazit. Alle Farben der Oberfläche leiten sich aus diesen beiden Schemata ab;
 * wer die Marke ändern will, ändert hier die Konstanten (BrandRed*, Anthracite*).
 */
private val BrandRed = Color(0xFF8E1B20)
private val BrandRedLight = Color(0xFFE4575C)
private val Anthracite = Color(0xFF2B3035)

private val LightScheme = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF3C0508),
    secondary = Color(0xFF3B4045),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE1E4),
    onSecondaryContainer = Color(0xFF14181B),
    tertiary = Color(0xFF565D63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1E4E7),
    onTertiaryContainer = Color(0xFF191D21),
    background = Color(0xFFF6F4F3),
    onBackground = Color(0xFF1C1B1B),
    surface = Color(0xFFFAF8F7),
    onSurface = Color(0xFF1C1B1B),
    surfaceVariant = Color(0xFFE7E3E2),
    onSurfaceVariant = Color(0xFF4B4645),
    surfaceTint = BrandRed,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F1F0),
    surfaceContainer = Color(0xFFEFEBEA),
    surfaceContainerHigh = Color(0xFFE9E5E4),
    surfaceContainerHighest = Color(0xFFE3DFDE),
    outline = Color(0xFF857E7D),
    outlineVariant = Color(0xFFD6CFCE),
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
    onSecondary = Color(0xFF22272B),
    secondaryContainer = Color(0xFF343A40),
    onSecondaryContainer = Color(0xFFDCE1E6),
    tertiary = Color(0xFFA9B0B7),
    onTertiary = Color(0xFF1F2428),
    tertiaryContainer = Color(0xFF3A4046),
    onTertiaryContainer = Color(0xFFDDE2E7),
    background = Color(0xFF14171A),
    onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF181B1E),
    onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF3A3F44),
    onSurfaceVariant = Color(0xFFBFC4C9),
    surfaceTint = BrandRedLight,
    surfaceContainerLowest = Color(0xFF0F1113),
    surfaceContainerLow = Color(0xFF1B1E21),
    surfaceContainer = Color(0xFF202427),
    surfaceContainerHigh = Color(0xFF2A2E32),
    surfaceContainerHighest = Color(0xFF34393D),
    outline = Color(0xFF8A9096),
    outlineVariant = Color(0xFF454A50),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFF9DEDC)
)

/** Großzügigere Rundungen als der Material-Standard: weicher, moderner Eindruck. */
private val FotoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Kräftigere Überschriften, sonst Standardschrift. */
private val FotoTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

/** Verlauf für Kopfbereiche: Dunkelrot → Anthrazit. */
fun brandGradient(): Brush = Brush.linearGradient(listOf(Color(0xFF8E1B20), Color(0xFF4A1A1E), Anthracite))

@Composable
fun FotoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalFotoColors provides if (dark) FotoColors.Dark else FotoColors.Light) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            shapes = FotoShapes,
            typography = FotoTypography,
            content = content
        )
    }
}
