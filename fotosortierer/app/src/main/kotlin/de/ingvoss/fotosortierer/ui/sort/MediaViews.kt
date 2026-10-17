package de.ingvoss.fotosortierer.ui.sort

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.ui.appContainer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bild (oder Video-Standbild) eines Mediums. Coil lädt auf die Größe des Layouts herunterskaliert; nur beim
 * Hineinzoomen ([highRes]) wird einmalig mit höherer Auflösung nachgeladen.
 */
@Composable
fun MediaImage(
    media: MediaStateEntity,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    highRes: Boolean = false,
    contentDescription: String? = media.displayName
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val reported = remember(media.uid) { AtomicBoolean(false) }
    val request = remember(media.uid, highRes) {
        ImageRequest.Builder(context)
            .data(media.contentUri())
            .apply { if (highRes) size(CoilSize(HIGH_RES_PX, HIGH_RES_PX)) }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        // Lädt die Vorschau nicht, fehlt vielleicht die Datei (auf anderem Weg gelöscht): dann aus der Datenbank entfernen.
        onError = {
            if (reported.compareAndSet(false, true)) {
                container.appScope.launch { container.mediaCleaner.purgeIfMissing(media.uid) }
            }
        }
    )
}

/**
 * Videowiedergabe (Media3/ExoPlayer) in Endlosschleife. Stumm ist der Standard; [soundOn] und [paused]
 * steuern Ton und Pause. [onProgress] meldet den Fortschritt 0..1 für den Fortschrittsbalken.
 */
@SuppressLint("UnsafeOptInUsageError") // PlayerView-Einstellungen sind in Media3 als @UnstableApi markiert
@Composable
fun VideoSurface(
    uri: Uri,
    soundOn: Boolean,
    paused: Boolean,
    onProgress: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, soundOn) { player.volume = if (soundOn) 1f else 0f }
    LaunchedEffect(player, paused) { player.playWhenReady = !paused }
    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            onProgress(if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f)
            delay(PROGRESS_INTERVAL_MS)
        }
    }
    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private const val HIGH_RES_PX = 2400
private const val PROGRESS_INTERVAL_MS = 200L
