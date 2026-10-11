package de.ingvoss.fotosortierer.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.Formats
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.MediaStatus
import de.ingvoss.fotosortierer.data.db.MediaStateEntity
import de.ingvoss.fotosortierer.ui.appContainer
import de.ingvoss.fotosortierer.ui.components.EmptyState
import de.ingvoss.fotosortierer.ui.components.ProgressSummary
import de.ingvoss.fotosortierer.ui.containerViewModel
import de.ingvoss.fotosortierer.ui.formatTaken
import de.ingvoss.fotosortierer.ui.sort.MediaImage
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import de.ingvoss.fotosortierer.ui.theme.LocalFotoColors

@Composable
fun statusLabel(status: MediaStatus): String = stringResource(
    when (status) {
        MediaStatus.UNBEARBEITET -> R.string.status_unprocessed
        MediaStatus.BEHALTEN -> R.string.status_keep
        MediaStatus.PAPIERKORB -> R.string.status_trash
        MediaStatus.ZURUECKGESTELLT -> R.string.status_deferred
        MediaStatus.GELOESCHT -> R.string.status_deleted
    }
)

private fun statusIcon(status: MediaStatus) = when (status) {
    MediaStatus.BEHALTEN -> AppIcons.Check
    MediaStatus.PAPIERKORB -> AppIcons.Trash
    MediaStatus.ZURUECKGESTELLT -> AppIcons.Clock
    else -> null
}

/**
 * Detailseite eines Tages, Monats oder Ortes: Fortschritt, "Sortieren starten" und das Medienraster mit
 * Statusrahmen. Hier lassen sich getroffene Entscheidungen korrigieren (Antippen oder Mehrfachauswahl per
 * langem Druck).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilterDetailScreen(
    filter: MediaFilter,
    onBack: () -> Unit,
    onStartSorting: (MediaFilter) -> Unit
) {
    val viewModel = containerViewModel(key = filter.toString()) { FilterDetailViewModel(it, filter) }
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val labels = LocalContext.current.appContainer().labels
    val title by produceState(initialValue = "", filter) { value = labels.label(filter) }
    val snackbarHost = remember { SnackbarHostState() }
    var inspected by remember { mutableStateOf<MediaStateEntity?>(null) }
    val selecting = selection.isNotEmpty()

    val undoLabel = stringResource(R.string.action_undo)
    val changedTemplate = stringResource(R.string.detail_changed)
    LaunchedEffect(viewModel) {
        viewModel.changeEvents.collect { event ->
            val result = snackbarHost.showSnackbar(
                message = changedTemplate.format(event.count),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLastChange()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selecting) stringResource(R.string.detail_selected, selection.size) else title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (selecting) viewModel::clearSelection else onBack) {
                        Icon(
                            if (selecting) AppIcons.Close else AppIcons.Back,
                            contentDescription = stringResource(if (selecting) R.string.action_cancel_selection else R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(AppIcons.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (selecting) SelectionBar(onApply = viewModel::applyToSelection)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!selecting) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressSummary(counts)
                    if (counts.open > 0) {
                        Button(onClick = { onStartSorting(filter) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_start_sorting))
                        }
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { viewModel.setStatusFilter(null) },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                listOf(MediaStatus.UNBEARBEITET, MediaStatus.BEHALTEN, MediaStatus.PAPIERKORB, MediaStatus.ZURUECKGESTELLT).forEach { s ->
                    FilterChip(
                        selected = statusFilter == s,
                        onClick = { viewModel.setStatusFilter(if (statusFilter == s) null else s) },
                        label = { Text(statusLabel(s)) }
                    )
                }
            }
            if (items.itemCount == 0) {
                EmptyState(stringResource(R.string.detail_empty_title), stringResource(R.string.detail_empty_message))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(count = items.itemCount, key = items.itemKey { it.uid }) { index ->
                        val media = items[index]
                        if (media == null) {
                            Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant))
                        } else {
                            MediaTile(
                                media = media,
                                selected = media.uid in selection,
                                onClick = { if (selecting) viewModel.toggleSelection(media.uid) else inspected = media },
                                onLongClick = { viewModel.toggleSelection(media.uid) }
                            )
                        }
                    }
                }
            }
        }
    }

    inspected?.let { media ->
        CorrectionDialog(
            media = media,
            onDismiss = { inspected = null },
            onSetStatus = { status ->
                viewModel.applyTo(media.uid, status)
                inspected = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaTile(media: MediaStateEntity, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = LocalFotoColors.current
    val status = media.mediaStatus
    val borderColor = if (status == MediaStatus.UNBEARBEITET) Color.Transparent else colors.forStatus(status)
    val label = statusLabel(status)
    val description = media.displayName + ", " + label
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(3.dp, borderColor, RoundedCornerShape(8.dp))
            .combinedClickable(
                onClickLabel = stringResource(R.string.detail_open_item),
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.detail_select_item),
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        MediaImage(media, Modifier.fillMaxSize(), ContentScale.Crop, contentDescription = null)
        statusIcon(status)?.let { icon ->
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp).size(22.dp).background(borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
        if (media.isVideo) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AppIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                if (media.durationMs > 0) {
                    Text(Formats.duration(media.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
            Box(
                Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun SelectionBar(onApply: (MediaStatus) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).navigationBarsPadding().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusButton(MediaStatus.BEHALTEN, Modifier.weight(1f), onApply)
            StatusButton(MediaStatus.PAPIERKORB, Modifier.weight(1f), onApply)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusButton(MediaStatus.ZURUECKGESTELLT, Modifier.weight(1f), onApply)
            StatusButton(MediaStatus.UNBEARBEITET, Modifier.weight(1f), onApply)
        }
    }
}

@Composable
private fun StatusButton(status: MediaStatus, modifier: Modifier, onClick: (MediaStatus) -> Unit, enabled: Boolean = true) {
    FilledTonalButton(onClick = { onClick(status) }, modifier = modifier, enabled = enabled) {
        statusIcon(status)?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text(statusLabel(status), modifier = Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Großansicht eines Mediums mit den Statusaktionen. */
@Composable
private fun CorrectionDialog(media: MediaStateEntity, onDismiss: () -> Unit, onSetStatus: (MediaStatus) -> Unit) {
    val context = LocalContext.current
    val noDate = stringResource(R.string.label_no_date)
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaImage(media, Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp)))
                Text(
                    listOfNotNull(
                        formatTaken(media.takenAtUtc) ?: noDate,
                        media.album,
                        media.displayName,
                        Formats.bytes(media.sizeBytes)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.detail_current_status, statusLabel(media.mediaStatus)),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusButton(MediaStatus.BEHALTEN, Modifier.weight(1f), onSetStatus, media.mediaStatus != MediaStatus.BEHALTEN)
                    StatusButton(MediaStatus.PAPIERKORB, Modifier.weight(1f), onSetStatus, media.mediaStatus != MediaStatus.PAPIERKORB)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusButton(MediaStatus.ZURUECKGESTELLT, Modifier.weight(1f), onSetStatus, media.mediaStatus != MediaStatus.ZURUECKGESTELLT)
                    StatusButton(MediaStatus.UNBEARBEITET, Modifier.weight(1f), onSetStatus, media.mediaStatus != MediaStatus.UNBEARBEITET)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(media.contentUri(), if (media.isVideo) "video/*" else "image/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // Keine passende App installiert: nichts zu tun.
                        }
                    }) {
                        Icon(AppIcons.Open, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.detail_open_external), modifier = Modifier.padding(start = 6.dp))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}
