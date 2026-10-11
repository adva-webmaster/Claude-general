package de.ingvoss.fotosortierer.ui.places

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.MotionEvent
import androidx.compose.ui.graphics.toArgb
import de.ingvoss.fotosortierer.core.Formats
import de.ingvoss.fotosortierer.core.ProgressState
import de.ingvoss.fotosortierer.core.ScreenClusterer
import de.ingvoss.fotosortierer.core.ScreenItem
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.ui.theme.FotoColors
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/**
 * Zeichnet alle Orte als Marker und fasst auf dem Bildschirm nahe Marker zu Clustern zusammen (osmdroid
 * bringt kein Clustering mit). Farbe UND Form kodieren den Zustand: gestrichelter Ring = unbearbeitet,
 * Teil-Ring = teilweise, gefüllt = vollständig. Die Zahl in der Mitte ist die Medienanzahl; ein rosa Punkt
 * markiert zurückgestellte Medien.
 */
class PlaceClusterOverlay(context: Context) : Overlay() {
    var places: List<PlaceItem> = emptyList()
    var selectedIds: Set<Long> = emptySet()
    var colors: FotoColors = FotoColors.Light
    var onMarkerTapped: (List<Long>) -> Unit = {}

    private val density = context.resources.displayMetrics.density
    private val radius = 20f * density
    private val ringWidth = 5f * density

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xF2FFFFFF.toInt() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringWidth; strokeCap = Paint.Cap.ROUND }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFF000000.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
        isFakeBoldText = true
    }
    private val oval = RectF()

    private class Drawn(val ids: List<Long>, val x: Float, val y: Float)

    private var drawn: List<Drawn> = emptyList()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val margin = radius * 2
        val point = Point()
        val items = ArrayList<ScreenItem>(places.size)
        val byId = HashMap<Long, PlaceItem>(places.size * 2)
        for (place in places) {
            projection.toPixels(GeoPoint(place.latitude, place.longitude), point)
            if (point.x < -margin || point.y < -margin || point.x > mapView.width + margin || point.y > mapView.height + margin) continue
            items.add(ScreenItem(place.clusterId, point.x.toFloat(), point.y.toFloat()))
            byId[place.clusterId] = place
        }
        val clusters = ScreenClusterer.cluster(items, radius * 2f)
        val result = ArrayList<Drawn>(clusters.size)
        for (cluster in clusters) {
            val members = cluster.ids.mapNotNull { byId[it] }
            val counts = members.fold(StatusCounts.EMPTY) { acc, p -> acc + p.counts }
            drawMarker(canvas, cluster.x, cluster.y, counts, cluster.ids.any { it in selectedIds })
            result.add(Drawn(cluster.ids, cluster.x, cluster.y))
        }
        drawn = result
    }

    private fun drawMarker(canvas: Canvas, x: Float, y: Float, counts: StatusCounts, selected: Boolean) {
        val color = colors.forState(counts.state).toArgb()
        canvas.drawCircle(x, y, radius, backgroundPaint)
        val ringRadius = radius - ringWidth / 2f
        ringPaint.pathEffect = null
        ringPaint.color = color
        textPaint.color = 0xFF202124.toInt()
        when (counts.state) {
            ProgressState.UNBEARBEITET, ProgressState.LEER -> {
                ringPaint.pathEffect = DashPathEffect(floatArrayOf(ringWidth * 1.6f, ringWidth * 1.1f), 0f)
                canvas.drawCircle(x, y, ringRadius, ringPaint)
            }
            ProgressState.TEILWEISE -> {
                ringPaint.color = 0xFFC9CCD2.toInt()
                canvas.drawCircle(x, y, ringRadius, ringPaint)
                ringPaint.color = color
                oval.set(x - ringRadius, y - ringRadius, x + ringRadius, y + ringRadius)
                canvas.drawArc(oval, -90f, 360f * counts.fraction.coerceIn(0.05f, 0.95f), false, ringPaint)
            }
            ProgressState.VOLLSTAENDIG -> {
                fillPaint.color = color
                canvas.drawCircle(x, y, radius, fillPaint)
                textPaint.color = 0xFFFFFFFF.toInt()
            }
        }
        val label = Formats.compactCount(counts.total)
        canvas.drawText(label, x, y - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        if (counts.hasDeferred) {
            fillPaint.color = colors.deferred.toArgb()
            canvas.drawCircle(x + radius * 0.75f, y - radius * 0.75f, radius * 0.28f, fillPaint)
        }
        if (selected) canvas.drawCircle(x, y, radius + 3f * density, selectPaint)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val hit = drawn.minByOrNull { hypot(it.x - e.x, it.y - e.y) } ?: return false
        if (hypot(hit.x - e.x, hit.y - e.y) > radius + 8f * density) return false
        onMarkerTapped(hit.ids)
        return true
    }
}
