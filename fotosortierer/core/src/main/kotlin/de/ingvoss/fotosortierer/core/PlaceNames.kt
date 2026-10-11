package de.ingvoss.fotosortierer.core

/** Baut aus Adressbestandteilen eine kurze Ortsbezeichnung. */
object PlaceNames {
    /**
     * "Stadtteil, Stadt", sonst nur Stadt bzw. Stadtteil; fehlen beide, dienen Bundesland/Region und Land
     * als Ersatz. Liefert null, wenn alle Bestandteile leer sind.
     */
    fun compose(district: String?, city: String?, state: String?, country: String?): String? {
        val d = district?.trim()?.takeIf { it.isNotEmpty() }
        val c = city?.trim()?.takeIf { it.isNotEmpty() }
        val primary = listOfNotNull(d, c).distinct()
        if (primary.isNotEmpty()) return primary.joinToString(", ")
        val fallback = listOfNotNull(
            state?.trim()?.takeIf { it.isNotEmpty() },
            country?.trim()?.takeIf { it.isNotEmpty() }
        ).distinct()
        return fallback.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** Rasterschlüssel (≈ 110 m) für den Geocoding-Cache. */
    fun cacheKey(lat: Double, lon: Double): String =
        String.format(java.util.Locale.ROOT, "%.3f,%.3f", lat, lon)
}
