package com.krlbryn.sophia.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krlbryn.sophia.R
import com.krlbryn.sophia.browser.BrowserViewModel
import com.krlbryn.sophia.browser.ControlsPanel
import com.krlbryn.sophia.browser.PageError
import com.krlbryn.sophia.browser.TabUi
import com.krlbryn.sophia.core.LayoutClass
import com.krlbryn.sophia.core.SophiaConstants
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.ui.components.NoticeBand
import com.krlbryn.sophia.ui.components.SecurityIcon
import com.krlbryn.sophia.ui.components.SiteAvatar
import com.krlbryn.sophia.ui.navigation.AppActions
import com.krlbryn.sophia.ui.navigation.Destination
import kotlinx.coroutines.flow.flowOf

@Composable
fun BrowserScreen(browser: BrowserViewModel, layout: LayoutClass, actions: AppActions) {
    val tabs by browser.tabs.collectAsStateWithLifecycle()
    val activeId by browser.activeTabId.collectAsStateWithLifecycle()
    val notice by browser.notice.collectAsStateWithLifecycle()
    val toolbarVisible by browser.toolbarVisible.collectAsStateWithLifecycle()
    val focusRequests by browser.focusAddressRequests.collectAsStateWithLifecycle()
    val controls by browser.controlsPanel.collectAsStateWithLifecycle()
    val tab = tabs.firstOrNull { it.id == activeId }
    val compact = layout == LayoutClass.COMPACT
    val expanded = layout == LayoutClass.EXPANDED
    var showTabs by rememberSaveable { mutableStateOf(false) }
    var sidePanel by rememberSaveable { mutableStateOf(false) }
    val bookmarked by remember(tab?.url) {
        if (tab != null && tab.url.isNotEmpty()) browser.bookmarkState(tab.url) else flowOf(false)
    }.collectAsStateWithLifecycle(false)

    BackHandler(enabled = controls != ControlsPanel.NONE) { browser.hideControls() }
    BackHandler(enabled = controls == ControlsPanel.NONE && tab?.canGoBack == true) { browser.back() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        AnimatedVisibility(visible = !compact || toolbarVisible, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                BrowserToolbar(
                    tab = tab,
                    tabCount = tabs.size,
                    compact = compact,
                    bookmarked = bookmarked,
                    focusRequests = focusRequests,
                    sidePanelOpen = sidePanel,
                    showSidePanelToggle = expanded,
                    onSubmit = { browser.open(it) },
                    onBack = browser::back,
                    onForward = browser::forward,
                    onReload = browser::reload,
                    onStop = browser::stop,
                    onHome = browser::goHome,
                    onBookmark = browser::toggleBookmark,
                    onTabs = { showTabs = true },
                    onNewTab = browser::newTab,
                    onToggleSidePanel = { sidePanel = !sidePanel },
                    onAskSelection = { browser.sendPageToAssistant(selectionOnly = true) },
                    onSummarizePage = { browser.sendPageToAssistant(selectionOnly = false) },
                    onShowControls = browser::showControls,
                    actions = actions,
                )
                if (!compact && tabs.size > 1) {
                    TabStrip(tabs, activeId, onSelect = browser::selectTab, onClose = browser::closeTab, onNew = browser::newTab)
                }
            }
        }
        if (tab != null && tab.loading) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }
        notice?.let { n ->
            NoticeBand(
                n.message,
                warning = true,
                actionLabel = n.action?.label,
                onAction = { n.action?.let(browser::runNoticeAction) },
                onDismiss = browser::dismissNotice,
            )
        }
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        // Alternatives to the swipe gestures for TalkBack and Switch Access users.
                        customActions = listOf(
                            CustomAccessibilityAction("Show navigation controls") { browser.showControls(ControlsPanel.NAVIGATION); true },
                            CustomAccessibilityAction("Show bookmarks panel") { browser.showControls(ControlsPanel.BOOKMARKS); true },
                        )
                    },
            ) {
                when {
                    tab == null || tab.isStartPage -> StartPage(
                        modifier = Modifier.swipeToSophiaControls(browser::onSwipe),
                        onOpenPortal = { browser.openPortal() },
                        onFocusAddress = browser::requestAddressFocus,
                    )
                    tab.error != null -> ErrorPage(
                        tab.error,
                        modifier = Modifier.swipeToSophiaControls(browser::onSwipe),
                        onRetry = browser::reload,
                        onBack = browser::back,
                        canGoBack = tab.canGoBack,
                    )
                    else -> key(tab.id) {
                        AndroidView(
                            factory = { ctx -> browser.obtainWebView(tab.id, ctx) },
                            modifier = Modifier.fillMaxSize().testTag("webview"),
                        )
                    }
                }
                GesturePanels(
                    panel = controls,
                    canGoBack = tab?.canGoBack == true,
                    canGoForward = tab?.canGoForward == true,
                    canReload = tab != null && !tab.isStartPage,
                    onSwipe = browser::onSwipe,
                    onDismiss = browser::hideControls,
                    onBack = browser::back,
                    onForward = browser::forward,
                    onReload = { browser.reload(); browser.hideControls() },
                    onOpenPortal = { browser.hideControls(); browser.openPortal() },
                    onOpenBookmarks = { browser.hideControls(); actions.navigate(Destination.BOOKMARKS) },
                )
            }
            if (expanded && sidePanel) {
                VerticalDivider()
                BrowserSidePanel(
                    modifier = Modifier.width(340.dp).fillMaxHeight(),
                    onClose = { sidePanel = false },
                    onAskSelection = { browser.sendPageToAssistant(selectionOnly = true) },
                    onSummarizePage = { browser.sendPageToAssistant(selectionOnly = false) },
                    onOpen = { browser.open(it) },
                )
            }
        }
    }

    if (showTabs) {
        TabsSheet(
            tabs = tabs,
            activeId = activeId,
            onSelect = { browser.selectTab(it); showTabs = false },
            onClose = browser::closeTab,
            onNew = { browser.newTab(); showTabs = false },
            onDismiss = { showTabs = false },
        )
    }
}

