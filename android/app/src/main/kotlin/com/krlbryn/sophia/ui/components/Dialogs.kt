package com.krlbryn.sophia.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Title + URL editor used for bookmarks and portal shortcuts. [onSave] returns false when the URL is invalid. */
@Composable
fun TitleUrlDialog(
    heading: String,
    initialTitle: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String) -> Boolean,
    extraContent: (@Composable () -> Unit)? = null,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    url,
                    { url = it; error = null },
                    label = { Text("Address") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "Secure (https) addresses only") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                )
                extraContent?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onSave(title, url)) onDismiss() else error = "Enter a valid https address" }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
