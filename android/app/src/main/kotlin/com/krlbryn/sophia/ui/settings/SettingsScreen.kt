package com.krlbryn.sophia.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krlbryn.sophia.BuildConfig
import com.krlbryn.sophia.browser.BrowserViewModel
import com.krlbryn.sophia.core.AiProviderId
import com.krlbryn.sophia.core.LayoutClass
import com.krlbryn.sophia.core.SearchEngine
import com.krlbryn.sophia.core.SophiaConstants
import com.krlbryn.sophia.data.repo.secretNameFor
import com.krlbryn.sophia.data.settings.AccentColor
import com.krlbryn.sophia.data.settings.AppSettings
import com.krlbryn.sophia.data.settings.DownloadLocation
import com.krlbryn.sophia.data.settings.ResponseDetail
import com.krlbryn.sophia.data.settings.ThemeMode
import com.krlbryn.sophia.ui.components.BrandLockup
import com.krlbryn.sophia.ui.components.ConfirmDialog
import com.krlbryn.sophia.ui.navigation.AppActions
import com.krlbryn.sophia.ui.theme.LocalSophiaColors
import kotlin.math.roundToInt

enum class SettingsSection(val title: String, val summary: String, val icon: ImageVector) {
    APPEARANCE("Appearance", "Theme, accent colour, your name", Icons.Outlined.Palette),
    BROWSER("Browser", "Search, homepage, downloads, portal sites", Icons.Outlined.Language),
    PRIVACY("Privacy and security", "History, cookies, site permissions", Icons.Outlined.Security),
    ASSISTANT("Study Assistant", "AI provider, API key, model", Icons.Outlined.AutoAwesome),
    ABOUT("About Sophia", "Version, credits, licences", Icons.Outlined.Info),
}

@Composable
fun SettingsScreen(layout: LayoutClass, actions: AppActions, browser: BrowserViewModel, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { n -> SettingsSection.entries.firstOrNull { it.name == n } }
    val twoPane = layout == LayoutClass.EXPANDED

    LaunchedEffect(vm) { vm.messages.collect(actions.message) }
    BackHandler(enabled = !twoPane && selected != null) { selectedName = null }

    if (twoPane) {
        val current = selected ?: SettingsSection.APPEARANCE
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
                SectionList(current, onPick = { selectedName = it.name })
            }
            VerticalDivider()
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(current.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp))
                SectionBody(current, settings, vm, browser, actions, Modifier.padding(horizontal = 28.dp))
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text(selected?.title ?: "Settings") },
                    navigationIcon = {
                        if (selected != null) {
                            IconButton(onClick = { selectedName = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to settings") }
                        }
                    },
                )
            },
        ) { inner ->
            Column(Modifier.padding(inner).fillMaxSize()) {
                if (selected == null) {
                    Column(Modifier.verticalScroll(rememberScrollState()).testTag("settings_list")) {
                        SectionList(null, onPick = { selectedName = it.name })
                    }
                } else {
                    SectionBody(selected, settings, vm, browser, actions, Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionList(current: SettingsSection?, onPick: (SettingsSection) -> Unit) {
    SettingsSection.entries.forEach { s ->
        ListItem(
            headlineContent = { Text(s.title) },
            supportingContent = { Text(s.summary) },
            leadingContent = { Icon(s.icon, null) },
            colors = if (s == current) {
                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                ListItemDefaults.colors()
            },
            modifier = Modifier.clickable { onPick(s) }.testTag("settings_${s.name.lowercase()}"),
        )
    }
}

@Composable
private fun SectionBody(
    section: SettingsSection,
    s: AppSettings,
    vm: SettingsViewModel,
    browser: BrowserViewModel,
    actions: AppActions,
    modifier: Modifier,
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .then(modifier)
            .padding(bottom = 32.dp),
    ) {
        Column(Modifier.widthIn(max = 720.dp)) {
            when (section) {
                SettingsSection.APPEARANCE -> Appearance(s, vm)
                SettingsSection.BROWSER -> BrowserSettings(s, vm)
                SettingsSection.PRIVACY -> Privacy(s, vm, browser, actions)
                SettingsSection.ASSISTANT -> Assistant(s, vm)
                SettingsSection.ABOUT -> About()
            }
        }
    }
}

// region Building blocks

@Composable
private fun Group(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ToggleRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit, tag: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) })
            .padding(vertical = 10.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Hint(detail)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> RadioGroup(options: List<T>, selected: T, label: (T) -> String, detail: (T) -> String? = { null }, onPick: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { o ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = o == selected, role = Role.RadioButton, onClick = { onPick(o) })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = o == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label(o), style = MaterialTheme.typography.bodyLarge)
                    detail(o)?.let { Hint(it) }
                }
            }
        }
    }
}

