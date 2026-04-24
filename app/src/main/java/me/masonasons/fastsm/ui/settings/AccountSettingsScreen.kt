package me.masonasons.fastsm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import me.masonasons.fastsm.data.feedback.FeedbackManager
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.util.FolderLauncher

/**
 * Per-account settings. Today: the soundpack binding — each account can have
 * its own pack so different servers can sound distinct. Kept as a simple
 * screen driven directly by [AccountRepository] / [FeedbackManager] rather
 * than a dedicated ViewModel, since there's exactly one setting to manage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    accountId: Long,
    accountRepository: AccountRepository,
    feedback: FeedbackManager,
    onClose: () -> Unit,
) {
    val accounts by accountRepository.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val account = accounts.firstOrNull { it.id == accountId }
    val packs by feedback.prefs.accountSoundpacks.collectAsStateWithLifecycle()
    val currentPack = packs[accountId] ?: "default"
    val availablePacks = remember { feedback.availablePacks() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(account?.let { "${it.label} settings" } ?: "Account settings")
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (account == null) {
            Text(
                "Account no longer available",
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            )
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item("header") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(account.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${account.label} \u00b7 ${account.platform.displayName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            item("pack") {
                AccountSoundpackRow(
                    current = currentPack,
                    available = availablePacks,
                    onSelect = { pack -> feedback.prefs.setAccountSoundpack(accountId, pack) },
                )
            }
            item("open_folder") {
                OpenSoundsFolderRow()
            }
        }
    }
}

@Composable
private fun OpenSoundsFolderRow() {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Soundpacks folder", style = MaterialTheme.typography.titleLarge)
            Text(
                "Open the external files directory that holds soundpack folders. Path is copied to clipboard if the intent doesn't resolve.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = {
            val dir = context.getExternalFilesDir("sounds") ?: return@Button
            FolderLauncher.open(context, dir)
        }) { Text("Open") }
    }
}

@Composable
private fun AccountSoundpackRow(
    current: String,
    available: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Soundpack", style = MaterialTheme.typography.titleLarge)
            Text(
                "Place .ogg folders under the app's external files directory, one per pack.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { expanded = true }) { Text(current) }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            available.forEach { pack ->
                DropdownMenuItem(
                    text = { Text(pack) },
                    onClick = { onSelect(pack); expanded = false },
                )
            }
        }
    }
}
