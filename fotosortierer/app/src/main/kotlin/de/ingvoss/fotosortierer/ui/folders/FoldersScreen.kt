package de.ingvoss.fotosortierer.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.R
import de.ingvoss.fotosortierer.core.FolderEntry
import de.ingvoss.fotosortierer.core.FolderOrder
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.data.media.FolderOverview
import de.ingvoss.fotosortierer.ui.components.AppTopBar
import de.ingvoss.fotosortierer.ui.components.EmptyState
import de.ingvoss.fotosortierer.ui.components.ProgressSummary
import de.ingvoss.fotosortierer.ui.components.pluralText
import de.ingvoss.fotosortierer.ui.containerViewModel
import de.ingvoss.fotosortierer.ui.theme.AppIcons
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ab so vielen Ordnern erscheint das Suchfeld. */
private const val SEARCH_FROM = 8

class FoldersViewModel(private val container: AppContainer) : ViewModel() {
    /** null solange nicht geladen. */
    val overview: StateFlow<FolderOverview?> =
        container.folders.overview.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setFavorite(name: String, favorite: Boolean) {
        container.appScope.launch { container.folders.setFavorite(name, favorite) }
    }

    /** Ausblenden und Einblenden laufen im App-Scope, damit sie auch beim schnellen Verlassen des Reiters ankommen. */
    fun setHidden(name: String, hidden: Boolean) {
        container.appScope.launch {
            container.folders.setHidden(name, hidden)
            container.onVisibilitySettingsChanged()
        }
    }
}

/**
 * Ordnerliste: Favoriten zuerst, dann die übrigen Ordner; ausgeblendete Ordner stehen eingeklappt am Ende.
 * Langes Antippen öffnet das Menü (Favorit, Ausblenden bzw. Wieder einblenden).
 */
