package de.ingvoss.fotosortierer.ui.sort

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.Formats
import de.ingvoss.fotosortierer.core.SwipeAction
import de.ingvoss.fotosortierer.core.SwipeDirection
import de.ingvoss.fotosortierer.core.SwipeMapping
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.data.settings.AppSettings
import de.ingvoss.fotosortierer.ui.appContainer
import de.ingvoss.fotosortierer.ui.components.pluralText
import de.ingvoss.fotosortierer.ui.formatTaken
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import kotlinx.coroutines.launch

/** Sortieransicht: Kartenstapel mit Wischgesten, Schaltflächen, Rückgängig und Abschluss-Zusammenfassung. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortScreen(viewModel: SortViewModel, onClose: () -> Unit, onOpenTrash: () -> Unit) {
    val container = LocalContext.current.appContainer()
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val title by produceState(initialValue = "", viewModel.filter) { value = container.labels.label(viewModel.filter) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (ui.stage == SortStage.SORTING && ui.headerTotal > 0) {
                            Text(
                                stringResource(R.string.sort_position, ui.headerPosition, ui.headerTotal),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onClose() }) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = ui.canUndo) {
                        Icon(AppIcons.Undo, contentDescription = stringResource(R.string.action_undo))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.stage == SortStage.SORTING -> SortingContent(ui, settings, viewModel)
                else -> SummaryContent(
                    ui = ui,
                    onTrash = onOpenTrash,
                    onDeferred = viewModel::startDeferredPhase,
                    onFinish = { viewModel.finish(); onClose() }
                )
            }
        }
    }
}

@Composable
private fun SortingContent(ui: SortUiState, settings: AppSettings, viewModel: SortViewModel) {
    val top = ui.cards.firstOrNull()
    val cardState = remember(top?.uid) { SwipeCardState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val mapping = settings.swipeMapping

    var soundOn by remember(top?.uid) { mutableStateOf(settings.videoSoundDefault) }
    var paused by remember(top?.uid) { mutableStateOf(false) }
    // Als State-Objekt weitergereicht: nur der Fortschrittsbalken liest den Wert, nicht der ganze Kartenstapel.
    val progress = remember(top?.uid) { mutableFloatStateOf(0f) }

    // Die Schaltflächen müssen immer die AKTUELLE Karte bedienen. Würde der Handler beim ersten Mal "eingefroren"
    // (Compose merkt sich Lambdas/Funktionsverweise), träfe jeder weitere Tipp die längst abgeflogene erste Karte
    // und bliebe wirkungslos. Deshalb liest er seine Werte über rememberUpdatedState und ist selbst stabil.
    val currentTop by rememberUpdatedState(top)
    val currentCardState by rememberUpdatedState(cardState)
    val currentMapping by rememberUpdatedState(mapping)
    val currentSettings by rememberUpdatedState(settings)
    val trigger: (SwipeDirection) -> Unit = remember {
        { direction ->
            val action = currentMapping.actionFor(direction)
            val state = currentCardState
            if (action != null && currentTop != null && state.beginExit()) {
                scope.launch {
                    state.flyOut(direction)
                    if (currentSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.decide(action)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val stack: @Composable (Modifier) -> Unit = { modifier ->
            CardStack(
                ui = ui,
                cardState = cardState,
                settings = settings,
                soundOn = soundOn,
                paused = paused,
                progress = progress,
                onToggleSound = { soundOn = !soundOn },
                onTogglePause = { paused = !paused },
                onProgress = { progress.floatValue = it },
                onDecision = viewModel::decide,
                modifier = modifier
            )
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    stack(Modifier.weight(1f).fillMaxWidth())
                    InfoLine(top, settings)
                }
                Column(
                    Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ActionButtons(mapping, enabled = top != null && !cardState.exiting, vertical = true, onTrigger = trigger)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 12.dp)) {
                stack(Modifier.weight(1f).fillMaxWidth())
                InfoLine(top, settings)
                Spacer(Modifier.height(8.dp))
                ActionButtons(mapping, enabled = top != null && !cardState.exiting, vertical = false, onTrigger = trigger)
            }
        }
    }
}

/** Oberste Karte plus bis zu zwei sichtbare und eine unsichtbar vorgeladene Karte dahinter. */
@Composable
private fun CardStack(
    ui: SortUiState,
    cardState: SwipeCardState,
    settings: AppSettings,
    soundOn: Boolean,
    paused: Boolean,
    progress: State<Float>,
    onToggleSound: () -> Unit,
    onTogglePause: () -> Unit,
    onProgress: (Float) -> Unit,
    onDecision: (SwipeAction) -> Unit,
    modifier: Modifier
) {
    Box(modifier) {
        val cards = ui.cards.take(VISIBLE_AND_PRELOADED)
        for (index in cards.indices.reversed()) {
            val card = cards[index]
            key(card.uid) {
                if (index == 0) {
                    TopCard(card, cardState, settings, soundOn, paused, progress, onToggleSound, onTogglePause, onProgress, onDecision)
                } else {
                    BackCard(card, depth = index)
                }
            }
        }
        if (cards.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun TopCard(
    card: MediaStateEntity,
    cardState: SwipeCardState,
    settings: AppSettings,
    soundOn: Boolean,
    paused: Boolean,
    progress: State<Float>,
    onToggleSound: () -> Unit,
    onTogglePause: () -> Unit,
    onProgress: (Float) -> Unit,
    onDecision: (SwipeAction) -> Unit
) {
    SwipeCard(
        state = cardState,
        mapping = settings.swipeMapping,
        haptics = settings.haptics,
        onDecision = onDecision,
        onTap = { if (card.isVideo) onTogglePause() },
        modifier = Modifier.fillMaxSize(),
        overlay = {
            if (card.isVideo) VideoOverlay(soundOn, paused, progress, onToggleSound)
        }
    ) { zoomed ->
        MediaImage(card, Modifier.fillMaxSize(), ContentScale.Fit, highRes = zoomed)
        if (card.isVideo) {
            VideoSurface(card.contentUri(), soundOn, paused, onProgress)
        }
    }
}

@Composable
private fun BackCard(card: MediaStateEntity, depth: Int) {
    val visible = depth <= 2
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = 1f - 0.04f * depth
                scaleX = s
                scaleY = s
                translationY = 14.dp.toPx() * depth
                // Die vierte Karte liegt unsichtbar hinten und wird nur vorgeladen.
                alpha = if (visible) 1f - 0.25f * (depth - 1) else 0f
            }
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        MediaImage(card, Modifier.fillMaxSize(), ContentScale.Fit)
    }
}

@Composable
private fun BoxScope.VideoOverlay(
    soundOn: Boolean,
    paused: Boolean,
    progress: State<Float>,
    onToggleSound: () -> Unit
) {
    if (paused) {
        Icon(
            AppIcons.Play,
            contentDescription = stringResource(R.string.video_paused),
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(36.dp))
                .padding(16.dp)
        )
    }
    IconButton(
        onClick = onToggleSound,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 10.dp, end = 6.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
    ) {
        Icon(
            if (soundOn) AppIcons.VolumeOn else AppIcons.VolumeOff,
            contentDescription = stringResource(if (soundOn) R.string.video_sound_off else R.string.video_sound_on),
            tint = Color.White
        )
    }
    LinearProgressIndicator(
        progress = { progress.value },
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
        color = Color.White,
        trackColor = Color.Black.copy(alpha = 0.4f)
    )
}

