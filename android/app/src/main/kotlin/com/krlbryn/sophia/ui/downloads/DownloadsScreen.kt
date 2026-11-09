package com.krlbryn.sophia.ui.downloads

import android.webkit.WebSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krlbryn.sophia.browser.BrowserViewModel
import com.krlbryn.sophia.core.FileNames
import com.krlbryn.sophia.core.Formatting
import com.krlbryn.sophia.core.LayoutClass
import com.krlbryn.sophia.data.repo.DownloadItem
import com.krlbryn.sophia.data.repo.DownloadState
import com.krlbryn.sophia.data.settings.DownloadLocation
import com.krlbryn.sophia.ui.components.EmptyState
import com.krlbryn.sophia.ui.navigation.AppActions
import com.krlbryn.sophia.ui.theme.LocalSophiaColors

@Composable
fun DownloadsScreen(layout: LayoutClass, actions: AppActions, browser: BrowserViewModel, vm: DownloadsViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Downloads") }) },
    ) { inner ->
        val list = items
        Column(Modifier.padding(inner).fillMaxSize()) {
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> EmptyState(
                    Icons.Outlined.Download,
                    "No downloads yet",
                    "Files you download from the portal, like lecture slides and handouts, will appear here.",
                    Modifier.padding(16.dp),
                )
                else -> LazyVerticalGrid(
                    columns = if (layout == LayoutClass.COMPACT) GridCells.Fixed(1) else GridCells.Adaptive(360.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("download_list"),
                ) {
                    items(list, key = { it.record.id }) { item ->
                        DownloadCard(
                            item = item,
                            onOpen = { if (!vm.open(item)) actions.message("No app on this device can open ${item.record.fileName}.") },
                            onCancel = { vm.cancel(item) },
                            onRetry = { vm.retry(item, WebSettings.getDefaultUserAgent(context), actions.message) },
                            onRemove = { deleteFile -> vm.remove(item, deleteFile) },
                        )
                    }
                }
            }
        }
    }
}

private fun iconFor(fileName: String, mime: String?): ImageVector = when (FileNames.typeLabel(fileName, mime)) {
    "PDF document" -> Icons.Outlined.PictureAsPdf
    "Word document", "Text file" -> Icons.Outlined.Article
    "Spreadsheet" -> Icons.Outlined.TableChart
    "Presentation" -> Icons.Outlined.Slideshow
    "Image" -> Icons.Outlined.Image
    else -> Icons.Outlined.InsertDriveFile
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: (deleteFile: Boolean) -> Unit,
) {
    val r = item.record
    var menu by remember { mutableStateOf(false) }
    val extra = LocalSophiaColors.current
    val location = DownloadLocation.entries.firstOrNull { it.name == r.location }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = item.state == DownloadState.COMPLETE, onClick = onOpen)
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (item.state == DownloadState.FAILED) Icons.Outlined.ErrorOutline else iconFor(r.fileName, r.mimeType),
                        null,
                        tint = if (item.state == DownloadState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val size = when {
                        item.state == DownloadState.COMPLETE -> Formatting.bytes(item.totalBytes)
                        item.totalBytes > 0 -> "${Formatting.bytes(item.downloadedBytes)} of ${Formatting.bytes(item.totalBytes)}"
                        item.downloadedBytes > 0 -> Formatting.bytes(item.downloadedBytes)
                        else -> ""
                    }
                    val status = when (item.state) {
                        DownloadState.QUEUED -> "Queued"
                        DownloadState.RUNNING -> "Downloading"
                        DownloadState.WAITING -> item.reason ?: "Waiting"
                        DownloadState.COMPLETE -> location?.label ?: "Done"
                        DownloadState.FAILED -> item.reason ?: "Failed"
                        DownloadState.CANCELLED -> "Cancelled"
                        DownloadState.MISSING -> item.reason ?: "Removed"
                    }
                    Text(
                        listOf(FileNames.typeLabel(r.fileName, r.mimeType), size, status).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (item.state) {
                            DownloadState.FAILED -> MaterialTheme.colorScheme.error
                            DownloadState.WAITING -> extra.warning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options for ${r.fileName}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (item.state == DownloadState.COMPLETE) {
                            DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() })
                            DropdownMenuItem(text = { Text("Delete file") }, onClick = { menu = false; onRemove(true) })
                        }
                        if (item.active) DropdownMenuItem(text = { Text("Cancel download") }, onClick = { menu = false; onCancel() })
                        if (item.state == DownloadState.FAILED || item.state == DownloadState.CANCELLED) {
                            DropdownMenuItem(text = { Text("Try again") }, onClick = { menu = false; onRetry() })
                        }
                        if (!item.active) DropdownMenuItem(text = { Text("Remove from list") }, onClick = { menu = false; onRemove(false) })
                    }
                }
            }
            if (item.active) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val p = item.progress
                    if (p != null) {
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else if (item.state == DownloadState.FAILED) {
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}