@Composable
private fun SaveField(label: String, initial: String, onSave: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, tag: String? = null) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.weight(1f).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { onSave(text) }, enabled = text != initial) { Text("Save") }
    }
}

// endregion

@Composable
private fun Appearance(s: AppSettings, vm: SettingsViewModel) {
    Group("Theme")
    RadioGroup(ThemeMode.entries, s.themeMode, { it.label }, onPick = vm::setTheme)
    Group("Accent colour")
    RadioGroup(AccentColor.entries, s.accent, { it.label }, onPick = vm::setAccent)
    Group("Your name")
    SaveField("Shown in the Home greeting", s.displayName, vm::setDisplayName)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowserSettings(s: AppSettings, vm: SettingsViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setDownloadLocation(if (granted) DownloadLocation.PUBLIC_DOWNLOADS else DownloadLocation.APP_STORAGE)
    }

    Group("Search engine")
    RadioGroup(SearchEngine.entries, s.searchEngine, { it.label }, onPick = vm::setSearchEngine)

    Group("Homepage")
    SaveField("Homepage address", s.homepage, vm::setHomepage, KeyboardType.Uri, tag = "homepage_field")
    Hint("Sophia opens this page from Home. It must use https://.")

    Group("Pages")
    ToggleRow("JavaScript", "Most portals, including AMA Discipulus, need this on.", s.javaScriptEnabled, vm::setJavaScript)
    var zoom by rememberSaveable(s.textZoom) { mutableFloatStateOf(s.textZoom.toFloat()) }
    Text("Text size: ${zoom.roundToInt()}%", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
    Slider(
        value = zoom,
        onValueChange = { zoom = it },
        onValueChangeFinished = { vm.setTextZoom(zoom.roundToInt()) },
        valueRange = 50f..200f,
        steps = 14,
    )

    Group("Downloads")
    RadioGroup(DownloadLocation.entries, s.downloadLocation, { it.label }, { it.detail }) { choice ->
        val needsPermission = choice == DownloadLocation.PUBLIC_DOWNLOADS &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.setDownloadLocation(choice)
    }

    Group("Portal sites")
    ToggleRow(
        "Portal-only mode",
        "Only open AMA portal sites and the sites you add below. Other links are blocked.",
        s.portalOnly,
        vm::setPortalOnly,
    )
    Hint("Built in: ${SophiaConstants.PORTAL_DOMAINS.joinToString()}")
    if (s.extraPortalHosts.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.extraPortalHosts.forEach { host ->
                FilterChip(
                    selected = true,
                    onClick = { vm.removeExtraHost(host) },
                    label = { Text(host) },
                    trailingIcon = { Icon(Icons.Outlined.Close, "Remove $host") },
                )
            }
        }
    }
    var host by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Add a site, e.g. classroom.example.edu") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { vm.addExtraHost(host); host = "" }, enabled = host.isNotBlank()) { Text("Add") }
    }
}