@Composable
private fun InfoLine(media: MediaStateEntity?, settings: AppSettings) {
    if (media == null || !settings.showInfoLine) return
    val noDate = stringResource(R.string.label_no_date)
    val parts = buildList {
        add(formatTaken(media.takenAtUtc) ?: noDate)
        add(media.album)
        add(media.displayName)
        if (media.isVideo && media.durationMs > 0) add(Formats.duration(media.durationMs))
        add(Formats.bytes(media.sizeBytes))
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp)
    )
}

/** Schaltflächen räumlich wie die Wischrichtungen angeordnet: links, oben, rechts. */
@Composable
private fun ActionButtons(
    mapping: SwipeMapping,
    enabled: Boolean,
    vertical: Boolean,
    onTrigger: (SwipeDirection) -> Unit
) {
    val directions = if (vertical) {
        listOf(SwipeDirection.OBEN, SwipeDirection.LINKS, SwipeDirection.RECHTS)
    } else {
        listOf(SwipeDirection.LINKS, SwipeDirection.OBEN, SwipeDirection.RECHTS)
    }
    val content: @Composable (SwipeDirection) -> Unit = { direction ->
        val action = mapping.actionFor(direction)
        if (action != null) ActionButton(action, direction, enabled) { onTrigger(direction) }
    }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            directions.forEach { content(it) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            directions.forEach { content(it) }
        }
    }
}

@Composable
private fun ActionButton(action: SwipeAction, direction: SwipeDirection, enabled: Boolean, onClick: () -> Unit) {
    val arrow = when (direction) {
        SwipeDirection.LINKS -> "←"
        SwipeDirection.RECHTS -> "→"
        SwipeDirection.OBEN -> "↑"
        SwipeDirection.KEINE -> ""
    }
    val label = actionLabel(action)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(64.dp).semantics { contentDescription = label },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = actionColor(action), contentColor = Color.White)
        ) {
            Icon(actionIcon(action), contentDescription = null, modifier = Modifier.size(32.dp))
        }
        Text("$arrow $label", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SummaryContent(ui: SortUiState, onTrash: () -> Unit, onDeferred: () -> Unit, onFinish: () -> Unit) {
    val summary = ui.summary
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Column(
            Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (ui.stage == SortStage.PHASE_DONE) R.string.summary_title_phase else R.string.summary_title_done),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            if (summary == null || (summary.behalten + summary.papierkorb + summary.zurueckgestellt) == 0) {
                Text(
                    stringResource(R.string.summary_nothing_decided),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(stringResource(R.string.summary_kept, summary.behalten), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.summary_trash, summary.papierkorb, Formats.bytes(summary.trashBytes)),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(stringResource(R.string.summary_deferred, summary.zurueckgestellt), style = MaterialTheme.typography.bodyLarge)
            }
            if (ui.trashTotal > 0) {
                Button(onClick = onTrash, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.summary_empty_trash, ui.trashTotal))
                }
            }
            if (ui.deferredRemaining > 0) {
                OutlinedButton(onClick = onDeferred, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.summary_review_deferred, ui.deferredRemaining))
                }
            }
            TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_done))
            }
            if (ui.deferredRemaining > 0 && ui.stage == SortStage.ALL_DONE) {
                Text(
                    pluralText(R.plurals.deferred_count, ui.deferredRemaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val VISIBLE_AND_PRELOADED = 4
