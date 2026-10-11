package de.ingvoss.fotosortierer.core

/** Tabellen- und Spaltennamen der Tabelle `media_state` (Room-Entity `MediaStateEntity`). */
object MediaTable {
    const val NAME = "media_state"
    const val UID = "uid"
    const val MEDIA_STORE_ID = "mediaStoreId"
    const val VOLUME = "volume"
    const val TAKEN_AT = "takenAtUtc"
    const val LOCAL_DATE = "localDate"
    const val LOCAL_MONTH = "localMonth"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val CLUSTER_ID = "clusterId"
    const val ALBUM = "album"
    const val KIND = "kind"
    const val SIZE_BYTES = "sizeBytes"
    const val STATUS = "status"
    const val STATUS_CHANGED_AT = "statusChangedAt"
    const val DISPLAY_NAME = "displayName"
    const val DURATION_MS = "durationMs"
    const val LOCATION_CHECKED = "locationChecked"
    const val HIDDEN = "hidden"

    /**
     * Referenz-DDL, die der von Room erzeugten Tabelle entspricht. Wird von den SQL-Tests auf
     * einer echten SQLite-Datenbank benutzt; Room erzeugt die Tabelle selbst aus der Entity.
     */
    const val CREATE_SQL = """
        CREATE TABLE IF NOT EXISTS media_state (
            uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            mediaStoreId INTEGER NOT NULL,
            volume TEXT NOT NULL,
            takenAtUtc INTEGER NOT NULL,
            localDate TEXT NOT NULL,
            localMonth TEXT NOT NULL,
            latitude REAL,
            longitude REAL,
            clusterId INTEGER,
            album TEXT NOT NULL,
            kind INTEGER NOT NULL,
            sizeBytes INTEGER NOT NULL,
            status INTEGER NOT NULL,
            statusChangedAt INTEGER NOT NULL,
            displayName TEXT NOT NULL,
            durationMs INTEGER NOT NULL,
            locationChecked INTEGER NOT NULL,
            hidden INTEGER NOT NULL
        )
    """
}

/** SQL-Text plus Argumente (für `SimpleSQLiteQuery` bzw. JDBC). */
data class SqlQuery(val sql: String, val args: List<Any>)

/** Phase einer Sortiersitzung: erst UNBEARBEITET, danach ZURUECKGESTELLT. */
enum class QueuePhase { UNBEARBEITET, ZURUECKGESTELLT }

/**
 * Alle SQL-Bausteine für Aggregationen und Sitzungs-Warteschlangen. Die Aggregationen laufen in
 * SQLite (GROUP BY), nicht im Speicher. Die `const val`-Strings werden von den Room-DAOs als
 * `@Query` verwendet und von den JVM-Tests gegen echtes SQLite ausgeführt.
 */
object MediaSql {
    /** Zählerspalten – Aliasnamen entsprechen den Feldern von `StatusCountsRow` (Room-POJO). */
    const val COUNT_COLUMNS =
        "COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END), 0) AS unbearbeitet, " +
            "COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS behalten, " +
            "COALESCE(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END), 0) AS papierkorb, " +
            "COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS zurueckgestellt, " +
            "COALESCE(SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END), 0) AS geloescht"

    /** Monatsübersicht (alle Monate, auch für die Jahresebene). */
    const val Q_MONTHS =
        "SELECT localMonth AS groupKey, $COUNT_COLUMNS FROM media_state " +
            "WHERE hidden = 0 GROUP BY localMonth ORDER BY localMonth"

    /** Tage eines Monats (Parameter :month, z. B. "2026-03"). */
    const val Q_DAYS_OF_MONTH =
        "SELECT localDate AS groupKey, $COUNT_COLUMNS FROM media_state " +
            "WHERE hidden = 0 AND localMonth = :month GROUP BY localDate ORDER BY localDate"

    /** Orte (Ortscluster) mit Zählern und Zeitraum. */
    const val Q_PLACES =
        "SELECT clusterId AS clusterId, MIN(takenAtUtc) AS firstAt, MAX(takenAtUtc) AS lastAt, $COUNT_COLUMNS " +
            "FROM media_state WHERE hidden = 0 AND clusterId IS NOT NULL GROUP BY clusterId"

    /** "Ohne Ort": Medien ohne GPS. Liefert immer genau eine Zeile (Zähler 0 bei leerer Menge). */
    const val Q_NO_PLACE =
        "SELECT 'ohne-ort' AS groupKey, $COUNT_COLUMNS FROM media_state " +
            "WHERE hidden = 0 AND latitude IS NULL"

    /** Gesamtzähler aller sichtbaren Medien. Liefert immer genau eine Zeile. */
    const val Q_OVERALL =
        "SELECT 'gesamt' AS groupKey, $COUNT_COLUMNS FROM media_state WHERE hidden = 0"

    /** Alben (Bucket-Namen) mit Zählern – für Filter/Einstellungen; enthält auch ausgeblendete. */
    const val Q_ALBUMS =
        "SELECT album AS groupKey, COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END), 0) AS unbearbeitet, " +
            "COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS behalten, " +
            "COALESCE(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END), 0) AS papierkorb, " +
            "COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS zurueckgestellt, " +
            "COALESCE(SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END), 0) AS geloescht " +
            "FROM media_state WHERE status != 4 GROUP BY album ORDER BY album COLLATE NOCASE"

