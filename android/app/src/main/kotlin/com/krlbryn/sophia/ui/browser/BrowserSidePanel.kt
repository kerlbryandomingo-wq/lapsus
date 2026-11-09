package com.krlbryn.sophia.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.ui.components.SiteAvatar
import com.krlbryn.sophia.ui.components.TwoLineText
import com.krlbryn.sophia.ui.library.LibraryViewModel

/** Tablet side panel: study tools for the current page, plus bookmarks and history beside the page. */
@Composable
fun BrowserSidePanel(
    modifier: Modifier,
    onClose: () -> Unit,
    onAskSelection: () -> Unit,
    onSummarizePage: () -> Unit,
    onOpen: (String) -> Unit,
    library: LibraryViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val bookmarks by library.bookmarks.collectAsStateWithLifecycle()
    val history by library.history.collectAsStateWithLifecycle()
    Surface(modifier.testTag("side_panel"), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close panel") }
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Study") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bookmarks") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("History") })
            }
            when (tab) {
                0 -> Column(Modifier.padding(16.dp)) {
                    Text(
                        "Send text from this page to the Study Assistant. You review exactly what will be sent before anything leaves your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onAskSelection, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Explain selected text")
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onSummarizePage, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Summarize, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Study this page")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Use the assistant to learn. Follow your course's rules for graded and monitored assessments.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                1 -> LazyColumn {
                    items(bookmarks, key = { it.id }) { b ->
                        PanelRow(b.title, b.url) { onOpen(b.url) }
                    }
                }
                else -> LazyColumn {
                    items(history, key = { it.id }) { h ->
                        PanelRow(h.title, h.url) { onOpen(h.url) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelRow(title: String, url: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteAvatar(url, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        TwoLineText(title, UrlPolicy.hostOf(url), Modifier.weight(1f))
    }
}
