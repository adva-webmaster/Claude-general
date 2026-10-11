package de.ingvoss.fotosortierer.core

import kotlin.math.abs

/**
 * Zoom und Verschiebung der Sortierkarte (Pixel). Rein rechnerisch, damit sich die Geometrie ohne Compose
 * testen lässt. Das Bild wird um seine Mitte skaliert; [panX]/[panY] ist die Verschiebung der Mitte.
 */
data class ZoomPan(val scale: Float = 1f, val panX: Float = 0f, val panY: Float = 0f) {
    val isZoomed: Boolean get() = scale > ZOOMED_THRESHOLD

    /**
     * Wendet eine Zwei-Finger-Geste an: Zoomfaktor [zoom] um den Berührpunkt ([centroidX]/[centroidY],
     * relativ zur Kartenmitte) plus Verschiebung ([dx]/[dy]). Der Berührpunkt bleibt dabei unter den
     * Fingern; das Ergebnis liegt immer innerhalb der Bildgrenzen.
     */
    fun transform(
        zoom: Float,
        centroidX: Float,
        centroidY: Float,
        dx: Float,
        dy: Float,
        width: Float,
        height: Float
    ): ZoomPan {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = if (scale == 0f) 1f else newScale / scale
        return ZoomPan(
            newScale,
            centroidX - (centroidX - panX) * ratio + dx,
            centroidY - (centroidY - panY) * ratio + dy
        ).clamped(width, height)
    }

    /** Auf [target] zoomen, sodass der Punkt ([tapX]/[tapY], in Kartenkoordinaten) unter dem Finger bleibt. */
    fun zoomedTo(target: Float, tapX: Float, tapY: Float, width: Float, height: Float): ZoomPan {
        val cx = tapX - width / 2f
        val cy = tapY - height / 2f
        return ZoomPan(target, -(target - 1f) * cx, -(target - 1f) * cy).clamped(width, height)
    }

    /** Verschiebung auf das begrenzen, was das vergrößerte Bild abdeckt. */
    fun clamped(width: Float, height: Float): ZoomPan {
        val maxX = abs(width * (scale - 1f) / 2f)
        val maxY = abs(height * (scale - 1f) / 2f)
        return copy(panX = panX.coerceIn(-maxX, maxX), panY = panY.coerceIn(-maxY, maxY))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
        /** Ab diesem Faktor gilt das Bild als vergrößert (Wischen ist dann aus, Ziehen verschiebt das Bild). */
        const val ZOOMED_THRESHOLD = 1.02f
    }
}
