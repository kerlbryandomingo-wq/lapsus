package com.krlbryn.sophia.ui.assistant

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krlbryn.sophia.core.AiProviderId
import com.krlbryn.sophia.core.Confidence
import com.krlbryn.sophia.core.Formatting
import com.krlbryn.sophia.core.LayoutClass
import com.krlbryn.sophia.core.StudyAnswer
import com.krlbryn.sophia.core.StudyMode
import com.krlbryn.sophia.data.db.StudyNote
import com.krlbryn.sophia.ui.components.NoticeBand
import com.krlbryn.sophia.ui.navigation.AppActions
import com.krlbryn.sophia.ui.navigation.Destination
import com.krlbryn.sophia.ui.theme.LocalSophiaColors

@Composable
fun AssistantScreen(layout: LayoutClass, actions: AppActions, vm: AssistantViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    var showNotes by rememberSaveable { mutableStateOf(false) }
    val expanded = layout == LayoutClass.EXPANDED

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Study Assistant") },
                actions = {
                    if (!expanded) {
                        TextButton(onClick = { showNotes = true }) {
                            Icon(Icons.Outlined.NoteAlt, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Saved (${notes.size})")
                        }
                    }
                },
            )
        },
    ) { inner ->
        Row(Modifier.padding(inner).fillMaxSize().imePadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (layout == LayoutClass.COMPACT) 16.dp else 28.dp, vertical = 8.dp),
            ) {
                Column(Modifier.widthIn(max = 820.dp)) {
                    if (!status.configured) {
                        NoticeBand(
                            "Choose an AI provider and add your own API key to get answers.",
                            modifier = Modifier.clip(MaterialTheme.shapes.medium),
                            actionLabel = "Settings",
                            onAction = { actions.navigate(Destination.SETTINGS) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    PrivacyNote(status.provider)
                    Spacer(Modifier.height(12.dp))
                    ModePicker(ui.mode, enabled = ui.stage == AssistantStage.EDIT, onPick = vm::setMode)
                    Spacer(Modifier.height(12.dp))
                    when (ui.stage) {
                        AssistantStage.EDIT -> Editor(ui, vm)
                        AssistantStage.REVIEW -> Review(ui, status, vm)
                        AssistantStage.LOADING -> Loading(vm::cancel)
                        AssistantStage.RESULT -> ui.answer?.let { AnswerView(it, ui.saved, vm, actions) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (expanded) {
                VerticalDivider()
                NotesList(notes, onOpen = vm::openNote, onDelete = vm::deleteNote, modifier = Modifier.width(320.dp).fillMaxHeight())
            }
        }
    }

    if (showNotes) {
        ModalBottomSheet(onDismissRequest = { showNotes = false }) {
            NotesList(notes, onOpen = { vm.openNote(it); showNotes = false }, onDelete = vm::deleteNote, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp))
        }
    }
}

@Composable
private fun PrivacyNote(provider: AiProviderId) {
    var open by rememberSaveable { mutableStateOf(false) }
    val extra = LocalSophiaColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = extra.aiContainer),
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { open = !open },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = extra.ai, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Privacy and fair use", style = MaterialTheme.typography.titleSmall, color = extra.ai, modifier = Modifier.weight(1f))
                Text(if (open) "Hide" else "Details", style = MaterialTheme.typography.labelMedium, color = extra.ai)
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                val where = when (provider) {
                    AiProviderId.NONE -> "the AI provider you choose in Settings"
                    AiProviderId.DEMO -> "nowhere: demo mode works offline"
                    else -> provider.label
                }
                Text(
                    "• Only the text you review is sent, to $where, using your own API key. Sophia has no servers of its own.\n" +
                        "• Before you review, Sophia removes things that look like passwords, keys, email addresses and long ID or phone numbers.\n" +
                        "• Your API key is encrypted with the Android Keystore and never leaves this device except to authenticate with your provider.\n" +
                        "• Saved notes stay on this device.\n" +
                        "• The assistant is for learning and understanding. Don't use it during graded or monitored assessments, and follow your course's rules.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModePicker(selected: StudyMode, enabled: Boolean, onPick: (StudyMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StudyMode.entries.forEach { m ->
            FilterChip(selected = m == selected, enabled = enabled, onClick = { onPick(m) }, label = { Text(m.label) })
        }
    }
}

@Composable
private fun Editor(ui: AssistantUi, vm: AssistantViewModel) {
    val clipboard = LocalClipboardManager.current
    ui.source?.let {
        Text("From: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
    }
    OutlinedTextField(
        value = ui.input,
        onValueChange = vm::setInput,
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).testTag("assistant_input"),
        label = { Text("Study material") },
        placeholder = { Text("Paste a lesson, a reading, or a practice question…") },
        supportingText = { Text("${ui.input.length} characters") },
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = ui.note,
        onValueChange = vm::setNote,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("What do you want to understand? (optional)") },
    )
    ui.error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { clipboard.getText()?.text?.let(vm::setInput) }) {
            Icon(Icons.Outlined.ContentPaste, null)
            Spacer(Modifier.width(6.dp))
            Text("Paste")
        }
        Button(onClick = vm::review, enabled = ui.input.isNotBlank(), modifier = Modifier.testTag("assistant_review")) { Text("Review before sending") }
    }
}

