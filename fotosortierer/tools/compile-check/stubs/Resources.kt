@file:Suppress("unused")
package androidx.compose.ui.platform

import android.content.res.Resources
import androidx.compose.runtime.compositionLocalOf

val LocalResources = compositionLocalOf<Resources> { error("stub") }
