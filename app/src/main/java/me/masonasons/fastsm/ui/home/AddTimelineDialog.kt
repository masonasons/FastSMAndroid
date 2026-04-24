package me.masonasons.fastsm.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.masonasons.fastsm.domain.model.TimelineSpec
import me.masonasons.fastsm.domain.model.UserListInfo

private enum class Kind(val label: String) {
    Local("Local (this instance)"),
    Federated("Federated (this instance)"),
    Bookmarks("Bookmarks"),
    Favourites("Favourites"),
    MastodonList("List"),
    Hashtag("Hashtag"),
    RemoteInstance("Remote instance"),
    RemoteUser("Remote user"),
}

@Composable
fun AddTimelineDialog(
    loadLists: suspend () -> List<UserListInfo>,
    onAdd: (TimelineSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(Kind.Local) }
    var instance by remember { mutableStateOf("") }
    var acct by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var localOnly by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Lists loading state (populated when Kind.MastodonList is picked).
    var lists by remember { mutableStateOf<List<UserListInfo>?>(null) }
    var listsLoading by remember { mutableStateOf(false) }
    var selectedListId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(kind) {
        if (kind == Kind.MastodonList && lists == null && !listsLoading) {
            listsLoading = true
            lists = runCatching { loadLists() }.getOrDefault(emptyList())
            listsLoading = false
        }
    }

    val buildSpec: () -> TimelineSpec? = {
        when (kind) {
            Kind.Local -> TimelineSpec.LocalPublic
            Kind.Federated -> TimelineSpec.FederatedPublic
            Kind.Bookmarks -> TimelineSpec.Bookmarks
            Kind.Favourites -> TimelineSpec.Favourites
            Kind.MastodonList -> {
                val chosen = lists?.firstOrNull { it.id == selectedListId }
                if (chosen == null) {
                    error = "Pick a list"
                    null
                } else TimelineSpec.UserList(chosen.id, chosen.title)
            }
            Kind.Hashtag -> {
                val trimmedTag = tag.trim().removePrefix("#")
                if (trimmedTag.isBlank()) {
                    error = "Enter a hashtag"
                    null
                } else TimelineSpec.Hashtag(trimmedTag)
            }
            Kind.RemoteInstance -> {
                val normalized = normalizeInstance(instance)
                if (normalized.isBlank()) {
                    error = "Enter an instance URL"
                    null
                } else TimelineSpec.RemoteInstance(normalized, localOnly)
            }
            Kind.RemoteUser -> {
                val normalized = normalizeInstance(instance)
                val trimmedAcct = acct.trim().removePrefix("@")
                if (normalized.isBlank() || trimmedAcct.isBlank()) {
                    error = "Enter both an instance and a handle"
                    null
                } else TimelineSpec.RemoteUser(normalized, trimmedAcct)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add timeline") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 500.dp),
            ) {
                Kind.entries.forEach { k ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { kind = k; error = null },
                    ) {
                        RadioButton(selected = kind == k, onClick = { kind = k; error = null })
                        Text(k.label)
                    }
                }
                when (kind) {
                    Kind.Local, Kind.Federated, Kind.Bookmarks, Kind.Favourites -> {
                        Text(
                            "Adds the ${kind.label.lowercase()} feed to the tab bar.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Kind.MastodonList -> ListPicker(
                        lists = lists,
                        loading = listsLoading,
                        selectedId = selectedListId,
                        onSelect = { selectedListId = it; error = null },
                    )
                    Kind.Hashtag -> OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it; error = null },
                        label = { Text("Hashtag (no #)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Kind.RemoteInstance -> {
                        OutlinedTextField(
                            value = instance,
                            onValueChange = { instance = it; error = null },
                            label = { Text("Instance (e.g. fosstodon.org)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { localOnly = !localOnly },
                        ) {
                            Checkbox(checked = localOnly, onCheckedChange = { localOnly = it })
                            Text("Only posts from this instance (local)")
                        }
                    }
                    Kind.RemoteUser -> {
                        OutlinedTextField(
                            value = instance,
                            onValueChange = { instance = it; error = null },
                            label = { Text("Instance (e.g. mastodon.social)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = acct,
                            onValueChange = { acct = it; error = null },
                            label = { Text("Handle (e.g. @alice)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val spec = buildSpec()
                if (spec != null) onAdd(spec)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ListPicker(
    lists: List<UserListInfo>?,
    loading: Boolean,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    when {
        loading -> CircularProgressIndicator()
        lists == null -> Unit
        lists.isEmpty() -> Text(
            "No lists on this account. Create one on your instance first.",
            style = MaterialTheme.typography.bodyLarge,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lists.forEach { list ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(list.id) },
                ) {
                    RadioButton(
                        selected = list.id == selectedId,
                        onClick = { onSelect(list.id) },
                    )
                    Text(list.title)
                }
            }
        }
    }
}

private fun normalizeInstance(raw: String): String {
    val trimmed = raw.trim().removeSuffix("/")
    return when {
        trimmed.isEmpty() -> trimmed
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        else -> "https://$trimmed"
    }
}
