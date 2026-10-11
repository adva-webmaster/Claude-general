package de.ingvoss.fotosortierer.ui.components

import androidx.annotation.PluralsRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.ProgressState
import de.ingvoss.fotosortierer.core.StatusCounts
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import de.ingvoss.fotosortierer.ui.theme.LocalFotoColors

/** Plural-Text (z. B. "1 Medium" / "5 Medien"). */
@Composable
fun pluralText(@PluralsRes id: Int, count: Int): String =
    LocalResources.current.getQuantityString(id, count, count)

@Composable
fun stateDescription(state: ProgressState): String = stringResource(
    when (state) {
        ProgressState.LEER -> R.string.progress_state_empty
        ProgressState.UNBEARBEITET -> R.string.state_open
        ProgressState.TEILWEISE -> R.string.state_partial
        ProgressState.VOLLSTAENDIG -> R.string.state_complete
    }
)

/**
 * Zustandsmarke für Tage, Monate, Jahre und Orte. Farbe UND Form tragen die Information:
 * gestrichelter Ring = unbearbeitet, Teil-Ring = teilweise (Länge = Fortschritt), gefüllter Kreis mit Haken =
 * vollständig. Ein kleiner Punkt oben rechts zeigt zurückgestellte Medien an.
 */
@Composable
fun StateMark(
    state: ProgressState,
    fraction: Float,
    modifier: Modifier = Modifier,
    markSize: Dp = 28.dp,
    hasDeferred: Boolean = false
) {
    val colors = LocalFotoColors.current
    val color = colors.forState(state)
    val track = MaterialTheme.colorScheme.outlineVariant
    val deferredColor = colors.deferred
    val description = stateDescription(state) + if (hasDeferred) ", " + stringResource(R.string.badge_deferred) else ""
    Canvas(modifier.size(markSize).semantics { contentDescription = description }) {
        val stroke = size.minDimension * 0.13f
        val diameter = size.minDimension - stroke
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val arcSize = Size(diameter, diameter)
        when (state) {
            ProgressState.LEER -> drawCircle(track, radius = diameter / 2f, style = Stroke(stroke))
            ProgressState.UNBEARBEITET -> drawCircle(
                color,
                radius = diameter / 2f,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 1.6f, stroke * 1.2f)))
            )
            ProgressState.TEILWEISE -> {
                drawCircle(track, radius = diameter / 2f, style = Stroke(stroke))
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0.04f, 0.96f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            ProgressState.VOLLSTAENDIG -> {
                drawCircle(color, radius = diameter / 2f + stroke / 2f)
                val check = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.68f)
                    lineTo(size.width * 0.72f, size.height * 0.34f)
                }
                drawPath(check, Color.White, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (hasDeferred) {
            val r = size.minDimension * 0.19f
            drawCircle(Color.White, radius = r + stroke * 0.3f, center = Offset(size.width - r, r))
            drawCircle(deferredColor, radius = r, center = Offset(size.width - r, r))
        }
    }
}

/** Fortschrittszeile: Zustandsmarke, "x von y bearbeitet" und Balken. */
@Composable
fun ProgressSummary(counts: StatusCounts, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StateMark(counts.state, counts.fraction, hasDeferred = counts.hasDeferred)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.progress_x_of_y, counts.processed, counts.total),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { counts.fraction }, modifier = Modifier.fillMaxWidth())
            if (counts.hasDeferred) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = pluralText(R.plurals.deferred_count, counts.zurueckgestellt),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalFotoColors.current.deferred
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = { actions() }
    )
}

/** Symbol-Schaltfläche mit Beschriftung für Screenreader. */
@Composable
fun LabeledIconButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.action_cancel)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}
