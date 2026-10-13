package de.ingvoss.fotosortierer.core

/** Ein Ordner (Album) in der Ordnerliste. */
data class FolderEntry(
    val name: String,
    val counts: StatusCounts,
    val favorite: Boolean = false,
    val hidden: Boolean = false
)

/** Reihenfolge und Suche der Ordnerliste. */
object FolderOrder {
    /**
     * Favoriten zuerst, dann Ordner mit offener Arbeit, dann erledigte; innerhalb jeder Gruppe alphabetisch
     * (ohne Beachtung der Groß-/Kleinschreibung). Die Eingabereihenfolge spielt keine Rolle.
     */
    fun sort(entries: List<FolderEntry>): List<FolderEntry> =
        entries.sortedWith(
            compareBy<FolderEntry> { rank(it) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.name }
        )

    private fun rank(entry: FolderEntry): Int = when {
        entry.favorite -> 0
        entry.counts.open > 0 -> 1
        else -> 2
    }

    /** Behält Ordner, deren Name [query] enthält (Groß-/Kleinschreibung egal); leere Suche behält alles. */
    fun filter(entries: List<FolderEntry>, query: String): List<FolderEntry> {
        val needle = query.trim()
        return if (needle.isEmpty()) entries else entries.filter { it.name.contains(needle, ignoreCase = true) }
    }

    /**
     * Baut die Liste aus den Zählern sichtbarer Ordner ([visible]) und aller Ordner ([all], dort stehen auch die
     * ausgeblendeten). Ein Ordner gilt als ausgeblendet, wenn er in [hidden] steht; Favoriten kommen aus [favorites].
     * Ausgeblendete Ordner sind nie Favorit. Rückgabe: (sichtbare, ausgeblendete), jeweils sortiert.
     */
    fun build(
        visible: Map<String, StatusCounts>,
        all: Map<String, StatusCounts>,
        favorites: Set<String>,
        hidden: Set<String>
    ): Pair<List<FolderEntry>, List<FolderEntry>> {
        val shown = visible.filterKeys { it !in hidden }
            .map { (name, counts) -> FolderEntry(name, counts, favorite = name in favorites) }
        val gone = all.filterKeys { it in hidden }
            .map { (name, counts) -> FolderEntry(name, counts, hidden = true) }
        return sort(shown) to sort(gone)
    }
}
