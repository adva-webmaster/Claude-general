package de.ingvoss.fotosortierer.ui.sort

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.ui.appContainer
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
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
    var failed by remember(media.uid) { mutableStateOf(false) }
    val request = remember(media.uid, highRes) {
        ImageRequest.Builder(context)
            .data(media.contentUri())
            .apply { if (highRes) size(CoilSize(HIGH_RES_PX, HIGH_RES_PX)) }
            .build()
    }
    Box(modifier) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            // Lädt die Vorschau nicht, fehlt vielleicht die Datei (auf anderem Weg gelöscht): dann aus der Datenbank
            // entfernen. Existiert sie, ist das Format nur nicht darstellbar (z. B. AVI): dann ein Platzhalter.
            onError = {
                failed = true
                if (reported.compareAndSet(false, true)) {
                    container.appScope.launch { container.mediaCleaner.purgeIfMissing(media.uid) }
                }
            }
        )
        if (failed) {
            Icon(
                if (media.isVideo) AppIcons.Video else AppIcons.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(40.dp)
            )
        }
    }
}

/**
 * Videowiedergabe in Endlosschleife (Standard stumm). [soundOn] und [paused] steuern Ton und Pause, [control] verbindet
 * den Laufbalken (Position, Länge, Springen).
 *
 * Zuerst spielt ExoPlayer (Android-Bordmittel). Kann Android die Bildspur nicht darstellen – bei AVI mit Motion-JPEG oder
 * DivX etwa hört man nur den Ton bei schwarzem Bild –, wechselt die Wiedergabe selbstständig zur Reserve (libvlc).
 */
@Composable
fun VideoSurface(
    uri: Uri,
    soundOn: Boolean,
    paused: Boolean,
    control: VideoControl,
    modifier: Modifier = Modifier
) {
    var fallback by remember(uri) { mutableStateOf(false) }
    if (fallback) {
        VlcVideoSurface(uri, soundOn, paused, control, modifier)
    } else {
        ExoVideoSurface(uri, soundOn, paused, control, onUnsupported = { fallback = true }, modifier = modifier)
    }
}

@SuppressLint("UnsafeOptInUsageError") // PlayerView-Einstellungen sind in Media3 als @UnstableApi markiert
@Composable
private fun ExoVideoSurface(
    uri: Uri,
    soundOn: Boolean,
    paused: Boolean,
    control: VideoControl,
    onUnsupported: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val currentOnUnsupported by rememberUpdatedState(onUnsupported)
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                // Bildspur vorhanden, aber auf diesem Gerät nicht darstellbar → Reserve-Abspieler.
                if (tracks.containsType(C.TRACK_TYPE_VIDEO) && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)) currentOnUnsupported()
            }

            override fun onPlayerError(error: PlaybackException) = currentOnUnsupported()
        }
        player.addListener(listener)
        val seeker: (Long) -> Unit = { ms -> player.seekTo(ms) }
        control.seeker = seeker
        onDispose {
            if (control.seeker === seeker) control.seeker = null
            player.release()
        }
    }
    LaunchedEffect(player, soundOn) { player.volume = if (soundOn) 1f else 0f }
    LaunchedEffect(player, paused) { player.playWhenReady = !paused }
    LaunchedEffect(player) {
        // Läuft die Wiedergabe, ohne dass je ein Bild entsteht (Spur unbekannt), gilt das Video als nicht darstellbar.
        var silentFrames = 0
        while (true) {
            val duration = player.duration
            if (duration > 0) control.durationMs = duration
            control.positionMs = player.currentPosition.coerceAtLeast(0L)
            if (player.playbackState == Player.STATE_READY && player.videoSize.width == 0) silentFrames++ else silentFrames = 0
            if (silentFrames * PROGRESS_INTERVAL_MS >= NO_PICTURE_TIMEOUT_MS) currentOnUnsupported()
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

/**
 * Reserve-Abspieler (libvlc) für Formate, die Android nicht darstellt. Öffnet die Datei über einen
 * Dateideskriptor (kein Dateipfad nötig) und läuft wie [ExoVideoSurface] stumm in Schleife.
 */
@Composable
private fun VlcVideoSurface(
    uri: Uri,
    soundOn: Boolean,
    paused: Boolean,
    control: VideoControl,
    modifier: Modifier
) {
    val context = LocalContext.current
    val pausedNow by rememberUpdatedState(paused)
    val handler = remember { Handler(Looper.getMainLooper()) }
    val engine = remember(uri) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        val libVlc = LibVLC(context.applicationContext, arrayListOf<String>())
        val player = MediaPlayer(libVlc)
        if (descriptor != null) {
            val media = Media(libVlc, descriptor.fileDescriptor)
            player.media = media
            media.release()
        }
        player.volume = 0
        player.setEventListener { event ->
            // Am Ende von vorn beginnen (Schleife); nicht im VLC-Thread selbst, sonst droht ein Stillstand.
            if (event.type == MediaPlayer.Event.EndReached) {
                handler.post {
                    player.stop()
                    if (!pausedNow) player.play()
                }
            }
        }
        VlcEngine(libVlc, player, descriptor)
    }
    DisposableEffect(engine) {
        val seeker: (Long) -> Unit = { ms -> engine.player.time = ms }
        control.seeker = seeker
        onDispose {
            if (control.seeker === seeker) control.seeker = null
            handler.removeCallbacksAndMessages(null)
            engine.player.setEventListener(null)
            engine.player.stop()
            engine.player.detachViews()
            engine.player.release()
            engine.libVlc.release()
            engine.descriptor?.close()
        }
    }
    LaunchedEffect(engine, soundOn) { engine.player.volume = if (soundOn) 100 else 0 }
    LaunchedEffect(engine, paused) { if (paused) engine.player.pause() else engine.player.play() }
    LaunchedEffect(engine) {
        while (true) {
            val length = engine.player.length
            if (length > 0) control.durationMs = length
            control.positionMs = engine.player.time.coerceAtLeast(0L)
            delay(PROGRESS_INTERVAL_MS)
        }
    }
    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    engine.player.attachViews(layout, null, false, false)
                    engine.player.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private class VlcEngine(val libVlc: LibVLC, val player: MediaPlayer, val descriptor: ParcelFileDescriptor?)

private const val HIGH_RES_PX = 2400
private const val PROGRESS_INTERVAL_MS = 200L

/** So lange darf ExoPlayer abspielen, ohne dass ein Bild entsteht, bevor die Reserve übernimmt. */
private const val NO_PICTURE_TIMEOUT_MS = 2000L
