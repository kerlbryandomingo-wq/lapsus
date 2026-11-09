package com.krlbryn.sophia.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.krlbryn.sophia.browser.ControlsPanel
import com.krlbryn.sophia.browser.TabUi
import com.krlbryn.sophia.core.SwipeClassifier
import com.krlbryn.sophia.core.SwipeDirection
import com.krlbryn.sophia.core.UrlPolicy

/** A row in the left panel's bookmark list. [url] is null while the bookmark isn't set up. */
data class PanelBookmark(val title: String, val url: String?, val key: String)

/** Colours of the side panels: translucent grey with white content, as in the reference design. */
object PanelColors {
    val Panel = Color(0xF2808080)
    val Field = Color.White.copy(alpha = 0.16f)
    val Tile = Color.White.copy(alpha = 0.14f)
    val Separator = Color.White.copy(alpha = 0.28f)
    val Content = Color.White
    val Muted = Color.White.copy(alpha = 0.55f)
    /** Dims the page only while a panel is open; nothing is drawn over the page when both are closed. */
    val Scrim = Color.Black.copy(alpha = 0.30f)
}

/**
 * Sophia's side panels, layered over the page.
 * - Swiping from the left edge slides in the address bar and bookmarks ([ControlsPanel.BOOKMARKS]).
 * - Swiping from the right edge slides in Back, Forward, Reload and Settings ([ControlsPanel.NAVIGATION]).
 * - Swiping a panel back toward its edge, tapping the page beside it, or pressing Back closes it.
 *
 * When both panels are closed nothing at all is composed here, so the page keeps its own colours.
 */
@Composable
fun BoxScope.GesturePanels(
    panel: ControlsPanel,
    wide: Boolean,
    currentUrl: String,
    bookmarks: List<PanelBookmark>,
    tabs: List<TabUi>,
    activeTabId: Long?,
    focusAddress: Boolean,
    onAddressFocused: () -> Unit = {},
    canGoBack: Boolean,
    canGoForward: Boolean,
    canReload: Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onBookmark: (PanelBookmark) -> Unit,
    onManageBookmarks: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onSettings: () -> Unit,
) {
    AnimatedVisibility(panel != ControlsPanel.NONE, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(PanelColors.Scrim)
                .swipeToSophiaControls(onSwipe)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .testTag("controls_scrim"),
        )
    }

    AnimatedVisibility(
        visible = panel == ControlsPanel.BOOKMARKS,
        enter = slideInHorizontally { -it },
        exit = slideOutHorizontally { -it },
        modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
    ) {
        AddressPanel(
            width = if (wide) 320.dp else 280.dp,
            currentUrl = currentUrl,
            bookmarks = bookmarks,
            tabs = tabs,
            activeTabId = activeTabId,
            focusAddress = focusAddress,
            onAddressFocused = onAddressFocused,
            onSwipe = onSwipe,
            onNavigate = onNavigate,
            onBookmark = onBookmark,
            onManageBookmarks = onManageBookmarks,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
        )
    }

    AnimatedVisibility(
        visible = panel == ControlsPanel.NAVIGATION,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    ) {
        ControlsPanelColumn(
            width = if (wide) 116.dp else 100.dp,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            canReload = canReload,
            onSwipe = onSwipe,
            onBack = onBack,
            onForward = onForward,
            onReload = onReload,
            onSettings = onSettings,
        )
    }
}

