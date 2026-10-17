package de.ingvoss.fotosortierer.core

/**
 * Ein Verzeichnis im Verzeichnisbaum. [path] endet mit "/" (z. B. "Pictures/Urlaub/") und dient direkt als
 * `MediaFilter.pathPrefix`; [counts] umfassen das Verzeichnis samt allen Unterverzeichnissen.
 */
data class DirectoryNode(
    val name: String,
    val path: String,
    val counts: StatusCounts,
    val children: List<DirectoryNode>
)

/** Eine sichtbare Zeile des (teilweise aufgeklappten) Baums. */
data class DirectoryRow(val node: DirectoryNode, val depth: Int, val expanded: Boolean)

object DirectoryTree {
    private class Builder(val name: String, val path: String) {
        var own: StatusCounts = StatusCounts.EMPTY
        val children = LinkedHashMap<String, Builder>()

        fun build(): DirectoryNode {
            val built = children.values.map { it.build() }.sortedWith(NODE_ORDER)
            val total = built.fold(own) { sum, child -> sum + child.counts }
            return DirectoryNode(name, path, total, built)
        }
    }

    private val NODE_ORDER = compareBy<DirectoryNode, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.name }

    /**
     * Baut den Baum aus [directories]: Pfad ("Pictures/Urlaub/") → Zähler der Medien, die direkt in diesem Verzeichnis
     * liegen. Zwischenverzeichnisse ohne eigene Medien entstehen von selbst. Leere Pfade werden ignoriert.
     */
    fun build(directories: Map<String, StatusCounts>): List<DirectoryNode> {
        val roots = LinkedHashMap<String, Builder>()
        for ((rawPath, counts) in directories) {
            val segments = rawPath.trim('/').split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) continue
            var level = roots
            var path = ""
            var node: Builder? = null
            for (segment in segments) {
                path += "$segment/"
                val current = level.getOrPut(segment) { Builder(segment, path) }
                node = current
                level = current.children
            }
            node?.let { it.own = it.own + counts }
        }
        return roots.values.map { it.build() }.sortedWith(NODE_ORDER)
    }

    /** Die sichtbaren Zeilen: Wurzeln immer, Kinder nur unterhalb aufgeklappter Verzeichnisse ([expanded] = Pfade). */
    fun flatten(roots: List<DirectoryNode>, expanded: Set<String>): List<DirectoryRow> {
        val rows = ArrayList<DirectoryRow>()
        fun visit(node: DirectoryNode, depth: Int) {
            val open = node.children.isNotEmpty() && node.path in expanded
            rows += DirectoryRow(node, depth, open)
            if (open) node.children.forEach { visit(it, depth + 1) }
        }
        roots.forEach { visit(it, 0) }
        return rows
    }

    /** Alle Verzeichnisse (auf jeder Ebene), deren Pfad [query] enthält, flach und nach Pfad sortiert. */
    fun search(roots: List<DirectoryNode>, query: String): List<DirectoryNode> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val hits = ArrayList<DirectoryNode>()
        fun visit(node: DirectoryNode) {
            if (node.path.contains(needle, ignoreCase = true)) hits += node
            node.children.forEach { visit(it) }
        }
        roots.forEach { visit(it) }
        return hits.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path })
    }
}
