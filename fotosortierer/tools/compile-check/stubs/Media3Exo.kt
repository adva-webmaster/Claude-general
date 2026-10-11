@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.exoplayer

import android.content.Context
import androidx.media3.common.Player

interface ExoPlayer : Player {
    class Builder(context: Context) {
        fun build(): ExoPlayer = error("stub")
    }
}
