@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.common

import android.net.Uri

class MediaItem {
    companion object {
        fun fromUri(uri: Uri): MediaItem = MediaItem()
    }
}

object C {
    const val TRACK_TYPE_VIDEO = 2
}

class VideoSize(val width: Int, val height: Int) {
    companion object {
        val UNKNOWN = VideoSize(0, 0)
    }
}

open class PlaybackException : Exception()

class Tracks {
    fun containsType(trackType: Int): Boolean = false
    fun isTypeSupported(trackType: Int): Boolean = false
}

interface Player {
    interface Listener {
        fun onTracksChanged(tracks: Tracks) {}
        fun onPlayerError(error: PlaybackException) {}
        fun onVideoSizeChanged(videoSize: VideoSize) {}
        fun onPlaybackStateChanged(playbackState: Int) {}
    }

    val videoSize: VideoSize
    val playbackState: Int
    fun addListener(listener: Listener)
    fun seekTo(positionMs: Long)
    var volume: Float
    var playWhenReady: Boolean
    var repeatMode: Int
    val duration: Long
    val currentPosition: Long
    fun setMediaItem(item: MediaItem)
    fun prepare()
    fun release()

    companion object {
        const val REPEAT_MODE_ONE = 1
        const val STATE_READY = 3
    }
}
