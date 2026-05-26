package com.pockettavern.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun ScanloreConfirmDialog(
    entries: List<String>,
    isLoading: Boolean,
    error: String?,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val checked = remember(entries) { mutableStateListOf(*Array(entries.size) { true }) }
    val edited  = remember(entries) { mutableStateListOf(*entries.toTypedArray()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scanlore Results") },
        text = {
            Column {
                when {
                    isLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Scanning conversation...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    error != null -> {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    entries.isEmpty() -> {
                        Text(
                            "Nothing notable found in recent messages.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    else -> {
                        Text(
                            "Uncheck or edit entries before adding to World Book.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(entries) { i, _ ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = checked.getOrElse(i) { true },
                                        onCheckedChange = { if (i < checked.size) checked[i] = it },
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    OutlinedTextField(
                                        value = edited.getOrElse(i) { "" },
                                        onValueChange = { if (i < edited.size) edited[i] = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        minLines = 1,
                                        maxLines = 4,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Sentences
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isLoading && error == null && entries.isNotEmpty()) {
                TextButton(onClick = {
                    val selected = entries.indices
                        .filter { checked.getOrElse(it) { true } }
                        .map { edited.getOrElse(it) { entries[it] }.trim() }
                        .filter { it.isNotBlank() }
                    onConfirm(selected)
                }) { Text("Add to Book") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
