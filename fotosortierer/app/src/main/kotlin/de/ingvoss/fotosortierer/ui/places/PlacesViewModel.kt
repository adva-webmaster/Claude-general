package de.ingvoss.fotosortierer.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.data.db.LocationProgressRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Ein Ort (räumlicher Cluster) mit Zählern, Zeitraum und Anzeigenamen. */
data class PlaceItem(
    val clusterId: Long,
    val name: String,
    val named: Boolean,
    val latitude: Double,
    val longitude: Double,
    val counts: StatusCounts,
    val firstAt: Long,
    val lastAt: Long
)

enum class PlaceSort { NAME, COUNT, PROGRESS }

data class PlacesUi(
    val places: List<PlaceItem> = emptyList(),
    val noPlace: StatusCounts = StatusCounts.EMPTY,
    val locations: LocationProgressRow = LocationProgressRow(0, 0, 0),
    val locationAccess: Boolean = false,
    val loaded: Boolean = false
)

/** Orte, optional eingeschränkt auf einen Zeitraum ([scope]: Jahr, Monat oder Tag): Zähler zählen dann nur dessen Medien. */
class PlacesViewModel(container: AppContainer, scope: MediaFilter) : ViewModel() {
    private val dao = container.database.mediaDao()
    private val labels = container.labels

    val state: StateFlow<PlacesUi> = combine(
        container.media.places(scope),
        container.database.placeDao().observeAll(),
        container.media.counts(scope.copy(withoutPlace = true)),
        dao.observeLocationProgress(),
        container.locationAccess
    ) { rows, entities, noPlace, progress, locationAccess ->
        val byId = entities.associateBy { it.clusterId }
        val items = rows.mapNotNull { row ->
            val entity = byId[row.clusterId] ?: return@mapNotNull null
            PlaceItem(
                clusterId = row.clusterId,
                name = labels.place(entity),
                named = entity.name != null,
                latitude = entity.latitude,
                longitude = entity.longitude,
                counts = row.toCounts(),
                firstAt = row.firstAt,
                lastAt = row.lastAt
            )
        }
        PlacesUi(items, noPlace, progress, locationAccess, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlacesUi())
}
