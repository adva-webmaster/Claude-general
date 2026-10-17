@file:Suppress("unused", "UNUSED_PARAMETER")
package org.videolan.libvlc

import android.content.Context
import java.io.FileDescriptor

interface IMedia

class LibVLC(context: Context, options: List<String>) {
    fun release() {}
}

class Media(libVLC: LibVLC, fd: FileDescriptor) : IMedia {
    fun release() {}
}

class MediaPlayer(libVLC: LibVLC) {
    enum class ScaleType { SURFACE_BEST_FIT, SURFACE_FIT_SCREEN }

    class Event {
        val type: Int = 0

        companion object {
            const val EndReached = 265
        }
    }

    fun interface EventListener {
        fun onEvent(event: Event)
    }

    var media: IMedia? = null
    var time: Long = 0L
    val length: Long = 0L
    var volume: Int = 100
    var videoScale: ScaleType = ScaleType.SURFACE_BEST_FIT
    fun play() {}
    fun pause() {}
    fun stop() {}
    fun release() {}
    fun attachViews(layout: org.videolan.libvlc.util.VLCVideoLayout, dialogProvider: Any?, enableSubtitles: Boolean, useTextureView: Boolean) {}
    fun detachViews() {}
    fun setEventListener(listener: EventListener?) {}
}
