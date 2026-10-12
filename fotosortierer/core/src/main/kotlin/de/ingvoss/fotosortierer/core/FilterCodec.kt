package de.ingvoss.fotosortierer.core

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * Wandelt einen [MediaFilter] in ein URL-sicheres Token (nur A–Z a–z 0–9 _ -) um, damit er als
 * Navigationsargument und in gespeicherten Sitzungen verwendet werden kann. Fehlerhafte Tokens
 * ergeben den unbeschränkten Filter [MediaFilter.ALL].
 */
object FilterCodec {
    fun encode(filter: MediaFilter): String {
        val parts = mutableListOf<String>()
        filter.localDate?.let { parts += "d=$it" }
        filter.localMonth?.let { parts += "m=$it" }
        filter.localYear?.let { parts += "y=$it" }
        filter.clusterId?.let { parts += "c=$it" }
        filter.clusterIds?.let { ids -> parts += "cs=" + ids.sorted().joinToString(",") }
        if (filter.withoutPlace) parts += "np=1"
        filter.album?.let { parts += "a=" + URLEncoder.encode(it, "UTF-8") }
        filter.kind?.let { parts += "k=${it.code}" }
        filter.statuses?.let { set ->
            parts += "s=" + set.map { it.code }.sorted().joinToString(",")
        }
        // Leeres Token wäre als Route-Argument unbrauchbar → Platzhalter "-".
        if (parts.isEmpty()) return EMPTY
        return Base64.getUrlEncoder().withoutPadding().encodeToString(parts.joinToString(";").toByteArray(Charsets.UTF_8))
    }

    fun decode(token: String?): MediaFilter {
        if (token.isNullOrEmpty() || token == EMPTY) return MediaFilter.ALL
        return try {
            val text = String(Base64.getUrlDecoder().decode(token), Charsets.UTF_8)
            var filter = MediaFilter.ALL
            for (part in text.split(';')) {
                val idx = part.indexOf('=')
                if (idx <= 0) continue
                val key = part.substring(0, idx)
                val value = part.substring(idx + 1)
                filter = when (key) {
                    "d" -> filter.copy(localDate = value)
                    "m" -> filter.copy(localMonth = value)
                    "y" -> filter.copy(localYear = value)
                    "c" -> filter.copy(clusterId = value.toLongOrNull())
                    "cs" -> filter.copy(clusterIds = value.split(',').mapNotNull { it.toLongOrNull() }.toSet())
                    "np" -> filter.copy(withoutPlace = value == "1")
                    "a" -> filter.copy(album = URLDecoder.decode(value, "UTF-8"))
                    "k" -> filter.copy(kind = value.toIntOrNull()?.let { code -> MediaKind.entries.firstOrNull { it.code == code } })
                    "s" -> filter.copy(
                        statuses = value.split(',').mapNotNull { v ->
                            v.toIntOrNull()?.let { code -> MediaStatus.entries.firstOrNull { it.code == code } }
                        }.toSet()
                    )
                    else -> filter
                }
            }
            filter
        } catch (e: IllegalArgumentException) {
            MediaFilter.ALL
        }
    }

    const val EMPTY = "-"
}
