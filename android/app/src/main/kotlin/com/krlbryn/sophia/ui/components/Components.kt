package com.krlbryn.sophia.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krlbryn.sophia.R
import com.krlbryn.sophia.core.SecurityLevel
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.ui.theme.Brand
import com.krlbryn.sophia.ui.theme.LocalSophiaColors

/** The Sophia mark and wordmark, as in the Lapsus for Windows title bar. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, showWordmark: Boolean = true, markSize: Dp = 30.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_sophia_mark),
            contentDescription = if (showWordmark) null else "Sophia",
            modifier = Modifier.size(markSize),
        )
        if (showWordmark) {
            Spacer(Modifier.width(10.dp))
            Text("Sophia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

enum class PillTone { GOLD, MAROON, NAVY, ON_DARK }

@Composable
fun Pill(text: String, tone: PillTone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val (bg, fg) = when (tone) {
        PillTone.GOLD -> Brand.Gold to Brand.OnGold
        PillTone.MAROON -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PillTone.NAVY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        PillTone.ON_DARK -> Color.White.copy(alpha = 0.16f) to Color.White
    }
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A round letter avatar for a site, as in the Lapsus for Windows "Recent pages" list. */
@Composable
fun SiteAvatar(url: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val host = UrlPolicy.hostOf(url).removePrefix("www.")
    val letter = host.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val palette = listOf(
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    val (bg, fg) = palette[(host.hashCode() and 0x7fffffff) % palette.size]
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(letter, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        action?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    SectionHeader(title, modifier) {
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/** A notice band, like the Lapsus for Windows "bands" that push the page down instead of covering it. */
@Composable
fun NoticeBand(
    message: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    val extra = LocalSophiaColors.current
    val bg = if (warning) extra.warningContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (warning) extra.warning else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = bg, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (warning) Icons.Filled.Warning else Icons.Outlined.Info, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, color = fg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel, color = fg) }
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Dismiss", color = fg) }
        }
    }
}

@Composable
fun SecurityIcon(level: SecurityLevel, modifier: Modifier = Modifier) {
    val (icon, tint, label) = when (level) {
        SecurityLevel.SECURE -> Triple(Icons.Filled.Lock, LocalSophiaColors.current.success, "Secure connection")
        SecurityLevel.INSECURE, SecurityLevel.CERT_ERROR ->
            Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, "Not secure")
        else -> Triple(Icons.Outlined.Public, MaterialTheme.colorScheme.onSurfaceVariant, "Page")
    }
    Icon(icon, null, tint = tint, modifier = modifier.size(16.dp).semantics { contentDescription = label })
}

@Composable
fun TwoLineText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
