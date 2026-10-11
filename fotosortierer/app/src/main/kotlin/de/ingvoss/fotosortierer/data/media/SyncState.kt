package de.ingvoss.fotosortierer.data.media

/** Zustand des MediaStore-Abgleichs für die Oberfläche. */
data class SyncState(
    val running: Boolean = false,
    /** Anzahl bisher gelesener Medien während des Scans. */
    val scanned: Int = 0,
    val lastSyncAt: Long = 0L,
    val lastError: String? = null,
    /** Beim letzten Abgleich neu hinzugekommene Medien. */
    val lastInserted: Int = 0
)
