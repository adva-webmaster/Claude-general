package de.ingvoss.fotosortierer.core

import kotlin.math.abs
import kotlin.math.max

/** Richtung einer Wischgeste auf der Sortierkarte. Nach unten wird nicht gewischt. */
enum class SwipeDirection { LINKS, RECHTS, OBEN, KEINE }

/** Aktion, die mit einer Wischrichtung (oder einer Schaltfläche) ausgelöst wird. */
enum class SwipeAction(val status: MediaStatus) {
    BEHALTEN(MediaStatus.BEHALTEN),
    PAPIERKORB(MediaStatus.PAPIERKORB),
    ZURUECKSTELLEN(MediaStatus.ZURUECKGESTELLT)
}

/**
 * Belegung der drei Wischrichtungen mit den drei Aktionen. Jede Aktion kommt genau einmal vor.
 * Standard: links = BEHALTEN, rechts = PAPIERKORB, oben = ZURUECKSTELLEN.
 */
data class SwipeMapping(
    val links: SwipeAction = SwipeAction.BEHALTEN,
    val rechts: SwipeAction = SwipeAction.PAPIERKORB,
    val oben: SwipeAction = SwipeAction.ZURUECKSTELLEN
) {
    init {
        require(setOf(links, rechts, oben).size == 3) { "Jede Aktion muss genau einer Richtung zugeordnet sein" }
    }

    fun actionFor(direction: SwipeDirection): SwipeAction? = when (direction) {
        SwipeDirection.LINKS -> links
        SwipeDirection.RECHTS -> rechts
        SwipeDirection.OBEN -> oben
        SwipeDirection.KEINE -> null
    }

    fun directionOf(action: SwipeAction): SwipeDirection = when (action) {
        links -> SwipeDirection.LINKS
        rechts -> SwipeDirection.RECHTS
        else -> SwipeDirection.OBEN
    }

    /**
     * Weist [direction] die Aktion [action] zu. War die Aktion bereits einer anderen Richtung
     * zugeordnet, tauschen beide Richtungen ihre Aktionen – die Belegung bleibt so immer gültig.
     */
    fun with(direction: SwipeDirection, action: SwipeAction): SwipeMapping {
        if (direction == SwipeDirection.KEINE) return this
        val map = mutableMapOf(
            SwipeDirection.LINKS to links,
            SwipeDirection.RECHTS to rechts,
            SwipeDirection.OBEN to oben
        )
        val previous = map.getValue(direction)
        val other = map.entries.first { it.value == action }.key
        map[direction] = action
        map[other] = previous // ist other == direction, ist previous == action: keine Änderung
        return SwipeMapping(
            map.getValue(SwipeDirection.LINKS),
            map.getValue(SwipeDirection.RECHTS),
            map.getValue(SwipeDirection.OBEN)
        )
    }

    companion object {
        val STANDARD = SwipeMapping()

        /** Liest eine gespeicherte Belegung; bei fehlenden/ungültigen Werten gilt der Standard. */
        fun fromNames(links: String?, rechts: String?, oben: String?): SwipeMapping {
            fun parse(name: String?): SwipeAction? =
                name?.let { n -> SwipeAction.entries.firstOrNull { it.name == n } }
            val l = parse(links)
            val r = parse(rechts)
            val o = parse(oben)
            return if (l != null && r != null && o != null && setOf(l, r, o).size == 3) {
                SwipeMapping(l, r, o)
            } else {
                STANDARD
            }
        }
    }
}

/** Reine Geometrie der Wischgeste (px-Werte), unabhängig von Compose und damit testbar. */
object SwipeGeometry {
    const val DEFAULT_FLING_VELOCITY = 1200f

    /**
     * Die dominante Achse entscheidet (bei Gleichstand gewinnt die horizontale). Bewegungen innerhalb
     * der Totzone ([deadZonePx]) und Bewegungen nach unten liefern [SwipeDirection.KEINE].
     */
    fun directionOf(dx: Float, dy: Float, deadZonePx: Float = 0f): SwipeDirection {
        val ax = abs(dx)
        val ay = abs(dy)
        if (max(ax, ay) <= deadZonePx) return SwipeDirection.KEINE
        return if (ax >= ay) {
            if (dx < 0) SwipeDirection.LINKS else SwipeDirection.RECHTS
        } else {
            if (dy < 0) SwipeDirection.OBEN else SwipeDirection.KEINE
        }
    }

    /** Strecke entlang der dominanten Richtung. */
    private fun distance(direction: SwipeDirection, dx: Float, dy: Float): Float = when (direction) {
        SwipeDirection.LINKS, SwipeDirection.RECHTS -> abs(dx)
        SwipeDirection.OBEN -> -dy
        SwipeDirection.KEINE -> 0f
    }

    /** Fortschritt 0..1 bis zum Auslösen (für die Rückmeldung während des Ziehens). */
    fun progress(dx: Float, dy: Float, thresholdPx: Float): Float {
        require(thresholdPx > 0f) { "Schwelle muss positiv sein" }
        val direction = directionOf(dx, dy)
        return (distance(direction, dx, dy) / thresholdPx).coerceIn(0f, 1f)
    }

    /**
     * Entscheidet beim Loslassen. Ausgelöst wird, wenn die Strecke die Schwelle erreicht ODER eine
     * schnelle Wischgeste in dieselbe Richtung (Geschwindigkeit ≥ [flingVelocity]) mindestens ein
     * Viertel der Schwelle zurückgelegt hat. Sonst [SwipeDirection.KEINE] (Karte springt zurück).
     */
    fun resolve(
        dx: Float,
        dy: Float,
        velocityX: Float,
        velocityY: Float,
        thresholdPx: Float,
        flingVelocity: Float = DEFAULT_FLING_VELOCITY
    ): SwipeDirection {
        require(thresholdPx > 0f) { "Schwelle muss positiv sein" }
        val direction = directionOf(dx, dy)
        if (direction == SwipeDirection.KEINE) return SwipeDirection.KEINE
        val d = distance(direction, dx, dy)
        if (d >= thresholdPx) return direction
        val v = when (direction) {
            SwipeDirection.LINKS -> -velocityX
            SwipeDirection.RECHTS -> velocityX
            SwipeDirection.OBEN -> -velocityY
            SwipeDirection.KEINE -> 0f
        }
        return if (v >= flingVelocity && d >= thresholdPx * 0.25f) direction else SwipeDirection.KEINE
    }
}
