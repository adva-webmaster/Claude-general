package de.ingvoss.fotosortierer.data.media

import android.content.Context
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.Geo
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.MediaKind
import de.ingvoss.fotosortierer.data.db.AppDatabase
import de.ingvoss.fotosortierer.data.db.PlaceEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Formatiert Tage, Monate und Orte für die Anzeige (Deutsch). */
class FilterLabeler(private val context: Context, private val db: AppDatabase) {
    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GERMAN)
    private val dayShortFormat = DateTimeFormatter.ofPattern("d. MMM yyyy", GERMAN)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", GERMAN)

    fun day(localDate: String): String = parseDay(localDate)?.let {
        if (it.year <= NO_DATE_YEAR) context.getString(R.string.label_no_date) else it.format(dayFormat)
    } ?: localDate

    fun dayShort(localDate: String): String = parseDay(localDate)?.let {
        if (it.year <= NO_DATE_YEAR) context.getString(R.string.label_no_date) else it.format(dayShortFormat)
    } ?: localDate

    fun month(localMonth: String): String = try {
        val ym = YearMonth.parse(localMonth)
        if (ym.year <= NO_DATE_YEAR) context.getString(R.string.label_no_date) else ym.format(monthFormat)
    } catch (e: DateTimeParseException) {
        localMonth
    }

    fun year(year: Int): String =
        if (year <= NO_DATE_YEAR) context.getString(R.string.label_no_date) else year.toString()

    /** Ortsname, ersatzweise Koordinaten. */
    fun place(place: PlaceEntity?): String = when {
        place == null -> context.getString(R.string.label_unknown_place)
        place.name != null -> place.name
        else -> Geo.formatCoordinates(place.latitude, place.longitude)
    }

    /** Kurzbeschreibung eines Filters, z. B. "Donnerstag, 12. März 2026 · Kiel". */
    suspend fun label(filter: MediaFilter): String {
        val parts = ArrayList<String>()
        filter.localDate?.let { parts += day(it) }
        filter.localMonth?.let { parts += month(it) }
        filter.clusterId?.let { parts += place(db.placeDao().byId(it)) }
        if (filter.withoutPlace) parts += context.getString(R.string.places_no_place)
        filter.album?.let { parts += it }
        filter.kind?.let {
            parts += context.getString(
                when (it) {
                    MediaKind.FOTO -> R.string.kind_photos
                    MediaKind.VIDEO -> R.string.kind_videos
                    MediaKind.SCREENSHOT -> R.string.kind_screenshots
                }
            )
        }
        return if (parts.isEmpty()) context.getString(R.string.label_all_media) else parts.joinToString(" · ")
    }

    private fun parseDay(localDate: String): LocalDate? = try {
        LocalDate.parse(localDate)
    } catch (e: DateTimeParseException) {
        null
    }

    private companion object {
        val GERMAN: Locale = Locale.GERMANY

        /** Medien ohne Datum landen auf 1970-01-01. */
        const val NO_DATE_YEAR = 1970
    }
}