@Composable
private fun BrowserToolbar(
    tab: TabUi?,
    tabCount: Int,
    compact: Boolean,
    bookmarked: Boolean,
    focusRequests: Int,
    sidePanelOpen: Boolean,
    showSidePanelToggle: Boolean,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onHome: () -> Unit,
    onBookmark: () -> Unit,
    onTabs: () -> Unit,
    onNewTab: () -> Unit,
    onToggleSidePanel: () -> Unit,
    onAskSelection: () -> Unit,
    onSummarizePage: () -> Unit,
    onShowControls: (ControlsPanel) -> Unit,
    actions: AppActions,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val hasPage = tab != null && !tab.isStartPage
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) {
                // Phones keep the page area clear: Back/Forward/Reload live in the swipe-left panel.
                IconButton(onClick = onBack, enabled = tab?.canGoBack == true) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                IconButton(onClick = onForward, enabled = tab?.canGoForward == true) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward") }
                if (tab?.loading == true) {
                    IconButton(onClick = onStop) { Icon(Icons.Filled.Close, "Stop loading") }
                } else {
                    IconButton(onClick = onReload, enabled = hasPage) { Icon(Icons.Filled.Refresh, "Reload") }
                }
                IconButton(onClick = onHome) { Icon(Icons.Outlined.Home, "Home page") }
            }
            AddressField(tab, focusRequests, onSubmit, Modifier.weight(1f))
            if (!compact) {
                IconButton(onClick = onBookmark, enabled = hasPage) {
                    Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (bookmarked) "Remove bookmark" else "Bookmark this page")
                }
                IconButton(onClick = onNewTab) { Icon(Icons.Outlined.Add, "New tab") }
            }
            TabCountButton(tabCount, onTabs)
            if (showSidePanelToggle) {
                IconButton(onClick = onToggleSidePanel) {
                    Icon(Icons.AutoMirrored.Outlined.ViewSidebar, if (sidePanelOpen) "Hide tools panel" else "Show tools panel")
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("browser_menu")) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Navigation controls") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                        onClick = { menu = false; onShowControls(ControlsPanel.NAVIGATION) },
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks panel") },
                        leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) },
                        onClick = { menu = false; onShowControls(ControlsPanel.BOOKMARKS) },
                    )
                    HorizontalDivider()
                    if (compact) {
                        if (tab?.loading == true) {
                            DropdownMenuItem(text = { Text("Stop loading") }, leadingIcon = { Icon(Icons.Filled.Close, null) }, onClick = { menu = false; onStop() })
                        }
                        DropdownMenuItem(text = { Text("New tab") }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = { menu = false; onNewTab() })
                        DropdownMenuItem(
                            text = { Text(if (bookmarked) "Remove bookmark" else "Bookmark page") },
                            enabled = hasPage,
                            leadingIcon = { Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, null) },
                            onClick = { menu = false; onBookmark() },
                        )
                        DropdownMenuItem(text = { Text("Home page") }, leadingIcon = { Icon(Icons.Outlined.Home, null) }, onClick = { menu = false; onHome() })
                        HorizontalDivider()
                    }
                    DropdownMenuItem(text = { Text("Explain selected text") }, enabled = hasPage, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }, onClick = { menu = false; onAskSelection() })
                    DropdownMenuItem(text = { Text("Study this page") }, enabled = hasPage, leadingIcon = { Icon(Icons.Outlined.Summarize, null) }, onClick = { menu = false; onSummarizePage() })
                    DropdownMenuItem(
                        text = { Text("Share page") },
                        enabled = hasPage,
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menu = false; tab?.url?.let { shareUrl(it, context) } },
                    )
                    DropdownMenuItem(text = { Text("Downloads") }, onClick = { menu = false; actions.navigate(Destination.DOWNLOADS) })
                    DropdownMenuItem(text = { Text("History") }, onClick = { menu = false; actions.navigate(Destination.HISTORY) })
                }
            }
        }
    }
}

