package de.ingvoss.pixelpress

import de.ingvoss.pixelpress.core.ConversionMode
import de.ingvoss.pixelpress.core.PlannedJob
import de.ingvoss.pixelpress.core.Quality
import de.ingvoss.pixelpress.core.Verdict

/** Einstellungen für einen Durchlauf (werden gemerkt). */
data class Options(
    val recursive: Boolean = true,
    val skipExisting: Boolean = true,
    val copyWhenPossible: Boolean = true,
    val quality: Quality = Quality.BALANCED,
    val deleteOriginals: Boolean = false
)

enum class ItemState { WAITING, SKIPPED, RUNNING, DONE, FAILED, CANCELLED }

/** Eine Zeile der Liste: der geplante Auftrag samt Stand. */
data class JobItem(
    val job: PlannedJob,
    val selected: Boolean,
    val state: ItemState,
    /** 0..1 beim Umwandeln; null = unbekannt. */
    val progress: Float? = null,
    val mode: ConversionMode? = null,
    val verdict: Verdict? = null,
    /** Fehlertext oder Hinweis zum Stand. */
    val message: String? = null,
    val outputBytes: Long = 0,
    val originalDeleted: Boolean = false
) {
    /** Bestätigt: MP4 liegt da und hat die Plausibilitätsprüfung bestanden – das Original dürfte gelöscht werden. */
    val verified: Boolean get() = state == ItemState.DONE && verdict?.ok == true
}

data class UiState(
    val directory: String? = null,
    val scanning: Boolean = false,
    val items: List<JobItem> = emptyList(),
    val options: Options = Options(),
    /** FFmpeg wird beim ersten Start entpackt. */
    val preparing: Boolean = false,
    val running: Boolean = false,
    /** Ein Durchlauf ist zu Ende (zeigt die Zusammenfassung). */
    val finished: Boolean = false,
    /** Soll jetzt nach dem Löschen der Originale gefragt werden? */
    val offerDeletion: Boolean = false,
    /** Allgemeiner Fehler (z. B. FFmpeg lässt sich nicht starten). */
    val error: String? = null
)
