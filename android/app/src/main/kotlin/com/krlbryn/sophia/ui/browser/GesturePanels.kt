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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krlbryn.sophia.browser.ControlsPanel
import com.krlbryn.sophia.core.SwipeClassifier
import com.krlbryn.sophia.core.SwipeDirection
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.data.repo.LibraryRepository
import com.krlbryn.sophia.ui.theme.Brand

/**
 * Sophia's swipe controls, layered over the page. Swiping left slides navigation controls in from the
 * right edge; swiping right slides the bookmarks panel in from the left edge. Tapping the page or
 * swiping back toward the centre closes them.
 */
@Composable
fun BoxScope.GesturePanels(
    panel: ControlsPanel,
    canGoBack: Boolean,
    canGoForward: Boolean,
    canReload: Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onOpenPortal: () -> Unit,
    onOpenBookmarks: () -> Unit,
) {
    // While a panel is open, a light scrim takes taps and swipes so the page underneath isn't touched by mistake.
    AnimatedVisibility(panel != ControlsPanel.NONE, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
                .swipeToSophiaControls(onSwipe)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .testTag("controls_scrim"),
        )
    }

    AnimatedVisibility(
        visible = panel == ControlsPanel.NAVIGATION,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        PanelCard(Modifier.padding(end = 12.dp).semantics { paneTitle = "Navigation controls" }.testTag("nav_controls")) {
            ControlButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", canGoBack, onBack)
            ControlButton(Icons.AutoMirrored.Filled.ArrowForward, "Forward", canGoForward, onForward)
            ControlButton(Icons.Filled.Refresh, "Reload", canReload, onReload)
        }
    }

    AnimatedVisibility(
        visible = panel == ControlsPanel.BOOKMARKS,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
        modifier = Modifier.align(Alignment.CenterStart),
    ) {
        PanelCard(Modifier.padding(start = 12.dp).semantics { paneTitle = "Bookmarks" }.testTag("bookmark_controls")) {
            PortalShortcutRow(onOpenPortal)
            Spacer(Modifier.height(4.dp))
            ControlButton(Icons.Outlined.Bookmarks, "Bookmarks", true, onOpenBookmarks)
        }
    }
}

@Composable
private fun PanelCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.widthIn(max = 280.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(min = 132.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** The built-in AMA Discipulus bookmark: fixed title and URL, not editable. */
@Composable
private fun PortalShortcutRow(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brand.Maroon)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("builtin_discipulus"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Brand.Gold), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.School, null, tint = Brand.OnGold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(LibraryRepository.BUILT_IN_TITLE, style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text(
                UrlPolicy.hostOf(LibraryRepository.BUILT_IN_URL),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Recognises the same swipes on Sophia's own surfaces (start page, error page, scrim) that
 * SwipeWatcher recognises on web pages, using the shared [SwipeClassifier].
 */
@Composable
fun Modifier.swipeToSophiaControls(onSwipe: (SwipeDirection) -> Unit): Modifier {
    val density = LocalDensity.current.density
    return this.pointerInput(onSwipe) {
        var dx = 0f
        var start = 0L
        detectHorizontalDragGestures(
            onDragStart = { dx = 0f; start = System.currentTimeMillis() },
            onDragEnd = {
                SwipeClassifier.classify(SwipeClassifier.Touch(dx, 0f, System.currentTimeMillis() - start, density))?.let(onSwipe)
            },
            onHorizontalDrag = { change, amount ->
                dx += amount
                change.consume()
            },
        )
    }
}
