package com.krlbryn.sophia.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krlbryn.sophia.core.LayoutClass
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.data.db.Bookmark
import com.krlbryn.sophia.data.db.BookmarkFolder
import com.krlbryn.sophia.data.db.HistoryEntry
import com.krlbryn.sophia.data.repo.LibraryRepository
import com.krlbryn.sophia.ui.components.ConfirmDialog
import com.krlbryn.sophia.ui.components.EmptyState
import com.krlbryn.sophia.ui.components.SiteAvatar
import com.krlbryn.sophia.ui.components.TitleUrlDialog
import com.krlbryn.sophia.ui.components.TwoLineText
import com.krlbryn.sophia.ui.navigation.AppActions
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

enum class LibraryTab { BOOKMARKS, HISTORY }

@Composable
fun LibraryScreen(start: LibraryTab, layout: LayoutClass, actions: AppActions, vm: LibraryViewModel = hiltViewModel()) {
    // Phones show bookmarks and history as two tabs of one screen; larger layouts have a nav item for each.
    var tab by rememberSaveable { mutableStateOf(start) }
    val compact = layout == LayoutClass.COMPACT
    val query by vm.query.collectAsStateWithLifecycle()
    var addingBookmark by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(if (tab == LibraryTab.BOOKMARKS) "Bookmarks" else "History") },
                actions = {
                    if (tab == LibraryTab.BOOKMARKS) {
                        IconButton(onClick = { addingBookmark = true }) { Icon(Icons.Outlined.Add, "Add bookmark") }
                    } else {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Clear all history") }
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (compact) {
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    Tab(selected = tab == LibraryTab.BOOKMARKS, onClick = { tab = LibraryTab.BOOKMARKS }, text = { Text("Bookmarks") })
                    Tab(selected = tab == LibraryTab.HISTORY, onClick = { tab = LibraryTab.HISTORY }, text = { Text("History") })
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("library_search"),
                singleLine = true,
                placeholder = { Text(if (tab == LibraryTab.BOOKMARKS) "Search bookmarks" else "Search history") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, "Clear search") } }
                } else null,
                shape = MaterialTheme.shapes.extraLarge,
            )
            val columns = if (compact) GridCells.Fixed(1) else GridCells.Adaptive(320.dp)
            when (tab) {
                LibraryTab.BOOKMARKS -> BookmarksPane(vm, columns, actions)
                LibraryTab.HISTORY -> HistoryPane(vm, columns, actions)
            }
        }
    }

    if (addingBookmark) {
        BookmarkDialog(null, vm, onDismiss = { addingBookmark = false })
    }
    if (confirmClear) {
        ConfirmDialog(
            "Clear all history?",
            "This removes every page from Sophia's history on this device. Bookmarks, cookies and downloads are not affected.",
            "Clear",
            onConfirm = { vm.clearHistory() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun BookmarksPane(vm: LibraryViewModel, columns: GridCells, actions: AppActions) {
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var folderDialog by remember { mutableStateOf<BookmarkFolder?>(null) }
    var newFolder by rememberSaveable { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(filter == FolderFilter.All, onClick = { vm.setFilter(FolderFilter.All) }, label = { Text("All") })
        FilterChip(filter == FolderFilter.Unfiled, onClick = { vm.setFilter(FolderFilter.Unfiled) }, label = { Text("Unfiled") })
        folders.forEach { f ->
            var menu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = filter == FolderFilter.Folder(f.id),
                    onClick = { vm.setFilter(FolderFilter.Folder(f.id)) },
                    label = { Text(f.name) },
                    trailingIcon = {
                        Icon(Icons.Filled.MoreVert, "Folder options", Modifier.clickable { menu = true })
                    },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; folderDialog = f })
                    DropdownMenuItem(text = { Text("Delete folder") }, onClick = { menu = false; vm.deleteFolder(f) })
                }
            }
        }
        TextButton(onClick = { newFolder = true }) {
            Icon(Icons.Outlined.CreateNewFolder, null)
            Spacer(Modifier.width(6.dp))
            Text("New folder")
        }
    }

    if (filter == FolderFilter.All) {
        BuiltInBookmarkCard(onOpen = { actions.openUrl(LibraryRepository.BUILT_IN_URL, false) })
    }
    if (bookmarks.isEmpty()) {
        EmptyState(
            Icons.Outlined.Bookmark,
            "No bookmarks here",
            "Bookmark pages from the browser menu, or add one with the + button.",
            Modifier.padding(16.dp),
        )
    } else {
        LazyVerticalGrid(
            columns = columns,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("bookmark_list"),
        ) {
            items(bookmarks, key = { it.id }) { b ->
                LibraryCard(
                    title = b.title,
                    subtitle = UrlPolicy.hostOf(b.url) + (folders.firstOrNull { it.id == b.folderId }?.let { " · ${it.name}" } ?: ""),
                    url = b.url,
                    onOpen = { actions.openUrl(b.url, false) },
                    menu = { close ->
                        DropdownMenuItem(text = { Text("Open in new tab") }, onClick = { close(); actions.openUrl(b.url, true) })
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { close(); editing = b })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { close(); vm.deleteBookmark(b) })
                    },
                )
            }
        }
    }

    editing?.let { b -> BookmarkDialog(b, vm, onDismiss = { editing = null }) }
    if (newFolder) {
        NameDialog("New folder", "", onDismiss = { newFolder = false }, onSave = { vm.addFolder(it) })
    }
    folderDialog?.let { f ->
        NameDialog("Rename folder", f.name, onDismiss = { folderDialog = null }, onSave = { vm.renameFolder(f, it) })
    }
}

