package de.ingvoss.pixelpress.core

/** Eine gefundene Quelldatei (absoluter Pfad). */
data class SourceFile(val path: String, val sizeBytes: Long)

enum class SkipReason {
    /** Neben der Quelle liegt schon eine MP4-Datei gleichen Namens. */
    OUTPUT_EXISTS
}

/** Ein geplanter Auftrag: [source] → [outputPath]; mit [skip] wird er nicht ausgeführt. */
data class PlannedJob(val source: SourceFile, val outputPath: String, val skip: SkipReason?)

object ConversionPlanner {
    private val sourceExtensions = setOf("avi", "mov")

    fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    /** Ist die Datei ein AVI oder MOV (Groß-/Kleinschreibung egal)? */
    fun isSource(fileName: String): Boolean = extensionOf(fileName) in sourceExtensions

    /**
     * Plant die Ausgabedateien: Die MP4 liegt neben der Quelle und heißt wie sie ("urlaub.avi" → "urlaub.mp4").
     * Mit [skipExisting] wird übersprungen, was schon eine MP4 gleichen Namens hat ([existingPaths] = vorhandene Dateien).
     * Es wird nie etwas überschrieben: Ist der Name vergeben (vorhanden oder in diesem Lauf geplant, z. B. wenn
     * "film.avi" und "film.mov" nebeneinander liegen), heißt die Datei "film_mov.mp4", dann "film_mov_2.mp4" usw.
     */
    fun plan(files: List<SourceFile>, existingPaths: Set<String>, skipExisting: Boolean): List<PlannedJob> {
        val existing = existingPaths.mapTo(HashSet()) { it.lowercase() }
        val taken = HashSet(existing)
        val jobs = ArrayList<PlannedJob>(files.size)
        for (file in files.sortedBy { it.path.lowercase() }) {
            val directory = file.path.substringBeforeLast('/', "")
            val name = file.path.substringAfterLast('/')
            val base = name.substringBeforeLast('.', name)
            val extension = extensionOf(name)
            val primary = "$directory/$base.mp4"
            if (skipExisting && primary.lowercase() in existing) {
                jobs += PlannedJob(file, primary, SkipReason.OUTPUT_EXISTS)
                continue
            }
            var candidate = primary
            var counter = 1
            while (candidate.lowercase() in taken) {
                counter++
                candidate = if (counter == 2) "$directory/${base}_$extension.mp4" else "$directory/${base}_${extension}_${counter - 1}.mp4"
            }
            taken += candidate.lowercase()
            jobs += PlannedJob(file, candidate, null)
        }
        return jobs
    }
}
