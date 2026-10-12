package de.ingvoss.fotosortierer.ui.sort

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.SwipeAction
import de.ingvoss.fotosortierer.core.SwipeDirection
import de.ingvoss.fotosortierer.core.SwipeGeometry
import de.ingvoss.fotosortierer.core.SwipeMapping
import de.ingvoss.fotosortierer.core.ZoomPan
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import de.ingvoss.fotosortierer.ui.theme.LocalFotoColors
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Zustand der obersten Sortierkarte: Ziehposition (Wischen), Zoom und Verschiebung (Zwei-Finger-Zoom,
 * Doppeltipp). Wird von der Karte selbst und von den Schaltflächen gesteuert, damit Wischen und Tasten
 * dieselbe Animation und Entscheidung auslösen.
 */
@Stable
class SwipeCardState {
    var dragX by mutableFloatStateOf(0f)
    var dragY by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)

    /** Wahr, sobald die Karte hinausfliegt: weitere Eingaben werden ignoriert. */
    var exiting by mutableStateOf(false)
        private set

    var widthPx by mutableFloatStateOf(0f)
    var heightPx by mutableFloatStateOf(0f)

    /** Liest nur [scale], damit reines Verschieben die Karteninhalte nicht neu zusammensetzt. */
    val isZoomed: Boolean get() = scale > ZoomPan.ZOOMED_THRESHOLD

    /** Zoomt um den Punkt [centroid] (relativ zur Kartenmitte) und verschiebt um [pan]. */
    fun transform(zoom: Float, centroid: Offset, pan: Offset) {
        val result = ZoomPan(scale, panX, panY)
            .transform(zoom, centroid.x, centroid.y, pan.x, pan.y, widthPx, heightPx)
        scale = result.scale
        panX = result.panX
        panY = result.panY
    }

    suspend fun resetZoom() {
        val s0 = scale
        val x0 = panX
        val y0 = panY
        animate(0f, 1f, animationSpec = tween(180)) { t, _ ->
            scale = s0 + (1f - s0) * t
            panX = x0 * (1f - t)
            panY = y0 * (1f - t)
        }
        scale = 1f
        panX = 0f
        panY = 0f
    }

    suspend fun zoomTo(target: Float, tap: Offset) {
        val s0 = scale
        val x0 = panX
        val y0 = panY
        val end = ZoomPan().zoomedTo(target, tap.x, tap.y, widthPx, heightPx)
        animate(0f, 1f, animationSpec = tween(200)) { t, _ ->
            scale = s0 + (end.scale - s0) * t
            panX = x0 + (end.panX - x0) * t
            panY = y0 + (end.panY - y0) * t
        }
    }

    suspend fun snapBack() {
        val x0 = dragX
        val y0 = dragY
        animate(0f, 1f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { t, _ ->
            dragX = x0 * (1f - t)
            dragY = y0 * (1f - t)
        }
        dragX = 0f
        dragY = 0f
    }

    /**
     * Reserviert den Abgang der Karte. Nur der erste Aufruf liefert true; dadurch löst eine Karte nie zwei
     * Entscheidungen aus (z. B. Wischen und Schaltfläche fast gleichzeitig).
     */
    fun beginExit(): Boolean {
        if (exiting) return false
        exiting = true
        return true
    }

    /** Lässt die Karte in [direction] aus dem Bild fliegen. Vorher [beginExit] aufrufen. */
    suspend fun flyOut(direction: SwipeDirection) {
        val x0 = dragX
        val y0 = dragY
        val targetX = when (direction) {
            SwipeDirection.LINKS -> -widthPx * EXIT_FACTOR
            SwipeDirection.RECHTS -> widthPx * EXIT_FACTOR
            else -> x0
        }
        val targetY = if (direction == SwipeDirection.OBEN) -heightPx * EXIT_FACTOR else y0
        animate(0f, 1f, animationSpec = tween(EXIT_MILLIS)) { t, _ ->
            dragX = x0 + (targetX - x0) * t
            dragY = y0 + (targetY - y0) * t
        }
    }

    companion object {
        const val DOUBLE_TAP_SCALE = ZoomPan.DOUBLE_TAP_SCALE
        private const val EXIT_FACTOR = 1.4f
        private const val EXIT_MILLIS = 220
    }
}

