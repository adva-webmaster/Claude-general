@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.ui.platform

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.compositionLocalOf

val LocalContext = compositionLocalOf<Context> { error("stub") }
val LocalConfiguration = compositionLocalOf<Configuration> { error("stub") }
