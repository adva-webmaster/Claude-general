package de.ingvoss.fotosortierer.core

/** Eindeutiger Schlüssel eines Mediums: MediaStore-IDs sind nur je Volume eindeutig. */
data class MediaKey(val volume: String, val mediaStoreId: Long)

/** Ein beim Scan des MediaStore gefundenes Medium. */
data class ScannedMedia(
    val volume: String,
    val mediaStoreId: Long,
    val kind: MediaKind,
    val displayName: String,
    val album: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val time: ResolvedTime
) {
    val key: MediaKey get() = MediaKey(volume, mediaStoreId)
}

/** Ein in der App-Datenbank bereits bekanntes Medium. */
data class KnownMedia(
    val uid: Long,
    val volume: String,
    val mediaStoreId: Long,
    val status: MediaStatus,
    val kind: MediaKind,
    val displayName: String,
    val album: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val takenAtUtc: Long,
    val localDate: String,
    val localMonth: String
) {
    val key: MediaKey get() = MediaKey(volume, mediaStoreId)
}

/** Geänderte Metadaten eines bekannten Mediums (Status bleibt unberührt, außer bei Revival). */
data class MediaUpdate(val uid: Long, val scanned: ScannedMedia)

/**
 * Ergebnis des Abgleichs zwischen MediaStore und Datenbank.
 *  - [inserts]: neue Medien → als UNBEARBEITET aufnehmen.
 *  - [updates]: bekannte Medien mit geänderten Metadaten (Datum, Album, Größe, …).
 *  - [removals]: auf anderem Weg gelöschte Medien (nicht mehr im MediaStore oder Datei fehlt) → Zeile aus der
 *    Datenbank entfernen. Nur was die App selbst löscht, bleibt als Tombstone (GELOESCHT) in der Statistik.
 *  - [revivals]: Tombstones, die wieder im MediaStore auftauchen (z. B. aus dem System-Papierkorb
 *    wiederhergestellt) → Metadaten aktualisieren und zurück auf UNBEARBEITET.
 */
data class SyncPlan(
    val inserts: List<ScannedMedia>,
    val updates: List<MediaUpdate>,
    val removals: List<KnownMedia>,
    val revivals: List<MediaUpdate>
) {
    val isEmpty: Boolean
        get() = inserts.isEmpty() && updates.isEmpty() && removals.isEmpty() && revivals.isEmpty()
}

object SyncPlanner {
    /**
     * @param scannedVolumes Volumes, für die ein Verschwinden aus dem MediaStore als Löschung gilt. Medien von
     *   Volumes, die gerade nicht verfügbar sind (z. B. ausgeworfene SD-Karte), werden NIE entfernt.
     * @param missingFiles Medien, die der MediaStore noch listet, deren Datei aber nicht mehr existiert. Sie werden
     *   weder aufgenommen noch aktualisiert oder wiederbelebt; bekannte werden entfernt (außer Tombstones, die
     *   bleiben, wie sie sind).
     */
    fun plan(
        known: List<KnownMedia>,
        scanned: List<ScannedMedia>,
        scannedVolumes: Set<String>,
        missingFiles: Set<MediaKey> = emptySet()
    ): SyncPlan {
        val scannedByKey = LinkedHashMap<MediaKey, ScannedMedia>(scanned.size * 2)
        for (s in scanned) if (s.key !in missingFiles) scannedByKey[s.key] = s
        val knownByKey = HashMap<MediaKey, KnownMedia>(known.size * 2)
        for (k in known) knownByKey[k.key] = k

        val inserts = ArrayList<ScannedMedia>()
        val updates = ArrayList<MediaUpdate>()
        val revivals = ArrayList<MediaUpdate>()
        for ((key, s) in scannedByKey) {
            val k = knownByKey[key]
            when {
                k == null -> inserts.add(s)
                k.status == MediaStatus.GELOESCHT -> revivals.add(MediaUpdate(k.uid, s))
                changed(k, s) -> updates.add(MediaUpdate(k.uid, s))
            }
        }

        // Schutz: Liefert der Scan gar nichts, obwohl Medien bekannt sind (z. B. Berechtigung entzogen,
        // MediaStore noch nicht bereit), wird wegen "nicht mehr im MediaStore" nichts entfernt.
        val vanished = scannedByKey.isNotEmpty()
        val removals = known.filter {
            it.status != MediaStatus.GELOESCHT &&
                (it.key in missingFiles || (vanished && it.volume in scannedVolumes && it.key !in scannedByKey))
        }
        return SyncPlan(inserts, updates, removals, revivals)
    }

    private fun changed(k: KnownMedia, s: ScannedMedia): Boolean =
        k.takenAtUtc != s.time.utcMillis ||
            k.localDate != s.time.localDate ||
            k.localMonth != s.time.localMonth ||
            k.album != s.album ||
            k.kind != s.kind ||
            k.sizeBytes != s.sizeBytes ||
            k.durationMs != s.durationMs ||
            k.displayName != s.displayName
}