private enum class GestureMode { UNDECIDED, SWIPE, ZOOM, PAN }

/** Farbe, Symbol und Text einer Aktion (für Rückmeldung und Schaltflächen). */
@Composable
fun actionColor(action: SwipeAction): Color = LocalFotoColors.current.forStatus(action.status)

fun actionIcon(action: SwipeAction): ImageVector = when (action) {
    SwipeAction.BEHALTEN -> AppIcons.Check
    SwipeAction.PAPIERKORB -> AppIcons.Trash
    SwipeAction.ZURUECKSTELLEN -> AppIcons.Clock
}

@Composable
fun actionLabel(action: SwipeAction): String = stringResource(
    when (action) {
        SwipeAction.BEHALTEN -> R.string.action_keep
        SwipeAction.PAPIERKORB -> R.string.action_trash
        SwipeAction.ZURUECKSTELLEN -> R.string.action_defer
    }
)

/**
 * Oberste Sortierkarte: Wischen nach links/rechts/oben (dominante Achse, Schwelle oder schnelle Geste),
 * Zwei-Finger-Zoom, Doppeltipp-Zoom, Antippen. Unterhalb der Schwelle springt die Karte zurück. Während des
 * Ziehens zeigen Farbfläche, Symbol und Text die Aktion; über [SwipeCardState.flyOut] können auch die
 * Schaltflächen dieselbe Animation auslösen. Für Screenreader gibt es zusätzlich benutzerdefinierte Aktionen.
 */