@Composable
private fun AddressPanel(
    width: Dp,
    currentUrl: String,
    bookmarks: List<PanelBookmark>,
    tabs: List<TabUi>,
    activeTabId: Long?,
    focusAddress: Boolean,
    onAddressFocused: () -> Unit = {},
    onSwipe: (SwipeDirection) -> Unit,
    onNavigate: (String) -> Unit,
    onBookmark: (PanelBookmark) -> Unit,
    onManageBookmarks: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
) {
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(PanelColors.Panel)
            .swipeToSophiaControls(onSwipe)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .semantics { paneTitle = "Address and bookmarks" }
            .testTag("bookmark_controls"),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        PanelHeading("Address Bar", Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        AddressField(currentUrl, focusAddress, onAddressFocused, onNavigate, Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(28.dp))
        PanelHeading("Bookmarks", Modifier.padding(horizontal = 16.dp))
        Text(
            "Manage",
            style = MaterialTheme.typography.bodyMedium,
            color = PanelColors.Muted,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onManageBookmarks)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        HorizontalDivider(color = PanelColors.Separator, modifier = Modifier.padding(top = 4.dp))
        bookmarks.forEach { b ->
            PanelRow(
                icon = Icons.Outlined.Language,
                title = b.title,
                subtitle = if (b.url == null) "Not set up yet" else null,
                tag = "panel_bookmark_${b.key}",
                onClick = { onBookmark(b) },
            )
        }

        if (tabs.size > 1) {
            Spacer(Modifier.height(24.dp))
            PanelHeading("Tabs", Modifier.padding(horizontal = 16.dp))
            HorizontalDivider(color = PanelColors.Separator, modifier = Modifier.padding(top = 8.dp))
            tabs.forEach { t ->
                val title = t.title.ifBlank { if (t.isStartPage) "New tab" else UrlPolicy.hostOf(t.url) }
                PanelRow(
                    icon = Icons.Outlined.Language,
                    title = title,
                    subtitle = if (t.id == activeTabId) "Current tab" else null,
                    tag = "panel_tab_${t.id}",
                    onClick = { onSelectTab(t.id) },
                    trailing = {
                        IconButton(onClick = { onCloseTab(t.id) }) {
                            Icon(Icons.Filled.Close, "Close $title", tint = PanelColors.Content)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PanelHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PanelColors.Content, modifier = modifier)
}

@Composable
private fun AddressField(currentUrl: String, focusAddress: Boolean, onFocused: () -> Unit, onNavigate: (String) -> Unit, modifier: Modifier) {
    var value by remember(currentUrl) { mutableStateOf(TextFieldValue(currentUrl)) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusAddress) {
        if (focusAddress) {
            runCatching { focusRequester.requestFocus() }
            onFocused()
        }
    }
    val submit = { if (value.text.isNotBlank()) onNavigate(value.text) }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PanelColors.Field)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = PanelColors.Content),
            cursorBrush = SolidColor(PanelColors.Content),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) Text("Search or type a web address", color = PanelColors.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused && !focused) value = value.copy(selection = TextRange(0, value.text.length))
                    focused = state.isFocused
                }
                .semantics { contentDescription = "Address" }
                .testTag("panel_address"),
        )
        Box(
            Modifier
                .padding(start = 6.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(role = Role.Button, onClick = submit)
                .semantics { contentDescription = "Go" }
                .testTag("panel_go"),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF6E6E6E)) }
    }
}

@Composable
private fun PanelRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tag: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).testTag(tag)) {
        Row(Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PanelColors.Content, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = PanelColors.Content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PanelColors.Muted, maxLines = 1)
            }
            trailing?.invoke()
        }
        // Separator starts under the text, as in the reference list.
        HorizontalDivider(color = PanelColors.Separator, modifier = Modifier.padding(start = 58.dp))
    }
}

@Composable
private fun ControlsPanelColumn(
    width: Dp,
    canGoBack: Boolean,
    canGoForward: Boolean,
    canReload: Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(PanelColors.Panel)
            .swipeToSophiaControls(onSwipe)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
            .semantics { paneTitle = "Browser controls" }
            .testTag("nav_controls"),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ControlTile(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", canGoBack, onBack)
        ControlTile(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Forward", canGoForward, onForward)
        ControlTile(Icons.Filled.Refresh, "Reload", canReload, onReload)
        ControlTile(Icons.Outlined.Settings, "Settings", true, onSettings)
    }
}

@Composable
private fun ControlTile(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 76.dp, height = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PanelColors.Tile)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = PanelColors.Content, modifier = Modifier.size(32.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = PanelColors.Content, maxLines = 1)
    }
}

/**
 * Recognises swipes on Sophia's own surfaces (start page, error page, scrim and panels) the same way
 * SwipeWatcher does on web pages. With [requireEdge] the swipe must start near the edge it moves away
 * from, which is how panels open; closing swipes on an open panel can start anywhere.
 */
@Composable
fun Modifier.swipeToSophiaControls(onSwipe: (SwipeDirection) -> Unit, requireEdge: Boolean = false): Modifier {
    val density = LocalDensity.current.density
    return this.pointerInput(onSwipe, requireEdge) {
        var dx = 0f
        var startX = 0f
        var start = 0L
        detectHorizontalDragGestures(
            onDragStart = { offset -> dx = 0f; startX = offset.x; start = System.currentTimeMillis() },
            onDragEnd = {
                SwipeClassifier.classify(
                    SwipeClassifier.Touch(
                        dxPx = dx,
                        dyPx = 0f,
                        durationMs = System.currentTimeMillis() - start,
                        density = density,
                        startXPx = if (requireEdge) startX else null,
                        widthPx = if (requireEdge) size.width.toFloat() else null,
                    ),
                )?.let(onSwipe)
            },
            onHorizontalDrag = { change, amount ->
                dx += amount
                change.consume()
            },
        )
    }
}
