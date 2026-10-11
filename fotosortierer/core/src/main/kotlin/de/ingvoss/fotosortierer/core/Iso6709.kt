package de.ingvoss.fotosortierer.core

/** Geografischer Punkt in Grad (WGS84). */
data class LatLon(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
}

/**
 * Parser für ISO-6709-Standortstrings, wie sie Video-Metadaten liefern
 * (MediaMetadataRetriever.METADATA_KEY_LOCATION), z. B. "+54.3233+010.1228/" oder
 * "+54.3233+010.1228+012.000/".
 */
object Iso6709 {
    private val pattern = Regex("""^([+-]\d{1,3}(?:\.\d+)?)([+-]\d{1,3}(?:\.\d+)?)(?:[+-]\d+(?:\.\d+)?)?(?:CRS\w+)?/?$""")

    fun parse(value: String?): LatLon? {
        if (value.isNullOrBlank()) return null
        val match = pattern.matchEntire(value.trim()) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return LatLon(lat, lon).takeIf { it.isValid }
    }
}
