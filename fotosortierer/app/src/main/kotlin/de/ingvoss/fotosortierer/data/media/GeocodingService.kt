package de.ingvoss.fotosortierer.data.media

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import de.ingvoss.fotosortierer.core.PlaceNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import kotlin.coroutines.resume

/** Ergebnis einer Ortsnamen-Auflösung. */
sealed interface GeocodeResult {
    data class Found(val name: String) : GeocodeResult

    /** Der Dienst kennt zu dieser Position keinen Namen: dauerhaft Koordinaten anzeigen. */
    data object NoResult : GeocodeResult

    /** Netz oder Dienst vorübergehend nicht erreichbar: später erneut versuchen. */
    data object NetworkError : GeocodeResult
}

/**
 * Reverse-Geocoding, in dieser Reihenfolge: Android-[Geocoder] (falls ein System-Backend vorhanden ist),
 * dann Nominatim (OpenStreetMap; höchstens eine Anfrage pro Sekunde, eigener User-Agent). Aufgerufen wird
 * pro Ortscluster, nicht pro Foto; Ergebnisse speichert der Aufrufer in Room.
 */
class GeocodingService(private val context: Context, private val userAgent: String) {
    private var lastNominatimRequest = 0L

    suspend fun resolve(lat: Double, lon: Double): GeocodeResult {
        platformName(lat, lon)?.let { return GeocodeResult.Found(it) }
        return nominatim(lat, lon)
    }

    // ---- Android-Geocoder -------------------------------------------------------------------

    private suspend fun platformName(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.GERMANY)
        return try {
            val addresses = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<List<Address>> { cont ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(emptyList())
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1) ?: emptyList()
                }
            }
            addresses.firstOrNull()?.let { a ->
                PlaceNames.compose(a.subLocality, a.locality ?: a.subAdminArea, a.adminArea, a.countryName)
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // ---- Nominatim --------------------------------------------------------------------------

    private suspend fun nominatim(lat: Double, lon: Double): GeocodeResult {
        val wait = lastNominatimRequest + MIN_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        return try {
            val result = withContext(Dispatchers.IO) { requestNominatim(lat, lon) }
            result
        } finally {
            lastNominatimRequest = SystemClock.elapsedRealtime()
        }
    }

    private fun requestNominatim(lat: Double, lon: Double): GeocodeResult {
        val url = URI.create(
            String.format(
                Locale.ROOT,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14&addressdetails=1&accept-language=de&lat=%.6f&lon=%.6f",
                lat, lon
            )
        ).toURL()
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseNominatim(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_FORBIDDEN, 429, 503, 502, 504 -> GeocodeResult.NetworkError
                else -> {
                    Log.w(TAG, "Nominatim antwortete mit HTTP $code")
                    GeocodeResult.NoResult
                }
            }
        } catch (e: IOException) {
            GeocodeResult.NetworkError
        } finally {
            connection.disconnect()
        }
    }

    private fun parseNominatim(body: String): GeocodeResult {
        return try {
            val json = JSONObject(body)
            val address = json.optJSONObject("address") ?: return GeocodeResult.NoResult
            fun first(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { k -> address.optString(k, "").takeIf { it.isNotBlank() } }
            val name = PlaceNames.compose(
                district = first("suburb", "city_district", "neighbourhood", "quarter"),
                city = first("city", "town", "village", "municipality", "county"),
                state = first("state", "region"),
                country = first("country")
            )
            if (name != null) GeocodeResult.Found(name) else GeocodeResult.NoResult
        } catch (e: JSONException) {
            GeocodeResult.NoResult
        }
    }

    private companion object {
        const val TAG = "GeocodingService"
        const val MIN_INTERVAL_MS = 1100L
        const val TIMEOUT_MS = 15_000
    }
}
