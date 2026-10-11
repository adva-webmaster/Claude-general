@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.common

import android.net.Uri

class MediaItem {
    companion object {
        fun fromUri(uri: Uri): MediaItem = MediaItem()
    }
}

interface Player {
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
    }
}
