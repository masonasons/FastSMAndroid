package me.masonasons.fastsm.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import me.masonasons.fastsm.domain.model.Account

/**
 * Visual account picker. Tapping the row opens a DropdownMenu that lists
 * every account plus "Add account" and "Log out" — same set of options the
 * TalkBack customActions menu exposes, so sighted and non-sighted users hit
 * the same flow.
 */
@Composable
fun AccountPicker(
    accounts: List<Account>,
    activeAccountId: Long?,
    onSwitch: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onLogOut: () -> Unit,
    onOpenAccountSettings: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = accounts.firstOrNull { it.id == activeAccountId } ?: accounts.firstOrNull()
    val others = accounts.filter { it.id != active?.id }
    var menuOpen by remember { mutableStateOf(false) }

    val spoken = buildString {
        append("Active account: ")
        append(active?.let { "${it.displayName} (${it.label}) on ${it.platform.displayName}" } ?: "none")
        if (accounts.size > 1) {
            append(". Tap to switch or use actions.")
        } else {
            append(". Tap to add another account.")
        }
    }

    val menuActions = buildList {
        others.forEach { account ->
            add(
                MenuAction("Switch to ${account.displayName} (${account.label})") {
                    onSwitch(account.id)
                }
            )
        }
        if (active != null) {
            add(MenuAction("Settings for ${active.label}") { onOpenAccountSettings(active.id) })
        }
        add(MenuAction("Add account") { onAddAccount() })
        if (active != null) {
            add(MenuAction("Log out ${active.label}") { onLogOut() })
        }
    }
    val actions = menuActions.toAccessibilityActions()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { menuOpen = true }
            .clearAndSetSemantics {
                contentDescription = spoken
                customActions = actions
                onClick { menuOpen = true; true }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (active == null) {
            Text("No account selected", style = MaterialTheme.typography.labelLarge)
        } else {
            Text(active.displayName, style = MaterialTheme.typography.labelLarge)
            Text(
                "${active.label} \u00b7 ${active.platform.displayName}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            others.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(account.displayName, style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${account.label} \u00b7 ${account.platform.displayName}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onSwitch(account.id); menuOpen = false },
                )
            }
            if (others.isNotEmpty()) HorizontalDivider()
            if (active != null) {
                DropdownMenuItem(
                    text = { Text("Settings for ${active.label}") },
                    onClick = { onOpenAccountSettings(active.id); menuOpen = false },
                )
            }
            DropdownMenuItem(
                text = { Text("Add account") },
                onClick = { onAddAccount(); menuOpen = false },
            )
            if (active != null) {
                DropdownMenuItem(
                    text = { Text("Log out ${active.label}") },
                    onClick = { onLogOut(); menuOpen = false },
                )
            }
        }
    }
}