@Composable
fun FoldersScreen(onOpenFolder: (MediaFilter) -> Unit) {
    val viewModel = containerViewModel { FoldersViewModel(it) }
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var query by rememberSaveable { mutableStateOf("") }
    var hiddenExpanded by rememberSaveable { mutableStateOf(false) }

    fun hide(entry: FolderEntry) {
        viewModel.setHidden(entry.name, true)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = resources.getString(R.string.folders_hidden_message, entry.name),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.setHidden(entry.name, false)
                if (entry.favorite) viewModel.setFavorite(entry.name, true)
            }
        }
    }

    fun unhide(entry: FolderEntry) {
        viewModel.setHidden(entry.name, false)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(resources.getString(R.string.folders_unhidden_message, entry.name))
        }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_folders)) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val data = overview
        when {
            data == null -> Box(Modifier.fillMaxSize().padding(padding))
            data.visible.isEmpty() && data.hidden.isEmpty() -> EmptyState(
                stringResource(R.string.folders_empty_title),
                stringResource(R.string.folders_empty_message),
                Modifier.padding(padding)
            )
            else -> FolderList(
                data = data,
                query = query,
                onQueryChange = { query = it },
                hiddenExpanded = hiddenExpanded,
                onToggleHidden = { hiddenExpanded = !hiddenExpanded },
                onOpenFolder = { onOpenFolder(MediaFilter(album = it.name)) },
                onToggleFavorite = { viewModel.setFavorite(it.name, !it.favorite) },
                onHide = ::hide,
                onUnhide = ::unhide,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun FolderList(
    data: FolderOverview,
    query: String,
    onQueryChange: (String) -> Unit,
    hiddenExpanded: Boolean,
    onToggleHidden: () -> Unit,
    onOpenFolder: (FolderEntry) -> Unit,
    onToggleFavorite: (FolderEntry) -> Unit,
    onHide: (FolderEntry) -> Unit,
    onUnhide: (FolderEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = FolderOrder.filter(data.visible, query)
    val gone = FolderOrder.filter(data.hidden, query)
    val favorites = shown.filter { it.favorite }
    val others = shown.filterNot { it.favorite }
    // Welches Menü gerade offen ist: "v:Name" (sichtbarer Ordner) oder "h:Name" (ausgeblendeter).
    var menuFor by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (data.visible.size + data.hidden.size >= SEARCH_FROM || query.isNotEmpty()) {
            item("search") { SearchField(query, onQueryChange) }
        }
        item("hint") {
            Text(
                stringResource(R.string.folders_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (shown.isEmpty() && gone.isEmpty()) {
            item("nomatch") { Text(stringResource(R.string.folders_no_match), style = MaterialTheme.typography.bodyMedium) }
        }
        if (favorites.isNotEmpty()) {
            item("title-favorites") { SectionTitle(stringResource(R.string.folders_section_favorites)) }
            items(favorites, key = { "v:" + it.name }) { entry ->
                FolderCard(
                    entry = entry,
                    menuOpen = menuFor == "v:" + entry.name,
                    onOpenMenu = { menuFor = "v:" + entry.name },
                    onCloseMenu = { menuFor = null },
                    onClick = { onOpenFolder(entry) },
                    onToggleFavorite = { onToggleFavorite(entry) },
                    onToggleHidden = { onHide(entry) }
                )
            }
        }
        if (others.isNotEmpty()) {
            if (favorites.isNotEmpty()) item("title-others") { SectionTitle(stringResource(R.string.folders_section_others)) }
            items(others, key = { "v:" + it.name }) { entry ->
                FolderCard(
                    entry = entry,
                    menuOpen = menuFor == "v:" + entry.name,
                    onOpenMenu = { menuFor = "v:" + entry.name },
                    onCloseMenu = { menuFor = null },
                    onClick = { onOpenFolder(entry) },
                    onToggleFavorite = { onToggleFavorite(entry) },
                    onToggleHidden = { onHide(entry) }
                )
            }
        }
        if (gone.isNotEmpty()) {
            val expanded = hiddenExpanded || query.isNotEmpty()
            item("title-hidden") { HiddenHeader(gone.size, expanded, onToggleHidden) }
            if (expanded) {
                items(gone, key = { "h:" + it.name }) { entry ->
                    FolderCard(
                        entry = entry,
                        menuOpen = menuFor == "h:" + entry.name,
                        onOpenMenu = { menuFor = "h:" + entry.name },
                        onCloseMenu = { menuFor = null },
                        // Ausgeblendete Medien stehen in keiner Abfrage; ein Tipp öffnet daher das Menü.
                        onClick = { menuFor = "h:" + entry.name },
                        onToggleFavorite = {},
                        onToggleHidden = { onUnhide(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.folders_search)) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.folders_search_clear))
                }
            }
        }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun HiddenHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(AppIcons.EyeOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.folders_section_hidden, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            AppIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (expanded) 90f else 0f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    entry: FolderEntry,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val counts = entry.counts
    val favoriteLabel = stringResource(R.string.folders_is_favorite)
    val hiddenLabel = stringResource(R.string.folders_is_hidden)
    val progressLabel = stringResource(R.string.progress_x_of_y, counts.processed, counts.total)
    val description = listOfNotNull(
        entry.name,
        favoriteLabel.takeIf { entry.favorite },
        if (entry.hidden) hiddenLabel else progressLabel
    ).joinToString(", ")
    Box {
        Card(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onLongClickLabel = stringResource(R.string.folders_menu_label),
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenMenu()
                    },
                    onClick = onClick
                )
                .semantics(mergeDescendants = true) { contentDescription = description },
            colors = if (entry.hidden) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            } else {
                CardDefaults.cardColors()
            }
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (entry.favorite) {
                            Icon(AppIcons.StarFilled, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        if (entry.hidden) {
                            Icon(AppIcons.EyeOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (entry.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        pluralText(R.plurals.media_count, counts.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!entry.hidden) ProgressSummary(counts)
                }
                if (!entry.hidden) Icon(AppIcons.Chevron, contentDescription = null)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onCloseMenu) {
            if (entry.hidden) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folders_unhide)) },
                    leadingIcon = { Icon(AppIcons.Eye, contentDescription = null) },
                    onClick = { onCloseMenu(); onToggleHidden() }
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (entry.favorite) R.string.folders_favorite_remove else R.string.folders_favorite_add))
                    },
                    leadingIcon = { Icon(if (entry.favorite) AppIcons.Star else AppIcons.StarFilled, contentDescription = null) },
                    onClick = { onCloseMenu(); onToggleFavorite() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folders_hide)) },
                    leadingIcon = { Icon(AppIcons.EyeOff, contentDescription = null) },
                    onClick = { onCloseMenu(); onToggleHidden() }
                )
            }
        }
    }
}
