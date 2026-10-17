package de.ingvoss.pixelpress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.ingvoss.pixelpress.ui.PixelPressRoot
import de.ingvoss.pixelpress.ui.PixelPressTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = (application as PixelPressApp).controller
        setContent {
            PixelPressTheme {
                PixelPressRoot(controller)
            }
        }
    }
}
