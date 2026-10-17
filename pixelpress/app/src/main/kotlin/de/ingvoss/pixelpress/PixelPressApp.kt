package de.ingvoss.pixelpress

import android.app.Application

class PixelPressApp : Application() {
    val controller: ConversionController by lazy { ConversionController(this) }
}
