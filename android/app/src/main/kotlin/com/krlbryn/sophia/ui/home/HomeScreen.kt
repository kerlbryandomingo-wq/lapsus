package com.krlbryn.sophia.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krlbryn.sophia.R
import com.krlbryn.sophia.browser.BrowserViewModel
import com.krlbryn.sophia.core.Formatting
import com.krlbryn.sophia.core.LayoutClass
import com.krlbryn.sophia.core.SophiaConstants
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.data.db.PortalShortcut
import com.krlbryn.sophia.data.repo.DownloadState
import com.krlbryn.sophia.ui.components.BrandLockup
import com.krlbryn.sophia.ui.components.EmptyState
import com.krlbryn.sophia.ui.components.NoticeBand
import com.krlbryn.sophia.ui.components.Pill
import com.krlbryn.sophia.ui.components.PillTone
import com.krlbryn.sophia.ui.components.SectionHeader
import com.krlbryn.sophia.ui.components.SiteAvatar
import com.krlbryn.sophia.ui.components.TitleUrlDialog
import com.krlbryn.sophia.ui.components.TwoLineText
import com.krlbryn.sophia.ui.navigation.AppActions
import com.krlbryn.sophia.ui.navigation.Destination
import com.krlbryn.sophia.ui.theme.Brand
import com.krlbryn.sophia.ui.theme.Eyebrow
import com.krlbryn.sophia.ui.theme.LocalSophiaColors
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun HomeScreen(layout: LayoutClass, actions: AppActions, browser: BrowserViewModel, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PortalShortcut?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val compact = layout == LayoutClass.COMPACT

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (compact) {
                ExtendedFloatingActionButton(
                    onClick = { actions.navigate(Destination.ASSISTANT) },
                    icon = { Icon(Icons.Outlined.AutoAwesome, null) },
                    text = { Text("Study Assistant") },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!state.online) {
                NoticeBand("You're offline. Pages and the Study Assistant need a connection; your bookmarks, history and saved notes are still here.", warning = true)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = 16.dp),
            ) {
                if (compact) {
                    BrandLockup(Modifier.padding(bottom = 16.dp))
                }
                Header(state.displayName)
                Spacer(Modifier.height(16.dp))
                PortalHero(
                    compact = compact,
                    lastVisit = state.lastPortalVisit,
                    onOpen = actions.openPortal,
                    onOpenNewTab = { actions.openUrl(SophiaConstants.DEFAULT_PORTAL_URL, true) },
                )
                Spacer(Modifier.height(20.dp))
                QuickActions(actions, browser)
                Spacer(Modifier.height(12.dp))

                if (layout == LayoutClass.EXPANDED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1.6f)) {
                            RecentPages(state, actions)
                            BookmarksSection(state, actions)
                        }
                        Column(Modifier.weight(1f)) {
                            AssistantCard(state.assistantConfigured, actions)
                            ShortcutsSection(state.shortcuts, actions, onAdd = { adding = true }, onEdit = { editing = it }, onDelete = vm::deleteShortcut)
                            DownloadsSection(state, actions)
                        }
                    }
                } else {
                    ShortcutsSection(state.shortcuts, actions, onAdd = { adding = true }, onEdit = { editing = it }, onDelete = vm::deleteShortcut)
                    RecentPages(state, actions)
                    AssistantCard(state.assistantConfigured, actions)
                    BookmarksSection(state, actions)
                    DownloadsSection(state, actions)
                }
                Spacer(Modifier.height(if (compact) 88.dp else 24.dp))
            }
        }
    }

    if (adding) {
        TitleUrlDialog("Add a portal shortcut", "", "", onDismiss = { adding = false }, onSave = { t, u -> vm.addShortcut(t, u) })
    }
    editing?.let { s ->
        TitleUrlDialog("Edit shortcut", s.title, s.url, onDismiss = { editing = null }, onSave = { t, u -> vm.updateShortcut(s, t, u) })
    }
}

