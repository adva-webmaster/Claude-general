package de.ingvoss.fotosortierer.core

/**
 * Bearbeitungsstatus eines Mediums. Die [code]s werden in der Datenbank gespeichert und in den
 * SQL-Bausteinen ([MediaSql]) verwendet – nicht ändern, ohne eine Migration zu schreiben.
 */
enum class MediaStatus(val code: Int) {
    /** Noch nicht angesehen. */
    UNBEARBEITET(0),

    /** Behalten (bearbeitet). */
    BEHALTEN(1),

    /** Nur in der App zum Löschen markiert (bearbeitet), noch nicht vom System gelöscht. */
    PAPIERKORB(2),

    /** Zurückgestellt: zählt ausdrücklich NICHT als bearbeitet. */
    ZURUECKGESTELLT(3),

    /** Tombstone: Medium wurde (bestätigt oder extern) gelöscht (bearbeitet). */
    GELOESCHT(4);

    /** "Bearbeitet" im Sinne der Statuslogik: BEHALTEN, PAPIERKORB oder GELOESCHT. */
    val isProcessed: Boolean
        get() = this == BEHALTEN || this == PAPIERKORB || this == GELOESCHT

    /** Noch offen: UNBEARBEITET oder ZURUECKGESTELLT. */
    val isOpen: Boolean
        get() = this == UNBEARBEITET || this == ZURUECKGESTELLT

    companion object {
        fun fromCode(code: Int): MediaStatus =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unbekannter Statuscode: $code")
    }
}

/** Art eines Mediums. Codes werden in der Datenbank gespeichert. */
enum class MediaKind(val code: Int) {
    FOTO(0),
    VIDEO(1),
    SCREENSHOT(2);

    companion object {
        fun fromCode(code: Int): MediaKind =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unbekannter Medientyp-Code: $code")
    }
}