@Composable
private fun HistoryPane(vm: LibraryViewModel, columns: GridCells, actions: AppActions) {
    val history by vm.history.collectAsStateWithLifecycle()
    val enabled by vm.historyEnabled.collectAsStateWithLifecycle()
    if (history.isEmpty()) {
        EmptyState(
            Icons.Outlined.History,
            if (enabled) "No history" else "History is turned off",
            if (enabled) "Pages you visit will be listed here with the time you opened them." else "Turn it on in Settings > Privacy and security.",
            Modifier.padding(16.dp),
        )
        return
    }
    val groups = remember(history) { history.groupBy { dayLabel(it.visitedAt) } }
    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("history_list"),
    ) {
        groups.forEach { (day, entries) ->
            item(key = "h_$day", span = { GridItemSpan(maxLineSpan) }) {
                Text(day, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
            items(entries, key = { it.id }) { e: HistoryEntry ->
                LibraryCard(
                    title = e.title,
                    subtitle = "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.visitedAt))} · ${UrlPolicy.hostOf(e.url)}",
                    url = e.url,
                    onOpen = { actions.openUrl(e.url, false) },
                    menu = { close ->
                        DropdownMenuItem(text = { Text("Open in new tab") }, onClick = { close(); actions.openUrl(e.url, true) })
                        DropdownMenuItem(text = { Text("Remove from history") }, onClick = { close(); vm.deleteHistory(e) })
                    },
                )
            }
        }
    }
}

private fun dayLabel(millis: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (same(day, today)) return "Yesterday"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

@Composable
private fun LibraryCard(
    title: String,
    subtitle: String,
    url: String,
    onOpen: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.clickable(onClick = onOpen).padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SiteAvatar(url, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            TwoLineText(title, subtitle, Modifier.weight(1f))
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Options for $title") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
            }
        }
    }
}

/** Sophia's built-in AMA Discipulus bookmark, always first and never editable. */
@Composable
private fun BuiltInBookmarkCard(onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).testTag("builtin_bookmark"),
    ) {
        Row(Modifier.clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.School, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            TwoLineText(LibraryRepository.BUILT_IN_TITLE, UrlPolicy.hostOf(LibraryRepository.BUILT_IN_URL), Modifier.weight(1f))
            Icon(Icons.Outlined.Lock, "Built-in bookmark", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

@Composable
private fun BookmarkDialog(existing: Bookmark?, vm: LibraryViewModel, onDismiss: () -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    var folderId by remember { mutableStateOf(existing?.folderId) }
    TitleUrlDialog(
        heading = if (existing == null) "Add bookmark" else "Edit bookmark",
        initialTitle = existing?.title.orEmpty(),
        initialUrl = existing?.url.orEmpty(),
        onDismiss = onDismiss,
        onSave = { t, u -> if (existing == null) vm.addBookmark(t, u, folderId) else vm.updateBookmark(existing, t, u, folderId) },
        extraContent = {
            Spacer(Modifier.height(8.dp))
            Text("Folder", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(folderId == null, onClick = { folderId = null }, label = { Text("No folder") })
                folders.forEach { f ->
                    FilterChip(folderId == f.id, onClick = { folderId = f.id }, label = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        },
    )
}

@Composable
private fun NameDialog(heading: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = { OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
