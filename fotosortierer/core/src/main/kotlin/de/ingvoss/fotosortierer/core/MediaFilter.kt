package de.ingvoss.fotosortierer.core

/**
 * Filter, der eine Sortiersitzung (oder eine Medienliste) definiert. Alle Felder sind optional und
 * werden mit UND verknüpft. Medien, die per Einstellung ausgeblendet sind (Medientyp/Album), fallen
 * immer heraus (Spalte `hidden`).
 */
data class MediaFilter(
    val localDate: String? = null,
    val localMonth: String? = null,
    val clusterId: Long? = null,
    /** Nur Medien ohne GPS-Position ("Ohne Ort"). */
    val withoutPlace: Boolean = false,
    val album: String? = null,
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

    companion object {
        val ALL = MediaFilter()
    }
}
