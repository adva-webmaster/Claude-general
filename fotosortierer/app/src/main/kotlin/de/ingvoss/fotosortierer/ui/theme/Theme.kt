package de.ingvoss.fotosortierer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0072B2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8F6),
    onPrimaryContainer = Color(0xFF001D31),
    secondary = Color(0xFF4F6070),
    secondaryContainer = Color(0xFFD2E4F5),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE2E2EA),
    error = Color(0xFFB3261E)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8CCBF0),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B71),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFFB6C8DB),
    secondaryContainer = Color(0xFF384956),
    background = Color(0xFF121316),
    surface = Color(0xFF121316),
    surfaceVariant = Color(0xFF44474E),
    error = Color(0xFFF2B8B5)
)

@Composable
fun FotoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalFotoColors provides if (dark) FotoColors.Dark else FotoColors.Light) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}
