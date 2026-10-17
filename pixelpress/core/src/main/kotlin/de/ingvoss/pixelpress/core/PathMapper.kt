package de.ingvoss.pixelpress.core

/** Wandelt die Auswahl aus dem System-Verzeichnisdialog in einen echten Dateipfad um. */
object PathMapper {
    private val volumeName = Regex("[A-Za-z0-9-]+")

    /**
     * [documentId] ist die Dokument-ID des gewählten Verzeichnisbaums (`DocumentsContract.getTreeDocumentId`):
     * "primary:Movies/AVI" → "<primärer Speicher>/Movies/AVI"; "1234-ABCD:Videos" → "/storage/1234-ABCD/Videos";
     * "home:Berichte" → "<primärer Speicher>/Documents/Berichte"; "raw:/pfad" → "/pfad".
     * Unbekannte Formen und Pfade mit ".." ergeben null.
     */
    fun treeDocumentIdToPath(documentId: String, primaryRoot: String): String? {
        val id = documentId.trim()
        if (id.isEmpty()) return null
        if (id.startsWith("raw:")) {
            val raw = id.removePrefix("raw:")
            return if (raw.startsWith("/")) clean(raw) else null
        }
        val colon = id.indexOf(':')
        if (colon <= 0) return null
        val volume = id.substring(0, colon)
        val rest = id.substring(colon + 1)
        val base = when {
            volume == "primary" -> primaryRoot
            volume == "home" -> "$primaryRoot/Documents"
            volumeName.matches(volume) -> "/storage/$volume"
            else -> return null
        }
        return clean(if (rest.isEmpty()) base else "$base/$rest")
    }

    private fun clean(path: String): String? {
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        return "/" + parts.joinToString("/")
    }
}