@Composable
private fun Privacy(s: AppSettings, vm: SettingsViewModel, browser: BrowserViewModel, actions: AppActions) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }

    Group("History")
    ToggleRow("Save browsing history", "Turning this off stops new entries. Existing history stays until you clear it.", s.historyEnabled, vm::setHistoryEnabled)
    OutlinedButton(onClick = { confirm = "history" }, modifier = Modifier.testTag("clear_history")) { Text("Clear history") }

    Group("Cookies and cache")
    ToggleRow(
        "Allow third-party cookies",
        "Some portals need these for embedded videos, documents and sign-in.",
        s.thirdPartyCookies,
        vm::setThirdPartyCookies,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { confirm = "cookies" }) { Text("Clear cookies") }
        OutlinedButton(onClick = { browser.clearBrowsingData(cookies = false, cache = true); actions.message("Cache cleared") }) { Text("Clear cache") }
    }
    Hint("Clearing cookies signs you out of websites, including AMA Discipulus.")

    Group("Security")
    ToggleRow("Safe Browsing", "Warns you before opening pages known to be dangerous.", s.safeBrowsing, vm::setSafeBrowsing)
    Hint(
        "Sophia always checks HTTPS certificates and never lets you continue past a certificate error. " +
            "Plain http:// pages ask before loading.",
    )

    Group("Site permissions")
    Hint("Sophia never gives websites your camera, microphone or location.")
    Spacer(Modifier.height(8.dp))
    if (s.popupAllowedOrigins.isEmpty()) {
        Hint("No sites can open pop-ups without a tap.")
    } else {
        Text("Allowed to open pop-ups", style = MaterialTheme.typography.bodyLarge)
        s.popupAllowedOrigins.sorted().forEach { origin ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(origin, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.revokePopups(origin) }) { Text("Remove") }
            }
        }
    }

    when (confirm) {
        "history" -> ConfirmDialog(
            title = "Clear all history?",
            body = "This removes every page from your history on this device. Bookmarks, including AMA Discipulus, are kept.",
            confirm = "Clear",
            onConfirm = { vm.clearHistory(); browser.clearTabHistory() },
            onDismiss = { confirm = null },
        )
        "cookies" -> ConfirmDialog(
            title = "Clear cookies?",
            body = "You will be signed out of websites.",
            confirm = "Clear",
            onConfirm = { browser.clearBrowsingData(cookies = true, cache = false); actions.message("Cookies cleared") },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun Assistant(s: AppSettings, vm: SettingsViewModel) {
    val stored by vm.storedKeys.collectAsStateWithLifecycle()
    val test by vm.connectionTest.collectAsStateWithLifecycle()
    val extra = LocalSophiaColors.current
    val provider = s.aiProvider

    Hint(
        "The Study Assistant explains, summarises and quizzes you on material you choose. " +
            "It is for learning, not for graded or monitored assessments.",
    )
    Group("Provider")
    RadioGroup(AiProviderId.entries, provider, { it.label }, onPick = vm::setProvider)

    if (provider == AiProviderId.OPENAI || provider == AiProviderId.ANTHROPIC || provider == AiProviderId.COMPATIBLE) {
        Group("API key")
        val hasKey = secretNameFor(provider) in stored
        if (hasKey) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A key is saved and encrypted on this device.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.removeApiKey(provider) }) { Text("Remove") }
            }
        } else {
            var key by rememberSaveable(provider) { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Paste your ${provider.label} API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f).testTag("api_key_field"),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.saveApiKey(provider, key); key = "" }, enabled = key.isNotBlank()) { Text("Save") }
            }
        }
        Hint("Your key is encrypted with the Android Keystore, is never shown again, and is only sent to ${provider.label}.")

        if (provider == AiProviderId.COMPATIBLE) {
            Group("Service address")
            SaveField("Base URL, e.g. https://api.example.com/v1", s.compatibleBaseUrl, vm::setCompatibleBaseUrl, KeyboardType.Uri)
        }
        Group("Model")
        SaveField("Model name", s.modelFor(provider), { vm.setModel(provider, it) })
    }

    if (provider != AiProviderId.NONE) {
        Group("Answers")
        RadioGroup(ResponseDetail.entries, s.responseDetail, { it.label }, onPick = vm::setResponseDetail)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = vm::testConnection, enabled = test != ConnectionTest.Running) { Text("Test connection") }
            Spacer(Modifier.width(12.dp))
            when (val t = test) {
                ConnectionTest.Running -> Hint("Checking…")
                ConnectionTest.Ok -> Text("Connected", color = extra.success, style = MaterialTheme.typography.bodyMedium)
                is ConnectionTest.Failed -> Text(t.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                ConnectionTest.Idle -> Unit
            }
        }
    }

    Group("Privacy")
    Hint(
        "Nothing is sent until you review it and tap Send. Sophia removes text that looks like passwords, keys, " +
            "email addresses and long ID numbers first. Your provider's own privacy terms apply to what you send. " +
            "Saved notes stay on this device.",
    )
}

@Composable
private fun About() {
    val uri = LocalUriHandler.current
    Spacer(Modifier.height(8.dp))
    BrandLockup(markSize = 48.dp)
    Spacer(Modifier.height(10.dp))
    Text(SophiaConstants.TAGLINE, style = MaterialTheme.typography.titleMedium)
    Hint(SophiaConstants.NAME_MEANING)
    Spacer(Modifier.height(4.dp))
    Hint("${SophiaConstants.FORMERLY}. On Windows, the same browser is still called Lapsus.")

    Group("Version")
    Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("about_version"))

    Group("Developer")
    Text("Made by ${SophiaConstants.PUBLISHER}", style = MaterialTheme.typography.bodyLarge)
    Hint(
        "Sophia is an independent student project. It is not made or endorsed by AMA Education System; " +
            "AMA Discipulus belongs to its owners.",
    )
    TextButton(onClick = { uri.openUri(SophiaConstants.REPO_URL) }) {
        Text("Source code on GitHub")
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
    }

    Group("Privacy")
    Hint(
        "Sophia has no accounts, analytics or servers. Your history, bookmarks, downloads and notes stay on this device " +
            "and are excluded from cloud backups. Websites you visit and the AI provider you choose receive only what " +
            "you send them.",
    )

    Group("Open-source licences")
    HorizontalDivider()
    listOf(
        "AndroidX, Jetpack Compose, Material 3, Room, DataStore, Hilt" to "Apache License 2.0",
        "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
        "OkHttp" to "Apache License 2.0",
        "Anthropic Java SDK" to "MIT License",
    ).forEach { (name, licence) ->
        ListItem(headlineContent = { Text(name) }, supportingContent = { Text(licence) })
    }
}
