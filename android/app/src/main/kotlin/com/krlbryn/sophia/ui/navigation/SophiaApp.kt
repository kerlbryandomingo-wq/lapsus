package com.krlbryn.sophia.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import android.content.ActivityNotFoundException
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.krlbryn.sophia.browser.BrowserEvent
import com.krlbryn.sophia.browser.BrowserViewModel
import com.krlbryn.sophia.core.AdaptiveLayout
import com.krlbryn.sophia.core.LayoutClass
import com.krlbryn.sophia.ui.assistant.AssistantScreen
import com.krlbryn.sophia.ui.browser.BrowserScreen
import com.krlbryn.sophia.ui.components.BrandLockup
import com.krlbryn.sophia.ui.downloads.DownloadsScreen
import com.krlbryn.sophia.ui.home.HomeScreen
import com.krlbryn.sophia.ui.library.LibraryScreen
import com.krlbryn.sophia.ui.library.LibraryTab
import com.krlbryn.sophia.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

enum class Destination(val route: String, val label: String, val icon: ImageVector, val onPhoneBar: Boolean) {
    HOME("home", "Home", Icons.Outlined.Home, true),
    BROWSER("browser", "Browser", Icons.Outlined.Language, true),
    BOOKMARKS("bookmarks", "Bookmarks", Icons.Outlined.Bookmarks, true),
    HISTORY("history", "History", Icons.Outlined.History, false),
    DOWNLOADS("downloads", "Downloads", Icons.Outlined.Download, true),
    ASSISTANT("assistant", "Study Assistant", Icons.Outlined.AutoAwesome, false),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings, true),
}

/** Everything a screen may ask the app shell to do. */
class AppActions(
    val navigate: (Destination) -> Unit,
    /** Opens a URL (or search text) in the browser and shows it. */
    val openUrl: (url: String, newTab: Boolean) -> Unit,
    val openPortal: () -> Unit,
    val message: (String) -> Unit,
)

