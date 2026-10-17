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
            hidden INTEGER NOT NULL,
            relativePath TEXT NOT NULL DEFAULT ''
        )
    """
}

/** SQL-Text plus Argumente (für `SimpleSQLiteQuery` bzw. JDBC). */
data class SqlQuery(val sql: String, val args: List<Any>)

/** Phase einer Sortiersitzung: erst UNBEARBEITET, danach ZURUECKGESTELLT. */
enum class QueuePhase {
    UNBEARBEITET,
    ZURUECKGESTELLT,

    /**
     * Erneutes Sortieren bereits entschiedener Medien: alle Medien mit den Status des Filters, die vor Beginn der
     * Sitzung entschieden wurden. In dieser Sitzung neu Entschiedene fallen heraus – so endet die Sitzung auch,
     * wenn jemand wieder denselben Status wählt.
     */
    ERNEUT
}

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

    /** Ordner (Alben) mit Zählern – nur sichtbare Medien; ausgeblendete Ordner kommen aus [Q_ALBUMS]. */
    const val Q_FOLDERS =
        "SELECT album AS groupKey, $COUNT_COLUMNS FROM media_state WHERE hidden = 0 GROUP BY album"

    /** Verzeichnisse (relativer Pfad, z. B. "Pictures/Urlaub/") mit Zählern der direkt darin liegenden sichtbaren Medien. */
    const val Q_DIRECTORIES =
        "SELECT relativePath AS groupKey, $COUNT_COLUMNS FROM media_state " +
            "WHERE hidden = 0 AND relativePath != '' GROUP BY relativePath"

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
        filter.localYear?.let { parts += "localMonth LIKE ?"; args += "$it-%" }
        filter.clusterId?.let { parts += "clusterId = ?"; args += it }
        filter.clusterIds?.let { ids ->
            // IDs sind Long-Zahlen, deshalb gefahrlos direkt eingesetzt (kein Parameterlimit bei großen Clustern).
            parts += if (ids.isEmpty()) "0 = 1" else "clusterId IN (" + ids.joinToString(",") + ")"
        }
        if (filter.withoutPlace) parts += "latitude IS NULL"
        filter.album?.let { parts += "album = ?"; args += it }
        filter.pathPrefix?.let { prefix ->
            // Verzeichnis samt allen Unterverzeichnissen: exakter Präfixvergleich (ohne LIKE-Sonderzeichen).
            val normalized = MediaClassifier.normalizePath(prefix)
            parts += "substr(relativePath, 1, ?) = ?"
            args += normalized.length
            args += normalized
        }
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

    /** Nur die IDs (uid) eines Filters, z. B. für "Alle auswählen". */
    fun uids(filter: MediaFilter, ascending: Boolean = true): SqlQuery {
        val args = mutableListOf<Any>()
        val where = baseConditions(filter, args).joinToString(" AND ")
        return SqlQuery("SELECT uid FROM media_state WHERE $where ${order(ascending)}", args)
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
            QueuePhase.ERNEUT -> {
                args += phase2StartedAt
                "statusChangedAt < ?"
            }
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
        val parts = queueConditions(filter, phase, args)
        parts += phaseCondition(phase, phase2StartedAt, args)
        return SqlQuery(
            "SELECT * FROM media_state WHERE ${parts.joinToString(" AND ")} ${order(ascending)} LIMIT $limit",
            args
        )
    }

    /**
     * Zusammenfassung einer Sitzung: Zähler der in dieser Sitzung (seit [startedAt]) getroffenen
     * Entscheidungen im Filter plus die Größe der in den Papierkorb gelegten Medien (Spalte `trashBytes`).
     * Neu indexierte Medien haben `statusChangedAt = 0` und zählen deshalb nie mit.
     */
    fun sessionSummary(filter: MediaFilter, startedAt: Long): SqlQuery {
        val args = mutableListOf<Any>()
        val parts = baseConditions(filter.copy(statuses = null), args)
        parts += "statusChangedAt >= ?"
        args += startedAt
        return SqlQuery(
            "SELECT 'sitzung' AS groupKey, $COUNT_COLUMNS, " +
                "COALESCE(SUM(CASE WHEN status = 2 THEN sizeBytes ELSE 0 END), 0) AS trashBytes " +
                "FROM media_state WHERE ${parts.joinToString(" AND ")}",
            args
        )
    }

    /** Anzahl der Medien in der Warteschlange einer Phase. */
    fun queueCount(filter: MediaFilter, phase: QueuePhase, phase2StartedAt: Long): SqlQuery {
        val args = mutableListOf<Any>()
        val parts = queueConditions(filter, phase, args)
        parts += phaseCondition(phase, phase2StartedAt, args)
        return SqlQuery("SELECT COUNT(*) FROM media_state WHERE ${parts.joinToString(" AND ")}", args)
    }

    /**
     * Grundbedingungen einer Warteschlange. Beim normalen Sortieren ersetzt die Phase die Statusbedingung des
     * Filters; beim erneuten Sortieren ([QueuePhase.ERNEUT]) gelten die Status des Filters (ohne Angabe: alle
     * außer GELOESCHT).
     */
    private fun queueConditions(filter: MediaFilter, phase: QueuePhase, args: MutableList<Any>): MutableList<String> =
        if (phase == QueuePhase.ERNEUT) {
            baseConditions(filter, args).also { if (filter.statuses == null) it += "status != 4" }
        } else {
            baseConditions(filter.copy(statuses = null), args)
        }

    // ------------------------------------------------------------------------------------
    // Gruppierungen innerhalb eines Filters (Kalender je Ort, Karte je Zeitraum)
    // ------------------------------------------------------------------------------------

    private fun grouped(select: String, filter: MediaFilter, extra: String?, groupBy: String, orderBy: String?): SqlQuery {
        val args = mutableListOf<Any>()
        val parts = baseConditions(filter.copy(statuses = null), args)
        if (extra != null) parts += extra
        val order = if (orderBy != null) " ORDER BY $orderBy" else ""
        return SqlQuery("SELECT $select FROM media_state WHERE ${parts.joinToString(" AND ")} GROUP BY $groupBy$order", args)
    }

    /** Monate (mit Zählern) der Medien des Filters. */
    fun groupedMonths(filter: MediaFilter): SqlQuery =
        grouped("localMonth AS groupKey, $COUNT_COLUMNS", filter, null, "localMonth", "localMonth")

    /** Tage (mit Zählern) der Medien des Filters; der Filter enthält normalerweise `localMonth`. */
    fun groupedDays(filter: MediaFilter): SqlQuery =
        grouped("localDate AS groupKey, $COUNT_COLUMNS", filter, null, "localDate", "localDate")

    /** Orte (mit Zählern und Zeitraum) der Medien des Filters; nur Medien mit Position. */
    fun groupedPlaces(filter: MediaFilter): SqlQuery =
        grouped(
            "clusterId AS clusterId, MIN(takenAtUtc) AS firstAt, MAX(takenAtUtc) AS lastAt, $COUNT_COLUMNS",
            filter, "clusterId IS NOT NULL", "clusterId", null
        )
}
