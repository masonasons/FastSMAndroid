package me.masonasons.fastsm.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import me.masonasons.fastsm.domain.model.TimelineFilter

/**
 * Per-timeline filter editor. Text search is substring-matched across post
 * body + author + CW; toggles hide whole categories. Applied live — the
 * parent observes the current filter from [HomeViewModel] and passes it
 * straight into [matches] when rendering the list.
 */
@Composable
fun FilterDialog(
    initial: TimelineFilter,
    onDismiss: () -> Unit,
    onApply: (TimelineFilter) -> Unit,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf(initial.query) }
    var hideBoosts by remember { mutableStateOf(initial.hideBoosts) }
    var hideReplies by remember { mutableStateOf(initial.hideReplies) }
    var hideOwn by remember { mutableStateOf(initial.hideOwn) }
    var mediaOnly by remember { mutableStateOf(initial.mediaOnly) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter timeline") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow("Hide boosts", hideBoosts) { hideBoosts = it }
                ToggleRow("Hide replies", hideReplies) { hideReplies = it }
                ToggleRow("Hide my own posts", hideOwn) { hideOwn = it }
                ToggleRow("Media only", mediaOnly) { mediaOnly = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    TimelineFilter(
                        query = query,
                        hideBoosts = hideBoosts,
                        hideReplies = hideReplies,
                        hideOwn = hideOwn,
                        mediaOnly = mediaOnly,
                    )
                )
            }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onClear(); onDismiss() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ToggleRow(
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
