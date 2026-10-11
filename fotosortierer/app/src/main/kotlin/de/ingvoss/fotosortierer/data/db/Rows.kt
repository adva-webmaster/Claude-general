package de.ingvoss.fotosortierer.data.db

import de.ingvoss.fotosortierer.core.StatusCounts

/** Ergebniszeile einer Gruppierung (Tag, Monat, Album, gesamt …). Spaltennamen siehe `MediaSql.COUNT_COLUMNS`. */
data class GroupCountsRow(
    val groupKey: String,
    val total: Int,
    val unbearbeitet: Int,
    val behalten: Int,
    val papierkorb: Int,
    val zurueckgestellt: Int,
    val geloescht: Int
) {
    fun toCounts() = StatusCounts(unbearbeitet, behalten, papierkorb, zurueckgestellt, geloescht)
}

/** Ergebniszeile der Ortsgruppierung. */
data class PlaceCountsRow(
    val clusterId: Long,
    val firstAt: Long,
    val lastAt: Long,
    val total: Int,
    val unbearbeitet: Int,
    val behalten: Int,
    val papierkorb: Int,
    val zurueckgestellt: Int,
    val geloescht: Int
) {
    fun toCounts() = StatusCounts(unbearbeitet, behalten, papierkorb, zurueckgestellt, geloescht)
}

/** Ergebniszeile der Sitzungszusammenfassung (`MediaSql.sessionSummary`). */
data class SessionSummaryRow(
    val groupKey: String,
    val total: Int,
    val unbearbeitet: Int,
    val behalten: Int,
    val papierkorb: Int,
    val zurueckgestellt: Int,
    val geloescht: Int,
    val trashBytes: Long
) {
    fun toCounts() = StatusCounts(unbearbeitet, behalten, papierkorb, zurueckgestellt, geloescht)
}

/** Schlanke Projektion für den Sync-Abgleich (ohne Standort und Entscheidungszeit). */
data class KnownRow(
    val uid: Long,
    val volume: String,
    val mediaStoreId: Long,
    val status: Int,
    val kind: Int,
    val displayName: String,
    val album: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val takenAtUtc: Long,
    val localDate: String,
    val localMonth: String
)

/** Medium, dessen EXIF-/Video-Standort noch gelesen werden muss. */
data class LocationJobRow(val uid: Long, val volume: String, val mediaStoreId: Long, val kind: Int)

/** Fortschritt des Standort-Indexierens. */
data class LocationProgressRow(val total: Int, val checked: Int)

/** Medium mit Position für das Clustering. */
data class GeoPointRow(val id: Long, val lat: Double, val lon: Double)

/** Für Räumlichkeit: Mittelpunkt eines Clusters. */
data class CentroidRow(val clusterId: Long, val lat: Double, val lon: Double)
