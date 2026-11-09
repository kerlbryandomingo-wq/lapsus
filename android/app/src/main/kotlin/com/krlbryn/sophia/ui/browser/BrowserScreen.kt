package com.krlbryn.sophia.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krlbryn.sophia.R
import com.krlbryn.sophia.browser.BrowserViewModel
import com.krlbryn.sophia.browser.ControlsPanel
import com.krlbryn.sophia.browser.PageError
import com.krlbryn.sophia.core.LayoutClass
import com.krlbryn.sophia.core.SophiaConstants
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.data.repo.LibraryRepository
import com.krlbryn.sophia.data.settings.PortalBookmark
import com.krlbryn.sophia.ui.components.NoticeBand
import com.krlbryn.sophia.ui.navigation.AppActions
import com.krlbryn.sophia.ui.navigation.Destination
import com.krlbryn.sophia.ui.theme.Brand

/**
 * The browser: the page fills the whole screen, with no toolbar, tabs bar or buttons on top of it.
 * Everything else lives in the two side panels (see [GesturePanels]), which open with an edge swipe,
 * from the accessibility actions, or with keyboard shortcuts.
 */
@Composable
fun BrowserScreen(browser: BrowserViewModel, layout: LayoutClass, actions: AppActions) {
    val tabs by browser.tabs.collectAsStateWithLifecycle()
    val activeId by browser.activeTabId.collectAsStateWithLifecycle()
    val notice by browser.notice.collectAsStateWithLifecycle()
    val focusRequests by browser.focusAddressRequests.collectAsStateWithLifecycle()
    val controls by browser.controlsPanel.collectAsStateWithLifecycle()
    val settings by browser.settings.collectAsStateWithLifecycle()
    val tab = tabs.firstOrNull { it.id == activeId }
    var setUp by rememberSaveable { mutableStateOf<String?>(null) }

    // Ctrl+L (and "Search" on the start page) opens the address panel with the field focused.
    var lastFocusRequest by rememberSaveable { mutableStateOf(focusRequests) }
    var focusPending by remember { mutableStateOf(false) }
    LaunchedEffect(focusRequests) {
        if (focusRequests != lastFocusRequest) {
            lastFocusRequest = focusRequests
            focusPending = true
            browser.showControls(ControlsPanel.BOOKMARKS)
        }
    }

    BackHandler(enabled = controls != ControlsPanel.NONE) { browser.hideControls() }
    BackHandler(enabled = controls == ControlsPanel.NONE && tab?.canGoBack == true) { browser.back() }

    val bookmarks = listOf(PanelBookmark(LibraryRepository.BUILT_IN_TITLE.removePrefix("AMA ").trim(), LibraryRepository.BUILT_IN_URL, "discipulus")) +
        PortalBookmark.entries.map { b -> PanelBookmark(b.label, settings.urlFor(b).ifEmpty { null }, b.name.lowercase()) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("browser_content")
            .semantics {
                // Alternatives to the swipe gestures for TalkBack and Switch Access users.
                customActions = listOf(
                    CustomAccessibilityAction("Show address bar and bookmarks") { browser.showControls(ControlsPanel.BOOKMARKS); true },
                    CustomAccessibilityAction("Show back, forward, reload and settings") { browser.showControls(ControlsPanel.NAVIGATION); true },
                )
            },
    ) {
        val wide = layout != LayoutClass.COMPACT && maxWidth >= 600.dp
        when {
            tab == null || tab.isStartPage -> StartPage(
                modifier = Modifier.swipeToSophiaControls(browser::onSwipe, requireEdge = true),
                onOpenPortal = { browser.openPortal() },
                onFocusAddress = browser::requestAddressFocus,
            )
            tab.error != null -> ErrorPage(
                tab.error,
                modifier = Modifier.swipeToSophiaControls(browser::onSwipe, requireEdge = true),
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

        // Keep Android's back gesture from taking edge swipes in the middle of each edge, where the
        // panels open. Android honours at most 200dp per edge; elsewhere swipes can start a little
        // further in. These strips don't handle touches themselves, so the page still gets them.
        Box(Modifier.align(Alignment.CenterStart).size(width = 24.dp, height = 200.dp).systemGestureExclusion())
        Box(Modifier.align(Alignment.CenterEnd).size(width = 24.dp, height = 200.dp).systemGestureExclusion())

        // Loading indicator: a thin bar over the top of the page, only while it loads.
        if (tab != null && tab.loading) {
            LinearProgressIndicator(
                progress = { (tab.progress / 100f).coerceAtLeast(0.05f) },
                color = Brand.Maroon,
                trackColor = Color.Transparent,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).testTag("page_progress"),
            )
        }

        notice?.let { n ->
            NoticeBand(
                n.message,
                warning = true,
                actionLabel = n.action?.label,
                onAction = { n.action?.let(browser::runNoticeAction) },
                onDismiss = browser::dismissNotice,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }

        GesturePanels(
            panel = controls,
            wide = wide,
            currentUrl = tab?.url.orEmpty(),
            bookmarks = bookmarks,
            tabs = tabs,
            activeTabId = activeId,
            focusAddress = focusPending,
            onAddressFocused = { focusPending = false },
            canGoBack = tab?.canGoBack == true,
            canGoForward = tab?.canGoForward == true,
            canReload = tab != null && !tab.isStartPage,
            onSwipe = browser::onSwipe,
            onDismiss = browser::hideControls,
            onNavigate = { browser.hideControls(); browser.open(it) },
            onBookmark = { b ->
                if (b.url == null) setUp = b.key else { browser.hideControls(); browser.open(b.url) }
            },
            onManageBookmarks = { browser.hideControls(); actions.navigate(Destination.BOOKMARKS) },
            onSelectTab = { browser.selectTab(it); browser.hideControls() },
            onCloseTab = browser::closeTab,
            onBack = browser::back,
            onForward = browser::forward,
            onReload = { browser.reload(); browser.hideControls() },
            onSettings = { browser.hideControls(); actions.navigate(Destination.SETTINGS) },
        )
    }

    setUp?.let { key ->
        val bookmark = PortalBookmark.entries.firstOrNull { it.name.lowercase() == key }
        if (bookmark == null) {
            setUp = null
        } else {
            BookmarkSetupDialog(
                bookmark = bookmark,
                onDismiss = { setUp = null },
                onSave = { url -> browser.setPortalBookmark(bookmark, url).also { ok -> if (ok) setUp = null } },
            )
        }
    }
}

/** Asks for the address of a portal bookmark that hasn't been set up. Sophia never guesses these. */
@Composable
private fun BookmarkSetupDialog(bookmark: PortalBookmark, onDismiss: () -> Unit, onSave: (String) -> Boolean) {
    var url by rememberSaveable { mutableStateOf("https://") }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up ${bookmark.label}") },
        text = {
            Column {
                Text("Enter the ${bookmark.label} address your school gave you. You can change it later in Settings.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = false },
                    singleLine = true,
                    isError = error,
                    supportingText = { if (error) Text("Use a secure https:// address.") },
                    modifier = Modifier.fillMaxWidth().testTag("bookmark_setup_url"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { error = !onSave(url) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
        Text("Sophia", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF1F1A1A))
        Text(SophiaConstants.TAGLINE, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF5B5252))
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenPortal, modifier = Modifier.height(48.dp)) { Text("Open AMA Discipulus") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onFocusAddress, modifier = Modifier.height(48.dp)) { Text("Search or enter an address") }
        Spacer(Modifier.height(16.dp))
        Text(
            "Swipe in from the left edge for the address bar and bookmarks, or from the right edge for Back, Forward, Reload and Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF5B5252),
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
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = Color(0xFF1F1A1A))
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5B5252), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canGoBack) OutlinedButton(onClick = onBack) { Text("Go back") }
            if (error !is PageError.Certificate) Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
