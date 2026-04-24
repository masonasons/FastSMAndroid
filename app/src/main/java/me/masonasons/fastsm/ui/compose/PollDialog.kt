package me.masonasons.fastsm.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.masonasons.fastsm.domain.model.PollSpec

/** Options users can pick for poll duration, mirroring the desktop client. */
private val durationChoices = listOf(
    "5 minutes" to 300,
    "30 minutes" to 1_800,
    "1 hour" to 3_600,
    "6 hours" to 21_600,
    "12 hours" to 43_200,
    "1 day" to 86_400,
    "3 days" to 259_200,
    "7 days" to 604_800,
)

@Composable
fun PollDialog(
    initial: PollSpec?,
    onDismiss: () -> Unit,
    onConfirm: (PollSpec) -> Unit,
) {
    // Two options minimum, four max. Seed with whatever was already configured.
    val initialOptions = initial?.options ?: listOf("", "")
    var options by remember { mutableStateOf(initialOptions.ifEmpty { listOf("", "") }) }
    var durationSec by remember { mutableStateOf(initial?.expiresInSec ?: 86_400) }
    var multiple by remember { mutableStateOf(initial?.multiple ?: false) }
    var hideTotals by remember { mutableStateOf(initial?.hideTotals ?: false) }
    var durationExpanded by remember { mutableStateOf(false) }

    val canConfirm = options.count { it.isNotBlank() } >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add poll" else "Edit poll") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { index, value ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newValue ->
                                options = options.toMutableList().also { it[index] = newValue }
                            },
                            label = { Text("Option ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        if (options.size > 2) {
                            IconButton(onClick = {
                                options = options.toMutableList().also { it.removeAt(index) }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove option ${index + 1}")
                            }
                        }
                    }
                }
                if (options.size < 4) {
                    OutlinedButton(onClick = { options = options + "" }) { Text("Add option") }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Duration:")
                    Button(onClick = { durationExpanded = true }) {
                        Text(durationChoices.firstOrNull { it.second == durationSec }?.first ?: "1 day")
                    }
                    DropdownMenu(
                        expanded = durationExpanded,
                        onDismissRequest = { durationExpanded = false },
                    ) {
                        durationChoices.forEach { (label, seconds) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { durationSec = seconds; durationExpanded = false },
                            )
                        }
                    }
                }

                TogglePollRow(
                    label = "Allow multiple choices",
                    checked = multiple,
                    onChange = { multiple = it },
                )
                TogglePollRow(
                    label = "Hide results until poll ends",
                    checked = hideTotals,
                    onChange = { hideTotals = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        PollSpec(
                            options = options.map { it.trim() }.filter { it.isNotEmpty() },
                            expiresInSec = durationSec,
                            multiple = multiple,
                            hideTotals = hideTotals,
                        )
                    )
                },
            ) { Text(if (initial == null) "Add poll" else "Update poll") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TogglePollRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}