private fun shareUrl(url: String, context: android.content.Context) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, url)
    context.startActivity(android.content.Intent.createChooser(send, null))
}

@Composable
private fun AddressField(tab: TabUi?, focusRequests: Int, onSubmit: (String) -> Unit, modifier: Modifier) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val display = tab?.url?.takeIf { it.isNotEmpty() }?.let(UrlPolicy::displayUrl).orEmpty()
    var value by remember { mutableStateOf(TextFieldValue(display)) }
    LaunchedEffect(display, focused) {
        if (!focused) value = TextFieldValue(display)
    }
    LaunchedEffect(focusRequests) {
        if (focusRequests > 0) focusRequester.requestFocus()
    }
    TextField(
        value = value,
        onValueChange = { value = it },
        modifier = modifier
            .height(48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused && !focused) value = value.copy(selection = TextRange(0, value.text.length))
                focused = state.isFocused
            }
            .testTag("address_bar"),
        singleLine = true,
        placeholder = { Text("Search or enter address", maxLines = 1) },
        leadingIcon = {
            if (tab != null && !tab.isStartPage && !focused) SecurityIcon(tab.security) else Icon(Icons.Outlined.Search, null)
        },
        trailingIcon = if (focused && value.text.isNotEmpty()) {
            { IconButton(onClick = { value = TextFieldValue("") }) { Icon(Icons.Filled.Close, "Clear") } }
        } else null,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = {
            onSubmit(value.text)
            focusManager.clearFocus()
        }),
    )
}

@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag("tabs_button")) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TabStrip(tabs: List<TabUi>, activeId: Long?, onSelect: (Long) -> Unit, onClose: (Long) -> Unit, onNew: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { t ->
            val active = t.id == activeId
            Surface(
                onClick = { onSelect(t.id) },
                shape = RoundedCornerShape(10.dp),
                color = if (active) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = if (active) 2.dp else 0.dp,
            ) {
                Row(Modifier.padding(start = 10.dp).widthIn(max = 220.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tabTitle(t),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    IconButton(onClick = { onClose(t.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Close ${tabTitle(t)}", Modifier.size(16.dp))
                    }
                }
            }
        }
        IconButton(onClick = onNew) { Icon(Icons.Outlined.Add, "New tab") }
    }
}

private fun tabTitle(t: TabUi) = when {
    t.isStartPage -> "New tab"
    t.title.isNotBlank() -> t.title
    else -> UrlPolicy.hostOf(t.url)
}

@Composable
private fun TabsSheet(
    tabs: List<TabUi>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tabs (${tabs.size}/${SophiaConstants.MAX_TABS})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onNew) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("New tab") }
        }
        Spacer(Modifier.height(8.dp))
        tabs.forEach { t ->
            ListItem(
                headlineContent = { Text(tabTitle(t), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(if (t.isStartPage) "Start page" else UrlPolicy.hostOf(t.url), maxLines = 1) },
                leadingContent = { SiteAvatar(t.url.ifEmpty { "https://sophia" }, size = 36.dp) },
                trailingContent = { IconButton(onClick = { onClose(t.id) }) { Icon(Icons.Filled.Close, "Close tab") } },
                tonalElevation = if (t.id == activeId) 4.dp else 0.dp,
                modifier = Modifier.clickable { onSelect(t.id) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StartPage(modifier: Modifier, onOpenPortal: () -> Unit, onFocusAddress: () -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.ic_sophia_mark), contentDescription = null, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(12.dp))
        Text("Sophia", style = MaterialTheme.typography.headlineMedium)
        Text(SophiaConstants.TAGLINE, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenPortal, modifier = Modifier.height(48.dp)) { Text("Open AMA Discipulus") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onFocusAddress, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Outlined.Search, null)
            Spacer(Modifier.width(8.dp))
            Text("Search or enter an address")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Secure (HTTPS) sites open directly. Sophia asks before opening sites without a secure connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun ErrorPage(error: PageError, modifier: Modifier, onRetry: () -> Unit, onBack: () -> Unit, canGoBack: Boolean) {
    val (icon, title, body) = when (error) {
        is PageError.Network -> Triple(
            Icons.Outlined.CloudOff,
            "Can't reach this page",
            "Check your internet connection and try again." + if (error.description.isNotBlank()) "\n(${error.description})" else "",
        )
        is PageError.Certificate -> Triple(
            Icons.Outlined.GppBad,
            "Your connection isn't private",
            "${error.problem} Sophia stopped loading ${UrlPolicy.hostOf(error.url)} to protect your information. Certificate errors can't be bypassed.",
        )
        is PageError.Crashed -> Triple(Icons.Outlined.Warning, "This page stopped working", "Reload to try again. Your other tabs are fine.")
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp).testTag("error_page"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canGoBack) OutlinedButton(onClick = onBack) { Text("Go back") }
            if (error !is PageError.Certificate) Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
