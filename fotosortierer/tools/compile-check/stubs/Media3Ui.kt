@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.ui

import android.content.Context
import android.widget.FrameLayout
import androidx.media3.common.Player

class AspectRatioFrameLayout(context: Context) : FrameLayout(context) {
    companion object {
        const val RESIZE_MODE_FIT = 0
    }
}

class PlayerView(context: Context) : FrameLayout(context) {
    var useController: Boolean = true
    var resizeMode: Int = 0
    var player: Player? = null
}
