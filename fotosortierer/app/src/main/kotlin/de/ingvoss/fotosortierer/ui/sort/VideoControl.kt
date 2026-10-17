package de.ingvoss.fotosortierer.ui.sort

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.Formats

/**
 * Verbindung zwischen Abspieler und Laufbalken: Der Abspieler meldet Position und Länge und trägt unter [seeker] ein,
 * wie er springt; der Laufbalken liest den Stand und ruft [seekToFraction] auf. Unabhängig davon, ob gerade ExoPlayer
 * oder die Reserve (libvlc) spielt.
 */
@Stable
class VideoControl(initialDurationMs: Long = 0L) {
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(initialDurationMs)

    /** Vom aktuellen Abspieler gesetzt: springt an die Stelle in Millisekunden. */
    var seeker: ((Long) -> Unit)? = null

    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun seekToFraction(fraction: Float) {
        val duration = durationMs
        if (duration <= 0) return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        positionMs = target
        seeker?.invoke(target)
    }
}

/**
 * Laufbalken wie bei jedem Videoplayer: Tippen springt an die Stelle, Ziehen spult mit. Liegt als eigene Ebene über
 * der Karte, damit Ziehen am Balken nicht als Wischgeste der Karte zählt.
 */
@Composable
fun VideoSeekBar(control: VideoControl, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: control.fraction
    val shownMs = if (dragging != null) (control.durationMs * fraction).toLong() else control.positionMs
    val description = stringResource(R.string.video_seek)
    val position = stringResource(R.string.video_position_of, Formats.duration(shownMs), Formats.duration(control.durationMs))
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp)
            // Auch Tipps neben dem Schieber (auf den Zeiten) gehören dem Balken und lösen kein Pausieren aus.
            .pointerInput(Unit) { detectTapGestures { } },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(Formats.duration(shownMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = fraction,
            onValueChange = {
                dragging = it
                control.seekToFraction(it)
            },
            onValueChangeFinished = { dragging = null },
            enabled = control.durationMs > 0,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .semantics {
                    contentDescription = description
                    stateDescription = position
                },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                disabledThumbColor = Color.White.copy(alpha = 0.5f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.5f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
        Text(Formats.duration(control.durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
