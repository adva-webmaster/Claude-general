package de.ingvoss.fotosortierer.ui.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.Formats
import de.ingvoss.fotosortierer.ui.components.ConfirmDialog
import de.ingvoss.fotosortierer.ui.components.EmptyState
import de.ingvoss.fotosortierer.ui.components.pluralText
import de.ingvoss.fotosortierer.ui.containerViewModel
import de.ingvoss.fotosortierer.ui.detail.MediaTile
import de.ingvoss.fotosortierer.ui.theme.AppIcons

private enum class TrashDialog { NONE, HINT, CONFIRM }

/**
 * App-interner Papierkorb: Raster, Gesamtgröße, Wiederherstellen und endgültiges Löschen. Gelöscht wird
 * erst, nachdem Android selbst nachgefragt und der Nutzer bestätigt hat (Blöcke zu höchstens 500 Medien).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen() {
    val viewModel = containerViewModel { TrashViewModel(it) }
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val bytes by viewModel.bytes.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val pending by viewModel.pendingRequest.collectAsStateWithLifecycle()
    val hintShown by viewModel.hintShown.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    var dialog by remember { mutableStateOf(TrashDialog.NONE) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(pending) {
        pending?.let {
            viewModel.requestLaunched()
            launcher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    val selecting = selection.isNotEmpty()
    val targetCount = if (selecting) selection.size else counts.total

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selecting) stringResource(R.string.detail_selected, selection.size)
                        else stringResource(R.string.nav_trash)
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_cancel_selection))
                        }
                    }
                },
                actions = {
                    if (counts.total > 0) {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(AppIcons.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (counts.total > 0) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::restore, modifier = Modifier.weight(1f)) {
                            Text(stringResource(if (selecting) R.string.trash_restore_selected else R.string.trash_restore_all))
                        }
                        Button(
                            onClick = { dialog = if (hintShown) TrashDialog.CONFIRM else TrashDialog.HINT },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(stringResource(R.string.trash_delete_forever, targetCount)) }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (counts.total == 0) {
                EmptyState(stringResource(R.string.trash_empty_title), stringResource(R.string.trash_empty_message))
            } else {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.trash_summary, pluralText(R.plurals.media_count, counts.total), Formats.bytes(bytes)),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.trash_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                            Box(Modifier.aspectRatio(1f))
                        } else {
                            MediaTile(
                                media = media,
                                selected = media.uid in selection,
                                onClick = { viewModel.toggle(media.uid) },
                                onLongClick = { viewModel.toggle(media.uid) }
                            )
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        TrashDialog.HINT -> AlertDialog(
            onDismissRequest = { dialog = TrashDialog.NONE },
            title = { Text(stringResource(R.string.trash_hint_title)) },
            text = { Text(stringResource(R.string.trash_hint_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.markHintShown(); dialog = TrashDialog.CONFIRM }) {
                    Text(stringResource(R.string.action_understood))
                }
            },
            dismissButton = { TextButton(onClick = { dialog = TrashDialog.NONE }) { Text(stringResource(R.string.action_cancel)) } }
        )
        TrashDialog.CONFIRM -> ConfirmDialog(
            title = stringResource(R.string.trash_confirm_title),
            message = stringResource(R.string.trash_confirm_message, targetCount),
            confirmLabel = stringResource(R.string.trash_confirm_action),
            onConfirm = { dialog = TrashDialog.NONE; viewModel.deleteForever() },
            onDismiss = { dialog = TrashDialog.NONE }
        )
        TrashDialog.NONE -> Unit
    }

    progress?.let { state ->
        AlertDialog(
            onDismissRequest = { if (state.finished) viewModel.dismissProgress() },
            title = { Text(stringResource(R.string.trash_progress_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        state.cancelled -> Text(stringResource(R.string.trash_progress_cancelled, state.deleted))
                        state.finished -> Text(stringResource(R.string.trash_progress_done, state.deleted))
                        else -> {
                            Text(stringResource(R.string.trash_progress_block, state.index + 1, state.totalBlocks))
                            LinearProgressIndicator(
                                progress = { state.index.toFloat() / state.totalBlocks.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                stringResource(R.string.trash_progress_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }
            },
            confirmButton = {
                if (state.finished) {
                    TextButton(onClick = viewModel::dismissProgress) { Text(stringResource(R.string.action_close)) }
                }
            }
        )
    }
}