@Composable
private fun Header(name: String) {
    val now = Date()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    Text(
        DateFormat.getDateInstance(DateFormat.FULL).format(now).uppercase(),
        style = Eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        Formatting.greeting(hour) + if (name.isNotBlank()) ", $name" else "",
        style = MaterialTheme.typography.headlineLarge,
    )
    Text(SophiaConstants.TAGLINE, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The maroon portal card, the same primary call to action as the Windows home page. */
@Composable
private fun PortalHero(compact: Boolean, lastVisit: Long?, onOpen: () -> Unit, onOpenNewTab: () -> Unit) {
    val gradient = Brush.linearGradient(listOf(Brand.MaroonBright, Brand.MaroonDeep))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(gradient)
            .testTag("portal_hero"),
    ) {
        Row(Modifier.padding(if (compact) 20.dp else 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Student Portal", PillTone.GOLD)
                    Pill("HTTPS", PillTone.ON_DARK, icon = Icons.Outlined.Lock)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "AMA Discipulus",
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
                Text(UrlPolicy.hostOf(SophiaConstants.DEFAULT_PORTAL_URL), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Courses, learning materials, assessments and your account.",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onOpen,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Gold, contentColor = Brand.OnGold),
                        modifier = Modifier.height(48.dp).testTag("open_discipulus"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Discipulus")
                    }
                    if (!compact) {
                        OutlinedButton(
                            onClick = onOpenNewTab,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.height(48.dp),
                        ) {
                            Icon(Icons.Outlined.Tab, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("New tab")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        lastVisit?.let { "Last opened ${Formatting.relativeTime(it, System.currentTimeMillis())}" } ?: "Not opened yet on this device",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!compact) {
                Spacer(Modifier.width(24.dp))
                Box(
                    Modifier.size(132.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_sophia_mark), contentDescription = null, modifier = Modifier.size(88.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(actions: AppActions, browser: BrowserViewModel) {
    SectionHeader("Browser shortcuts")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction(Icons.Outlined.Tab, "New tab") { browser.newTab(); actions.navigate(Destination.BROWSER) }
        QuickAction(Icons.Outlined.Search, "Search") { browser.newTab(); actions.navigate(Destination.BROWSER) }
        QuickAction(Icons.Outlined.Bookmark, "Bookmarks") { actions.navigate(Destination.BOOKMARKS) }
        QuickAction(Icons.Outlined.History, "History") { actions.navigate(Destination.HISTORY) }
        QuickAction(Icons.Outlined.Download, "Downloads") { actions.navigate(Destination.DOWNLOADS) }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.widthIn(min = 104.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutsSection(
    shortcuts: List<PortalShortcut>,
    actions: AppActions,
    onAdd: () -> Unit,
    onEdit: (PortalShortcut) -> Unit,
    onDelete: (PortalShortcut) -> Unit,
) {
    SectionHeader("Portal shortcuts") {
        TextButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
    }
    if (shortcuts.isEmpty()) {
        Text(
            "Add the other school sites you use, such as your class's online resources.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shortcuts.forEach { s ->
            var menu by remember { mutableStateOf(false) }
            Box {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.widthIn(min = 150.dp, max = 260.dp),
                ) {
                    Row(
                        Modifier.clickable { actions.openUrl(s.url, false) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SiteAvatar(s.url, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        TwoLineText(s.title, UrlPolicy.hostOf(s.url), Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Edit, "Edit ${s.title}", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit(s) })
                    DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete(s) })
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RecentPages(state: HomeState, actions: AppActions) {
    SectionHeader("Recently visited", if (state.recent.isNotEmpty()) "View history" else null, { actions.navigate(Destination.HISTORY) })
    if (state.recent.isEmpty()) {
        EmptyState(
            Icons.Outlined.History,
            if (state.historyEnabled) "No recent pages yet" else "History is turned off",
            if (state.historyEnabled) "Pages you open will appear here." else "Turn on history in Settings to see recent pages.",
        )
    } else {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            state.recent.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { actions.openUrl(e.url, false) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SiteAvatar(e.url, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    TwoLineText(e.title, "${UrlPolicy.hostOf(e.url)} · ${Formatting.relativeTime(e.visitedAt, System.currentTimeMillis())}", Modifier.weight(1f))
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun BookmarksSection(state: HomeState, actions: AppActions) {
    SectionHeader("Bookmarks", if (state.bookmarks.isNotEmpty()) "View all" else null, { actions.navigate(Destination.BOOKMARKS) })
    if (state.bookmarks.isEmpty()) {
        EmptyState(Icons.Outlined.Bookmark, "No bookmarks yet", "Tap the bookmark button in the browser to save a page here.")
    } else {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            state.bookmarks.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().clickable { actions.openUrl(b.url, false) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SiteAvatar(b.url, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    TwoLineText(b.title, UrlPolicy.hostOf(b.url), Modifier.weight(1f))
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun AssistantCard(configured: Boolean, actions: AppActions) {
    val extra = LocalSophiaColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.clip(MaterialTheme.shapes.extraSmall).background(extra.aiContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = extra.ai, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("AI", color = extra.ai, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Study Assistant", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Explanations, summaries, study notes, practice questions and flashcards from material you paste or select.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!configured) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No AI provider is set up yet. Add your own key in Settings, or try the offline demo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.warning,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { actions.navigate(Destination.ASSISTANT) }) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open assistant")
            }
        }
    }
}

@Composable
private fun DownloadsSection(state: HomeState, actions: AppActions) {
    if (state.downloads.isEmpty()) return
    SectionHeader("Recent downloads", "View all", { actions.navigate(Destination.DOWNLOADS) })
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        state.downloads.forEach { d ->
            Row(
                Modifier.fillMaxWidth().clickable { actions.navigate(Destination.DOWNLOADS) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (d.state == DownloadState.COMPLETE) Icons.Outlined.Download else Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    d.record.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