fun NavHostController.goTo(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The adaptive app shell. The interface is chosen from the window's actual size, so it follows rotation,
 * split-screen, freeform windows, foldables and external displays:
 * - compact (phones): bottom navigation bar, collapsible browser toolbar;
 * - medium (large phones in landscape, small tablets): navigation rail;
 * - expanded (tablets): permanent navigation drawer, persistent toolbar and optional side panel.
 */
@Composable
fun SophiaApp(browser: BrowserViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val fullscreenView by browser.fullscreenView.collectAsStateWithLifecycle()
    var linkMenu by remember { mutableStateOf<BrowserEvent.LinkMenu?>(null) }
    var pendingFileCallback by remember { mutableStateOf<BrowserEvent.ChooseFiles?>(null) }

    val actions = remember(nav) {
        AppActions(
            navigate = { nav.goTo(it) },
            openUrl = { url, newTab -> browser.open(url, newTab); nav.goTo(Destination.BROWSER) },
            openPortal = { browser.openPortal(); nav.goTo(Destination.BROWSER) },
            message = { text -> scope.launch { snackbar.showSnackbar(text) } },
        )
    }

    val fileChooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingFileCallback
        pendingFileCallback = null
        pending?.callback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }

    LaunchedEffect(browser) {
        browser.eventFlow.collect { event ->
            when (event) {
                is BrowserEvent.LaunchExternal -> try {
                    context.startActivity(event.intent)
                } catch (e: ActivityNotFoundException) {
                    if (event.fallbackUrl != null) browser.open(event.fallbackUrl) else actions.message("No app on this device can open that link.")
                } catch (e: SecurityException) {
                    actions.message("That app can't be opened from Sophia.")
                }
                is BrowserEvent.ChooseFiles -> {
                    // Only one chooser at a time; a previous unanswered request is cancelled.
                    pendingFileCallback?.callback?.onReceiveValue(null)
                    pendingFileCallback = event
                    try {
                        fileChooser.launch(event.intent)
                    } catch (e: ActivityNotFoundException) {
                        pendingFileCallback = null
                        event.callback.onReceiveValue(null)
                        actions.message("No app is available to pick files.")
                    }
                }
                is BrowserEvent.LinkMenu -> linkMenu = event
                is BrowserEvent.Message -> actions.message(event.text)
                BrowserEvent.OpenAssistant -> nav.goTo(Destination.ASSISTANT)
                BrowserEvent.ShowBrowser -> nav.goTo(Destination.BROWSER)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val layout = AdaptiveLayout.classify(maxWidth.value, maxHeight.value)
        val inBrowser = currentRoute == null || currentRoute == Destination.BROWSER.route
        ImmersiveMode(enabled = inBrowser || fullscreenView != null)
        val content: @Composable (Modifier) -> Unit = { modifier ->
            NavHost(nav, startDestination = Destination.BROWSER.route, modifier = modifier) {
                composable(Destination.HOME.route) { HomeScreen(layout, actions, browser) }
                composable(Destination.BROWSER.route) { BrowserScreen(browser, layout, actions) }
                composable(Destination.BOOKMARKS.route) { LibraryScreen(LibraryTab.BOOKMARKS, layout, actions) }
                composable(Destination.HISTORY.route) { LibraryScreen(LibraryTab.HISTORY, layout, actions) }
                composable(Destination.DOWNLOADS.route) { DownloadsScreen(layout, actions, browser) }
                composable(Destination.ASSISTANT.route) { AssistantScreen(layout, actions) }
                composable(Destination.SETTINGS.route) { SettingsScreen(layout, actions, browser) }
            }
        }

        when (layout) {
            LayoutClass.COMPACT -> Scaffold(
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    AnimatedVisibility(
                        visible = !inBrowser,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        NavigationBar(Modifier.testTag("bottom_nav")) {
                            Destination.entries.filter { it.onPhoneBar }.forEach { d ->
                                NavigationBarItem(
                                    selected = isSelected(currentRoute, d),
                                    onClick = { nav.goTo(d) },
                                    icon = { Icon(d.icon, null) },
                                    label = { Text(d.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                },
            ) { inner ->
                content(
                    if (inBrowser) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .padding(inner)
                            .consumeWindowInsets(inner)
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    },
                )
            }

            LayoutClass.MEDIUM -> Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { inner ->
                Row(Modifier.padding(inner).fillMaxSize()) {
                    if (!inBrowser) NavigationRail(
                        modifier = Modifier.testTag("nav_rail"),
                        header = { BrandLockup(showWordmark = false, modifier = Modifier.padding(vertical = 8.dp)) },
                    ) {
                        Destination.entries.forEach { d ->
                            NavigationRailItem(
                                selected = isSelected(currentRoute, d),
                                onClick = { nav.goTo(d) },
                                icon = { Icon(d.icon, null) },
                                label = { Text(railLabel(d), maxLines = 1) },
                            )
                        }
                    }
                    content(
                        if (inBrowser) {
                            Modifier.weight(1f).fillMaxHeight()
                        } else {
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                        },
                    )
                }
            }

            LayoutClass.EXPANDED -> Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { inner ->
                PermanentNavigationDrawer(
                    modifier = Modifier.padding(inner),
                    drawerContent = {
                        if (!inBrowser) PermanentDrawerSheet(
                            Modifier.width(248.dp).testTag("nav_drawer"),
                            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp)) {
                                Spacer(Modifier.height(16.dp))
                                BrandLockup(Modifier.padding(start = 12.dp, bottom = 16.dp))
                                Destination.entries.forEach { d ->
                                    if (d == Destination.SETTINGS) {
                                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    }
                                    NavigationDrawerItem(
                                        label = { Text(d.label) },
                                        icon = { Icon(d.icon, null) },
                                        selected = isSelected(currentRoute, d),
                                        onClick = { nav.goTo(d) },
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    },
                ) {
                    content(
                        if (inBrowser) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                        },
                    )
                }
            }
        }

        // A page element in genuine fullscreen (for example a lecture video) covers the whole window.
        fullscreenView?.let { view -> FullscreenHost(view) }
        BackHandler(enabled = fullscreenView != null) { browser.exitFullscreen() }
    }

    linkMenu?.let { menu ->
        ModalBottomSheet(onDismissRequest = { linkMenu = null }) {
            Text(
                menu.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(if (menu.isImage) "Open image in new tab" else "Open in new tab") },
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                modifier = Modifier.clickable { actions.openUrl(menu.url, true); linkMenu = null },
            )
            ListItem(
                headlineContent = { Text(if (menu.isImage) "Download image" else "Download link") },
                leadingContent = { Icon(Icons.Outlined.Download, null) },
                modifier = Modifier.clickable { browser.downloadLink(menu.url); linkMenu = null },
            )
            ListItem(
                headlineContent = { Text("Copy address") },
                leadingContent = { Icon(Icons.Outlined.ContentCopy, null) },
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(menu.url)); linkMenu = null; actions.message("Address copied")
                },
            )
            ListItem(
                headlineContent = { Text("Share") },
                leadingContent = { Icon(Icons.Outlined.Share, null) },
                modifier = Modifier.clickable {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, menu.url)
                    context.startActivity(android.content.Intent.createChooser(send, null))
                    linkMenu = null
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun railLabel(d: Destination) = if (d == Destination.ASSISTANT) "Assistant" else d.label

private fun isSelected(route: String?, d: Destination) = route == d.route


/** Hosts a page's fullscreen element; system bars are hidden by the Activity while this is shown. */
@Composable
private fun FullscreenHost(view: View) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                (view.parent as? ViewGroup)?.removeView(view)
                view
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Real Android immersive mode while browsing or showing a page's fullscreen video: the status and
 * navigation bars are hidden, and a swipe from the screen edge brings them back for a moment.
 * Nothing about this is reported to web pages; they see the window size Android gives them.
 */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (enabled) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