@Composable
fun SwipeCard(
    state: SwipeCardState,
    mapping: SwipeMapping,
    haptics: Boolean,
    onDecision: (SwipeAction) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.(zoomed: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentMapping by rememberUpdatedState(mapping)
    val currentOnDecision by rememberUpdatedState(onDecision)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentHaptics by rememberUpdatedState(haptics)

    fun commit(dir: SwipeDirection) {
        val action = currentMapping.actionFor(dir) ?: return
        if (!state.beginExit()) return
        scope.launch {
            state.flyOut(dir)
            if (currentHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnDecision(action)
        }
    }

    val keepLabel = stringResource(R.string.action_keep)
    val trashLabel = stringResource(R.string.action_trash)
    val deferLabel = stringResource(R.string.action_defer)
    fun labelOf(a: SwipeAction) = when (a) {
        SwipeAction.BEHALTEN -> keepLabel
        SwipeAction.PAPIERKORB -> trashLabel
        SwipeAction.ZURUECKSTELLEN -> deferLabel
    }

    Box(
        modifier
            .onSizeChanged {
                state.widthPx = it.width.toFloat()
                state.heightPx = it.height.toFloat()
            }
            .semantics {
                customActions = SwipeAction.entries.map { a ->
                    CustomAccessibilityAction(labelOf(a)) {
                        commit(currentMapping.directionOf(a))
                        true
                    }
                }
            }
            .graphicsLayer {
                translationX = state.dragX
                translationY = state.dragY
                rotationZ = if (state.widthPx > 0f) (state.dragX / state.widthPx * MAX_TILT_DEGREES).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES) else 0f
            }
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (!state.exiting) {
                            scope.launch {
                                if (state.isZoomed) state.resetZoom() else state.zoomTo(SwipeCardState.DOUBLE_TAP_SCALE, tap)
                            }
                        }
                    },
                    onTap = { if (!state.exiting) currentOnTap() }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (state.exiting) return@awaitEachGesture
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var mode = if (state.isZoomed) GestureMode.PAN else GestureMode.UNDECIDED
                    var total = Offset.Zero
                    do {
                        val event: PointerEvent = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            // Begann die Geste als Wischen, kehrt die Karte zuerst an ihren Platz zurück.
                            if (mode == GestureMode.SWIPE) scope.launch { state.snapBack() }
                            mode = GestureMode.ZOOM
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val relative = if (centroid.isSpecified) centroid - Offset(size.width / 2f, size.height / 2f) else Offset.Zero
                            state.transform(event.calculateZoom(), relative, event.calculatePan())
                            event.changes.forEach { if (it.pressed) it.consume() }
                        } else if (pressed == 1) {
                            val change = event.changes.first { it.pressed }
                            val delta = change.positionChange()
                            if (mode == GestureMode.ZOOM || mode == GestureMode.PAN) {
                                if (state.isZoomed) {
                                    state.transform(1f, Offset.Zero, delta)
                                    change.consume()
                                }
                            } else {
                                total += delta
                                if (mode == GestureMode.UNDECIDED && hypot(total.x, total.y) > viewConfiguration.touchSlop) {
                                    mode = GestureMode.SWIPE
                                }
                                if (mode == GestureMode.SWIPE) {
                                    state.dragX += delta.x
                                    // Nach unten wird nicht gewischt: nur leichter Widerstand.
                                    state.dragY = if (delta.y > 0f && state.dragY > 0f) state.dragY + delta.y * 0.3f else state.dragY + delta.y
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    when (mode) {
                        GestureMode.SWIPE -> {
                            val velocity = tracker.calculateVelocity()
                            val resolved = SwipeGeometry.resolve(
                                state.dragX, state.dragY, velocity.x, velocity.y,
                                (minOf(state.widthPx, state.heightPx) * THRESHOLD_FRACTION).coerceAtLeast(1f)
                            )
                            if (resolved == SwipeDirection.KEINE || currentMapping.actionFor(resolved) == null) {
                                scope.launch { state.snapBack() }
                            } else {
                                commit(resolved)
                            }
                        }
                        GestureMode.ZOOM -> if (state.scale < SNAP_TO_FIT_SCALE) scope.launch { state.resetZoom() }
                        else -> Unit
                    }
                }
            }
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = state.scale
                scaleY = state.scale
                translationX = state.panX
                translationY = state.panY
            }
        ) { content(state.isZoomed) }

        SwipeFeedback(state, currentMapping, currentHaptics)
        overlay()
    }
}

/** Rückmeldung während des Ziehens: Farbfläche, Symbol und Text der Aktion. Liest den Zustand in eigener Scope. */
@Composable
private fun BoxScope.SwipeFeedback(state: SwipeCardState, mapping: SwipeMapping, haptics: Boolean) {
    val haptic = LocalHapticFeedback.current
    val threshold = (minOf(state.widthPx, state.heightPx) * THRESHOLD_FRACTION).coerceAtLeast(1f)
    val direction = SwipeGeometry.directionOf(state.dragX, state.dragY, deadZonePx = threshold * 0.08f)
    val progress = SwipeGeometry.progress(state.dragX, state.dragY, threshold)
    val armedAction = mapping.actionFor(direction)
    val reached = armedAction != null && progress >= 1f

    // Kurzes Haptik-Signal, sobald die Auslöseschwelle erreicht ist.
    LaunchedEffect(reached) {
        if (reached && haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    if (armedAction != null && progress > 0f) {
        val color = actionColor(armedAction)
        Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.55f * progress)))
        Column(
            Modifier.align(Alignment.Center).graphicsLayer { alpha = 0.35f + 0.65f * progress },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(44.dp)).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(actionIcon(armedAction), contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Text(
                actionLabel(armedAction),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/** Schwelle als Anteil der kürzeren Kartenseite. */
const val THRESHOLD_FRACTION = 0.3f
private const val SNAP_TO_FIT_SCALE = 1.05f
private const val MAX_TILT_DEGREES = 14f
