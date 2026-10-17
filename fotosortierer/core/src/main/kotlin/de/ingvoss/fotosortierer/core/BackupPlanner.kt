package de.ingvoss.fotosortierer.core

/** Ein Eintrag der JSON-Sicherung (nur Medien mit einem anderen Status als UNBEARBEITET). */
data class BackupItem(
    val volume: String,
    val mediaStoreId: Long,
    val displayName: String,
    val takenAtUtc: Long,
    val sizeBytes: Long,
    val status: MediaStatus,
    val statusChangedAt: Long
)

/** Zu setzender Status für ein vorhandenes Medium. */
data class BackupMatch(val uid: Long, val status: MediaStatus, val statusChangedAt: Long)

data class BackupPlan(val matches: List<BackupMatch>, val unmatched: Int)

/** Abschnitte einer Sicherung; beim Export und beim Import einzeln wählbar. [key] steht in der Sicherungsdatei. */
enum class BackupSection(val key: String) {
    /** Entscheidungen: behalten, Papierkorb, zurückgestellt. */
    DECISIONS("decisions"),

    /** Von der App gelöschte Medien (Statistik "gelöscht" und "Speicher freigegeben"). */
    DELETED("deleted"),

    /** Standorte der Medien und Ortsnamen (Zwischenspeicher der Namensauflösung). */
    LOCATIONS("locations"),

    /** Allgemeine Einstellungen (Wischbelegung, Reihenfolge, Darstellung, …). */
    SETTINGS("settings"),

    /** Favoriten und ausgeblendete Ordner. */
    FOLDERS("folders");

    companion object {
        fun fromKey(key: String): BackupSection? = entries.firstOrNull { it.key == key }
    }
}

/** Ein von der App gelöschtes Medium (Tombstone) mit allem, was die Statistik und der Kalender brauchen. */
data class BackupDeleted(
    val volume: String,
    val mediaStoreId: Long,
    val displayName: String,
    val album: String,
    val relativePath: String,
    val kind: MediaKind,
    val takenAtUtc: Long,
    val localDate: String,
    val localMonth: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val statusChangedAt: Long
)

/** [toInsert]: als Tombstone neu anzulegen; [alreadyKnown]: gibt es schon (als Medium oder Tombstone). */
data class DeletedPlan(val toInsert: List<BackupDeleted>, val alreadyKnown: Int)

/** Position eines Mediums aus der Sicherung. */
data class BackupLocation(
    val volume: String,
    val mediaStoreId: Long,
    val displayName: String,
    val takenAtUtc: Long,
    val sizeBytes: Long,
    val latitude: Double,
    val longitude: Double
)

data class LocationMatch(val uid: Long, val latitude: Double, val longitude: Double)

data class LocationPlan(val matches: List<LocationMatch>, val unmatched: Int)

/**
 * Ordnet Einträge einer Sicherung den aktuell bekannten Medien zu. MediaStore-IDs können sich nach einer
 * Neuinstallation oder auf einem anderen Gerät ändern; deshalb zählt zuerst die Kombination aus Name,
 * Aufnahmezeit und Größe. Ist sie nicht eindeutig, entscheidet zusätzlich (Volume, ID); sonst bleibt der
 * Eintrag unzugeordnet.
 */
object BackupPlanner {
    private data class Fingerprint(val name: String, val takenAtUtc: Long, val sizeBytes: Long)

    /** Zuordnung mit Vergabe: Jedes bekannte Medium kann nur einem Eintrag der Sicherung entsprechen. */
    private class Matcher(known: List<KnownMedia>) {
        private val byFingerprint = HashMap<Fingerprint, MutableList<KnownMedia>>()
        private val claimed = HashSet<Long>()

        init {
            for (k in known) {
                byFingerprint.getOrPut(Fingerprint(k.displayName, k.takenAtUtc, k.sizeBytes)) { ArrayList(1) }.add(k)
            }
        }

        fun take(name: String, takenAtUtc: Long, sizeBytes: Long, volume: String, mediaStoreId: Long): KnownMedia? {
            val candidates = byFingerprint[Fingerprint(name, takenAtUtc, sizeBytes)]?.filter { it.uid !in claimed }
            val target = when {
                candidates.isNullOrEmpty() -> null
                candidates.size == 1 -> candidates[0]
                else -> candidates.firstOrNull { it.volume == volume && it.mediaStoreId == mediaStoreId }
            }
            target?.let { claimed.add(it.uid) }
            return target
        }
    }

    /** Entscheidungen: [known] sollte nur vorhandene Medien enthalten (keine Tombstones). */
    fun plan(items: List<BackupItem>, known: List<KnownMedia>): BackupPlan {
        val matcher = Matcher(known)
        val matches = ArrayList<BackupMatch>()
        var unmatched = 0
        for (item in items) {
            val target = matcher.take(item.displayName, item.takenAtUtc, item.sizeBytes, item.volume, item.mediaStoreId)
            if (target == null) {
                unmatched++
            } else {
                matches.add(BackupMatch(target.uid, item.status, item.statusChangedAt))
            }
        }
        return BackupPlan(matches, unmatched)
    }

    /**
     * Gelöschte Medien: Was es mit gleichem Namen, gleicher Aufnahmezeit und Größe schon gibt (als Medium oder als
     * Tombstone, [known] mit allen Status), wird nicht doppelt angelegt. Gleiche Fingerabdrücke zählen mengenweise.
     */
    fun planDeleted(items: List<BackupDeleted>, known: List<KnownMedia>): DeletedPlan {
        val available = HashMap<Fingerprint, Int>()
        for (k in known) available.merge(Fingerprint(k.displayName, k.takenAtUtc, k.sizeBytes), 1, Int::plus)
        val toInsert = ArrayList<BackupDeleted>()
        var already = 0
        for (item in items) {
            val key = Fingerprint(item.displayName, item.takenAtUtc, item.sizeBytes)
            val count = available[key] ?: 0
            if (count > 0) {
                available[key] = count - 1
                already++
            } else {
                toInsert.add(item)
            }
        }
        return DeletedPlan(toInsert, already)
    }

    /** Positionen: [known] sollte nur vorhandene Medien enthalten. */
    fun planLocations(items: List<BackupLocation>, known: List<KnownMedia>): LocationPlan {
        val matcher = Matcher(known)
        val matches = ArrayList<LocationMatch>()
        var unmatched = 0
        for (item in items) {
            val target = matcher.take(item.displayName, item.takenAtUtc, item.sizeBytes, item.volume, item.mediaStoreId)
            if (target == null) unmatched++ else matches.add(LocationMatch(target.uid, item.latitude, item.longitude))
        }
        return LocationPlan(matches, unmatched)
    }
}
