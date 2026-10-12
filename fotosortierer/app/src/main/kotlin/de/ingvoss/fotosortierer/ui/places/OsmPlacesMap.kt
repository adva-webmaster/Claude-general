package de.ingvoss.fotosortierer.ui.places

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.ingvoss.fotosortierer.ui.appContainer
import de.ingvoss.fotosortierer.ui.theme.LocalFotoColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import java.io.File

/** Einmalige osmdroid-Konfiguration: eigener User-Agent (Pflicht für OSM-Kacheln) und App-eigener Kachel-Cache. */
private fun configureOsmdroid(context: Context, userAgent: String) {
    val configuration = Configuration.getInstance()
    configuration.userAgentValue = userAgent
    val base = File(context.cacheDir, "osmdroid")
    val tiles = File(base, "tiles")
    tiles.mkdirs()
    configuration.osmdroidBasePath = base
    configuration.osmdroidTileCache = tiles
    configuration.tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES
    configuration.tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_BYTES
}

private const val TILE_CACHE_MAX_BYTES = 120L * 1024 * 1024
private const val TILE_CACHE_TRIM_BYTES = 100L * 1024 * 1024
private const val MAX_ZOOM = 19.0
private const val MIN_ZOOM = 3.0

/**
 * OpenStreetMap-Karte (osmdroid) mit geclusterten, nach Bearbeitungsstand gefärbten Ortsmarkern.
 * Ein Tipp auf einen einzelnen Marker meldet den Ort, ein Tipp auf einen Cluster zoomt hinein; auf der
 * höchsten Zoomstufe werden die Orte des Clusters gemeldet.
 */
@Composable
fun OsmPlacesMap(
    places: List<PlaceItem>,
    selectedIds: Set<Long>,
    onPlacesTapped: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userAgent = context.appContainer().userAgent
    val colors = LocalFotoColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnTapped by rememberUpdatedState(onPlacesTapped)

    val mapView = remember {
        configureOsmdroid(context, userAgent)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            minZoomLevel = MIN_ZOOM
            maxZoomLevel = MAX_ZOOM
            isTilesScaledToDpi = true
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(51.0, 10.0))
        }
    }
    val overlay = remember { PlaceClusterOverlay(context) }
    var fitted by remember { mutableStateOf(false) }

    DisposableEffect(mapView, overlay) {
        // Ein Tipp auf einen Marker (auch auf einen Cluster mit Zahl) öffnet die Medien dieser Orte. Zoomen geht
        // weiterhin mit Zwei-Finger-Geste, Doppeltipp und den Zoom-Tasten der Karte.
        overlay.onMarkerTapped = { ids -> currentOnTapped(ids) }
        mapView.overlays.add(overlay)
        mapView.overlays.add(CopyrightOverlay(context))
        onDispose {
            mapView.overlays.clear()
            mapView.onDetach()
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Beim ersten Eintreffen von Orten auf deren Ausdehnung zoomen.
    LaunchedEffect(places.isNotEmpty()) {
        if (places.isNotEmpty() && !fitted) {
            fitted = true
            mapView.post {
                if (places.size == 1) {
                    mapView.controller.setZoom(13.0)
                    mapView.controller.setCenter(GeoPoint(places[0].latitude, places[0].longitude))
                } else {
                    val box = BoundingBox.fromGeoPoints(places.map { GeoPoint(it.latitude, it.longitude) })
                    mapView.zoomToBoundingBox(box, false, ZOOM_PADDING_PX * 2)
                }
            }
        }
    }

    AndroidView(
        factory = { mapView },
        update = {
            overlay.places = places
            overlay.selectedIds = selectedIds
            overlay.colors = colors
            it.invalidate()
        },
        modifier = modifier
    )
}

private const val ZOOM_PADDING_PX = 80