@Composable
private fun Review(ui: AssistantUi, status: ProviderStatus, vm: AssistantViewModel) {
    val prepared = ui.prepared ?: return
    Text("Review what will be sent", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        when (status.provider) {
            AiProviderId.NONE -> "No provider is selected yet."
            AiProviderId.DEMO -> "Demo mode: nothing leaves your device."
            else -> "This exact text goes to ${status.provider.label}."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    if (prepared.redactions > 0) {
        NoticeBand("Removed ${prepared.redactions} item(s) that looked like private information.", modifier = Modifier.clip(MaterialTheme.shapes.small))
        Spacer(Modifier.height(6.dp))
    }
    if (prepared.truncated) {
        NoticeBand("The material was long, so only the first ${prepared.text.length} characters will be sent.", warning = true, modifier = Modifier.clip(MaterialTheme.shapes.small))
        Spacer(Modifier.height(6.dp))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth().testTag("assistant_preview")) {
        Text(
            prepared.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(12.dp),
        )
    }
    ui.error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = vm::backToEdit) { Text("Edit") }
        Button(onClick = vm::send, enabled = status.configured, modifier = Modifier.testTag("assistant_send")) {
            Icon(Icons.Outlined.AutoAwesome, null)
            Spacer(Modifier.width(6.dp))
            Text("Send")
        }
    }
}

@Composable
private fun Loading(onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Text("Thinking it through…", modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun AnswerView(answer: StudyAnswer, saved: Boolean, vm: AssistantViewModel, actions: AppActions) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val extra = LocalSophiaColors.current
    Column(Modifier.testTag("assistant_answer")) {
        Text(answer.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        val (label, color) = when (answer.confidence) {
            Confidence.HIGH -> "High confidence" to extra.success
            Confidence.MEDIUM -> "Medium confidence" to extra.warning
            Confidence.LOW -> "Low confidence" to MaterialTheme.colorScheme.error
        }
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        answer.warning?.let {
            Spacer(Modifier.height(8.dp))
            NoticeBand(it, warning = true, modifier = Modifier.clip(MaterialTheme.shapes.small))
        }
        Spacer(Modifier.height(12.dp))
        Text(answer.explanation, style = MaterialTheme.typography.bodyLarge)
        if (answer.keyPoints.isNotEmpty()) {
            Section("Key points")
            answer.keyPoints.forEach { Bullet(it) }
        }
        if (answer.flashcards.isNotEmpty()) {
            Section("Flashcards")
            Text("Tap a card to flip it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            answer.flashcards.forEach { card ->
                var flipped by remember(card) { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (flipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { flipped = !flipped },
                ) {
                    Text(
                        if (flipped) card.back else card.front,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (flipped) FontWeight.Normal else FontWeight.SemiBold,
                    )
                }
            }
        }
        if (answer.practiceQuestions.isNotEmpty()) {
            Section("Practice questions")
            answer.practiceQuestions.forEachIndexed { i, q ->
                var reveal by remember(q) { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${i + 1}. ${q.question}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        q.choices.forEachIndexed { j, c -> Text("${'A' + j}. $c", style = MaterialTheme.typography.bodyMedium) }
                        if (reveal) {
                            Spacer(Modifier.height(6.dp))
                            Text("Answer: ${q.answer}", color = extra.success, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(q.rationale, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            TextButton(onClick = { reveal = true }) { Text("Show answer") }
                        }
                    }
                }
            }
        }
        if (answer.missingInformation.isNotEmpty()) {
            Section("What's missing")
            answer.missingInformation.forEach { Bullet(it) }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "AI answers can be wrong. Check them against your course materials.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { clipboard.setText(AnnotatedString(answer.toPlainText())); actions.message("Copied") }) {
                Icon(Icons.Outlined.ContentCopy, "Copy answer")
            }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, answer.toPlainText())
                context.startActivity(Intent.createChooser(send, null))
            }) { Icon(Icons.Outlined.Share, "Share answer") }
            OutlinedButton(onClick = vm::save, enabled = !saved) {
                Icon(Icons.Outlined.Save, null)
                Spacer(Modifier.width(6.dp))
                Text(if (saved) "Saved" else "Save to notes")
            }
            Button(onClick = vm::startOver) { Text("New") }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.padding(top = 9.dp).size(6.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NotesList(notes: List<StudyNote>, onOpen: (StudyNote) -> Unit, onDelete: (StudyNote) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text("Saved notes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        HorizontalDivider()
        if (notes.isEmpty()) {
            Text(
                "Answers you save appear here, on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn {
                items(notes, key = { it.id }) { n ->
                    ListItem(
                        headlineContent = { Text(n.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            val mode = StudyMode.entries.firstOrNull { it.name == n.mode }?.label ?: ""
                            Text("$mode · ${Formatting.relativeTime(n.createdAt, System.currentTimeMillis())}")
                        },
                        trailingContent = { IconButton(onClick = { onDelete(n) }) { Icon(Icons.Outlined.Delete, "Delete note") } },
                        modifier = Modifier.clickable { onOpen(n) },
                    )
                }
            }
        }
    }
}
