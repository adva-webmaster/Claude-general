package de.ingvoss.fotosortierer.core

/**
 * Filter, der eine Sortiersitzung (oder eine Medienliste) definiert. Alle Felder sind optional und
 * werden mit UND verknüpft. Medien, die per Einstellung ausgeblendet sind (Medientyp/Album), fallen
 * immer heraus (Spalte `hidden`).
 */
data class MediaFilter(
    val localDate: String? = null,
    val localMonth: String? = null,
    /** Jahr, z. B. "2026" (alle Monate dieses Jahres). */
    val localYear: String? = null,
    val clusterId: Long? = null,
    /** Mehrere Orte auf einmal (z. B. alle Orte eines Kartenclusters). */
    val clusterIds: Set<Long>? = null,
    /** Nur Medien ohne GPS-Position ("Ohne Ort"). */
    val withoutPlace: Boolean = false,
    val album: String? = null,
    /** Verzeichnis samt allen Unterverzeichnissen, z. B. "Pictures/Urlaub/" (nicht "Pictures/Ur"). */
    val pathPrefix: String? = null,
    val kind: MediaKind? = null,
    /** Einschränkung auf bestimmte Status; null = alle. */
    val statuses: Set<MediaStatus>? = null
) {
    /** Sitzung darf UNBEARBEITETE enthalten? */
    val includesUnprocessed: Boolean
        get() = statuses == null || MediaStatus.UNBEARBEITET in statuses

    /** Sitzung darf ZURUECKGESTELLTE enthalten? */
    val includesDeferred: Boolean
        get() = statuses == null || MediaStatus.ZURUECKGESTELLT in statuses

    /** Schränkt das Ergebnis auf bestimmte Orte ein (einzelner Ort, mehrere Orte oder "Ohne Ort")? */
    val hasPlaceScope: Boolean
        get() = clusterId != null || clusterIds != null || withoutPlace

    /** Schränkt das Ergebnis auf einen Zeitraum (Tag, Monat, Jahr) ein? */
    val hasTimeScope: Boolean
        get() = localDate != null || localMonth != null || localYear != null

    /** Nur die Zeitbedingungen dieses Filters – für die Karte "dieses Jahr/diesen Monat/diesen Tag zeigen". */
    fun timeScope() = MediaFilter(localDate = localDate, localMonth = localMonth, localYear = localYear)

    /** Nur die Ortsbedingungen dieses Filters – für den Kalender "diesen Ort zeigen". */
    fun placeScope() = MediaFilter(clusterId = clusterId, clusterIds = clusterIds, withoutPlace = withoutPlace)

    companion object {
        val ALL = MediaFilter()
    }
}
