@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.activity

import android.content.res.Resources

open class ComponentActivity : android.app.Activity()

class SystemBarStyle private constructor() {
    companion object {
        fun auto(lightScrim: Int, darkScrim: Int, detectDarkMode: (Resources) -> Boolean = { false }): SystemBarStyle = SystemBarStyle()
    }
}

fun ComponentActivity.enableEdgeToEdge(
    statusBarStyle: SystemBarStyle = SystemBarStyle.auto(0, 0),
    navigationBarStyle: SystemBarStyle = SystemBarStyle.auto(0, 0)
) {}