    /** Summe der Dateigrößen aller Medien im App-Papierkorb. */
    const val Q_TRASH_BYTES =
        "SELECT COALESCE(SUM(sizeBytes), 0) FROM media_state WHERE status = 2 AND hidden = 0"

    /** Summe der Dateigrößen aller gelöschten (Tombstone-)Medien = freigegebener Speicher. */
    const val Q_FREED_BYTES =
        "SELECT COALESCE(SUM(sizeBytes), 0) FROM media_state WHERE status = 4"

    /** Ältestes noch offenes (UNBEARBEITET) Medium: Einstieg für "Weitermachen". */
    const val Q_OLDEST_UNPROCESSED_TIME =
        "SELECT MIN(takenAtUtc) FROM media_state WHERE hidden = 0 AND status = 0"

    // ------------------------------------------------------------------------------------
    // Dynamische Abfragen (aus [MediaFilter])
    // ------------------------------------------------------------------------------------

    private fun baseConditions(filter: MediaFilter, args: MutableList<Any>): MutableList<String> {
        val parts = mutableListOf("hidden = 0")
        filter.localDate?.let { parts += "localDate = ?"; args += it }
        filter.localMonth?.let { parts += "localMonth = ?"; args += it }
        filter.clusterId?.let { parts += "clusterId = ?"; args += it }
        if (filter.withoutPlace) parts += "latitude IS NULL"
        filter.album?.let { parts += "album = ?"; args += it }
        filter.kind?.let { parts += "kind = ?"; args += it.code }
        val statusSet = filter.statuses
        if (statusSet != null) {
            if (statusSet.isEmpty()) {
                parts.add("0 = 1")
            } else {
                parts.add("status IN (" + statusSet.joinToString(",") { it.code.toString() } + ")")
            }
        }
        return parts
    }

    private fun order(ascending: Boolean) =
        if (ascending) "ORDER BY takenAtUtc ASC, uid ASC" else "ORDER BY takenAtUtc DESC, uid DESC"

    /** Medienliste für Raster/Paging (Room hängt LIMIT/OFFSET selbst an). */
    fun list(filter: MediaFilter, ascending: Boolean = true): SqlQuery {
        val args = mutableListOf<Any>()
        val where = baseConditions(filter, args).joinToString(" AND ")
        return SqlQuery("SELECT * FROM media_state WHERE $where ${order(ascending)}", args)
    }

    /** Anzahl Medien eines Filters. */
    fun count(filter: MediaFilter): SqlQuery {
        val args = mutableListOf<Any>()
        val where = baseConditions(filter, args).joinToString(" AND ")
        return SqlQuery("SELECT COUNT(*) FROM media_state WHERE $where", args)
    }

    /** Statuszähler eines Filters (eine Zeile). */
    fun counts(filter: MediaFilter): SqlQuery {
        val args = mutableListOf<Any>()
        val where = baseConditions(filter, args).joinToString(" AND ")
        return SqlQuery("SELECT 'filter' AS groupKey, $COUNT_COLUMNS FROM media_state WHERE $where", args)
    }

    private fun phaseCondition(phase: QueuePhase, phase2StartedAt: Long, args: MutableList<Any>): String =
        when (phase) {
            QueuePhase.UNBEARBEITET -> "status = 0"
            QueuePhase.ZURUECKGESTELLT -> {
                // Erneut zurückgestellte Medien (statusChangedAt >= Beginn Phase 2) kommen in dieser
                // Sitzung nicht noch einmal dran – sonst gäbe es eine Endlosschleife.
                args += phase2StartedAt
                "status = 3 AND statusChangedAt < ?"
            }
        }

    /**
     * Die nächsten [limit] Medien einer Sitzungsphase (aktuelles Medium + Vorlade-Karten).
     * Die Statusbedingung des Filters wird durch die Phase ersetzt.
     */
    fun queue(
        filter: MediaFilter,
        phase: QueuePhase,
        phase2StartedAt: Long,
        ascending: Boolean,
        limit: Int
    ): SqlQuery {
        val args = mutableListOf<Any>()
        val parts = baseConditions(filter.copy(statuses = null), args)
        parts += phaseCondition(phase, phase2StartedAt, args)
        return SqlQuery(
            "SELECT * FROM media_state WHERE ${parts.joinToString(" AND ")} ${order(ascending)} LIMIT $limit",
            args
        )
    }

    /** Anzahl der Medien in der Warteschlange einer Phase. */
    fun queueCount(filter: MediaFilter, phase: QueuePhase, phase2StartedAt: Long): SqlQuery {
        val args = mutableListOf<Any>()
        val parts = baseConditions(filter.copy(statuses = null), args)
        parts += phaseCondition(phase, phase2StartedAt, args)
        return SqlQuery("SELECT COUNT(*) FROM media_state WHERE ${parts.joinToString(" AND ")}", args)
    }
}
