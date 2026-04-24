package me.masonasons.fastsm.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.masonasons.fastsm.domain.model.UniversalUser

/**
 * User-picker for inserting an @mention into compose. Typing filters results
 * via the platform search API (debounced in the ViewModel); selecting an entry
 * returns that user's acct so the caller can insert `@acct` at the cursor.
 */
@Composable
fun MentionPickerDialog(
    query: String,
    results: List<UniversalUser>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (UniversalUser) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onQueryChange(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mention a user") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    searching -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("Searching\u2026", style = MaterialTheme.typography.bodyLarge)
                    }
                    query.isNotBlank() && results.isEmpty() -> Text(
                        "No matches",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(results, key = { it.id }) { user ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            ) {
                                TextButton(
                                    onClick = { onSelect(user) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            user.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            "@${user.acct}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
