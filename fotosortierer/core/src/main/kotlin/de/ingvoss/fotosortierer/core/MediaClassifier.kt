package de.ingvoss.fotosortierer.core

/** Ordnet MediaStore-Einträge einem [MediaKind] zu. */
object MediaClassifier {
    const val MEDIA_TYPE_IMAGE = 1
    const val MEDIA_TYPE_VIDEO = 3

    /**
     * @param mediaType Wert der Spalte `MEDIA_TYPE` (1 = Bild, 3 = Video).
     * @return null, wenn weder Bild noch Video.
     *
     * Screenshots werden an einem Ordner- oder Dateinamen mit "screenshot" erkannt (Groß-/Kleinschreibung
     * egal) – das deckt Android ("Pictures/Screenshots/"), Samsung ("Screenshots", "Screenshot_…") und die
     * meisten anderen Hersteller ab. Eine sicherere Kennzeichnung gibt MediaStore nicht her.
     */
    fun kindOf(mediaType: Int, relativePath: String?, displayName: String?): MediaKind? = when (mediaType) {
        MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
        MEDIA_TYPE_IMAGE -> if (looksLikeScreenshot(relativePath, displayName)) MediaKind.SCREENSHOT else MediaKind.FOTO
        else -> null
    }

    private fun looksLikeScreenshot(relativePath: String?, displayName: String?): Boolean =
        relativePath?.contains("screenshot", ignoreCase = true) == true ||
            displayName?.startsWith("screenshot", ignoreCase = true) == true

    /** Albumname: Bucket-Name, sonst letzter Ordner des relativen Pfads, sonst [UNKNOWN_ALBUM]. */
    fun albumOf(bucketName: String?, relativePath: String?): String {
        if (!bucketName.isNullOrBlank()) return bucketName
        val folder = relativePath?.trim('/')?.substringAfterLast('/')
        return if (folder.isNullOrBlank()) UNKNOWN_ALBUM else folder
    }

    /**
     * Vereinheitlicht ein Verzeichnis (MediaStore `RELATIVE_PATH`): ohne führenden Schrägstrich, mit abschließendem,
     * z. B. "Pictures/Urlaub/". Null oder leer → "" (unbekannt).
     */
    fun normalizePath(relativePath: String?): String {
        val trimmed = relativePath?.trim()?.trim('/') ?: return ""
        return if (trimmed.isEmpty()) "" else "$trimmed/"
    }

    const val UNKNOWN_ALBUM = "Unbekannt"
}
