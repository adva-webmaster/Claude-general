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

/**
 * Ordnet Einträge einer Sicherung den aktuell bekannten Medien zu. MediaStore-IDs können sich nach einer
 * Neuinstallation oder auf einem anderen Gerät ändern; deshalb zählt zuerst die Kombination aus Name,
 * Aufnahmezeit und Größe. Ist sie nicht eindeutig, entscheidet zusätzlich (Volume, ID); sonst bleibt der
 * Eintrag unzugeordnet.
 */
object BackupPlanner {
    private data class Fingerprint(val name: String, val takenAtUtc: Long, val sizeBytes: Long)

    fun plan(items: List<BackupItem>, known: List<KnownMedia>): BackupPlan {
        val byFingerprint = HashMap<Fingerprint, MutableList<KnownMedia>>()
        for (k in known) {
            byFingerprint.getOrPut(Fingerprint(k.displayName, k.takenAtUtc, k.sizeBytes)) { ArrayList(1) }.add(k)
        }
        val matches = ArrayList<BackupMatch>()
        val claimed = HashSet<Long>()
        var unmatched = 0
        for (item in items) {
            val candidates = byFingerprint[Fingerprint(item.displayName, item.takenAtUtc, item.sizeBytes)]
                ?.filter { it.uid !in claimed }
            val target = when {
                candidates.isNullOrEmpty() -> null
                candidates.size == 1 -> candidates[0]
                else -> candidates.firstOrNull { it.volume == item.volume && it.mediaStoreId == item.mediaStoreId }
            }
            if (target == null) {
                unmatched++
            } else {
                claimed.add(target.uid)
                matches.add(BackupMatch(target.uid, item.status, item.statusChangedAt))
            }
        }
        return BackupPlan(matches, unmatched)
    }
}
