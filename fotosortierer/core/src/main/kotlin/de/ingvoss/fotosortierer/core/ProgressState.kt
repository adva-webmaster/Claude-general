package de.ingvoss.fotosortierer.core

/** Zustand eines Tages, Monats, Jahres oder Ortes (aggregiert aus den enthaltenen Medien). */
enum class ProgressState {
    /** Keine Medien enthalten (neutral). */
    LEER,

    /** Kein Element ist bearbeitet. */
    UNBEARBEITET,

    /** Mindestens eines bearbeitet und mindestens eines noch offen. */
    TEILWEISE,

    /** Kein Element mehr offen (UNBEARBEITET oder ZURUECKGESTELLT). */
    VOLLSTAENDIG
}

/**
 * Zähler je Status für eine Gruppe von Medien. Alle Aggregationen (Tag, Monat, Jahr, Ort, gesamt)
 * laufen über diese Klasse, damit die Statuslogik an genau einer Stelle definiert ist.
 */
data class StatusCounts(
    val unbearbeitet: Int = 0,
    val behalten: Int = 0,
    val papierkorb: Int = 0,
    val zurueckgestellt: Int = 0,
    val geloescht: Int = 0
) {
    init {
        require(unbearbeitet >= 0 && behalten >= 0 && papierkorb >= 0 && zurueckgestellt >= 0 && geloescht >= 0) {
            "Zähler dürfen nicht negativ sein"
        }
    }

    val total: Int get() = unbearbeitet + behalten + papierkorb + zurueckgestellt + geloescht

    /** BEHALTEN + PAPIERKORB + GELOESCHT. Zurückgestellte zählen ausdrücklich nicht dazu. */
    val processed: Int get() = behalten + papierkorb + geloescht

    /** UNBEARBEITET + ZURUECKGESTELLT. */
    val open: Int get() = unbearbeitet + zurueckgestellt

    val hasDeferred: Boolean get() = zurueckgestellt > 0

    val state: ProgressState
        get() = when {
            total == 0 -> ProgressState.LEER
            open == 0 -> ProgressState.VOLLSTAENDIG
            processed == 0 -> ProgressState.UNBEARBEITET
            else -> ProgressState.TEILWEISE
        }

    /** Anteil bearbeitet an gesamt (0..1); 0 bei leerer Gruppe. */
    val fraction: Float get() = if (total == 0) 0f else processed.toFloat() / total

    operator fun plus(other: StatusCounts) = StatusCounts(
        unbearbeitet + other.unbearbeitet,
        behalten + other.behalten,
        papierkorb + other.papierkorb,
        zurueckgestellt + other.zurueckgestellt,
        geloescht + other.geloescht
    )

    fun count(status: MediaStatus): Int = when (status) {
        MediaStatus.UNBEARBEITET -> unbearbeitet
        MediaStatus.BEHALTEN -> behalten
        MediaStatus.PAPIERKORB -> papierkorb
        MediaStatus.ZURUECKGESTELLT -> zurueckgestellt
        MediaStatus.GELOESCHT -> geloescht
    }

    companion object {
        val EMPTY = StatusCounts()

        fun of(statuses: Iterable<MediaStatus>): StatusCounts {
            var u = 0
            var b = 0
            var p = 0
            var z = 0
            var g = 0
            for (s in statuses) {
                when (s) {
                    MediaStatus.UNBEARBEITET -> u++
                    MediaStatus.BEHALTEN -> b++
                    MediaStatus.PAPIERKORB -> p++
                    MediaStatus.ZURUECKGESTELLT -> z++
                    MediaStatus.GELOESCHT -> g++
                }
            }
            return StatusCounts(u, b, p, z, g)
        }
    }
}
