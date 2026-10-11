package de.ingvoss.fotosortierer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ingvoss.fotosortierer.data.media.AccessLevel
import de.ingvoss.fotosortierer.data.media.MediaAccess
import de.ingvoss.fotosortierer.data.settings.AppSettings
import de.ingvoss.fotosortierer.data.settings.ThemeMode
import de.ingvoss.fotosortierer.ui.AppRoot
import de.ingvoss.fotosortierer.ui.theme.FotoTheme

/** Einzige Activity (Single-Activity-App); die Navigation übernimmt Navigation Compose. */
class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as FotoApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startWithOnboarding = MediaAccess.level(this) == AccessLevel.NONE
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark }
                )
                onDispose { }
            }
            FotoTheme(settings.themeMode) { AppRoot(startWithOnboarding) }
        }
    }

    override fun onStart() {
        super.onStart()
        container.onActivityStart()
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
