package me.masonasons.fastsm.ui.userlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.Relationship
import me.masonasons.fastsm.domain.model.UniversalUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    viewModel: UserListViewModel,
    onOpenProfile: (String) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                UserListEvent.Closed -> onClose()
            }
        }
    }

    val title = buildString {
        append(if (state.kind == UserListKind.Followers) "Followers" else "Following")
        state.targetUser?.let { append(" of @").append(it.acct) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort (${state.sortMode.label})",
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            val sortOptions = buildList {
                                add(SortMode.Default)
                                add(SortMode.FollowingFirst)
                                if (state.platform == PlatformType.MASTODON) {
                                    add(SortMode.BoostsHiddenFirst)
                                }
                                add(SortMode.MutedFirst)
                                add(SortMode.BlockedFirst)
                            }
                            sortOptions.forEach { mode ->
                                val active = state.sortMode == mode
                                DropdownMenuItem(
                                    text = { Text((if (active) "✓ " else "  ") + mode.label) },
                                    onClick = {
                                        sortMenuOpen = false
                                        viewModel.setSortMode(mode)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.setEditMode(!state.editMode) }) {
                        if (state.editMode) {
                            Icon(Icons.Filled.Close, contentDescription = "Exit edit mode")
                        } else {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit (multi-select)")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.editMode) {
                BottomAppBar {
                    TextButton(
                        onClick = viewModel::selectAll,
                        enabled = !state.batchInProgress,
                    ) { Text("Select all") }
                    TextButton(
                        onClick = viewModel::clearSelection,
                        enabled = !state.batchInProgress && state.selected.isNotEmpty(),
                    ) { Text("Clear") }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = viewModel::batchFollow,
                        enabled = !state.batchInProgress && state.selected.isNotEmpty(),
                    ) { Text("Follow ${state.selected.size}") }
                    TextButton(
                        onClick = viewModel::batchUnfollow,
                        enabled = !state.batchInProgress && state.selected.isNotEmpty(),
                    ) { Text("Unfollow ${state.selected.size}") }
                }
            }
        },
    ) { innerPadding ->
        when {
            state.loading && state.users.isEmpty() -> LoadingFill(Modifier.padding(innerPadding))
            state.error != null && state.users.isEmpty() -> ErrorFill(
                Modifier.padding(innerPadding),
                message = state.error.orEmpty(),
                onRetry = viewModel::refresh,
            )
            state.users.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No ${if (state.kind == UserListKind.Followers) "followers" else "follows"} yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> {
                val listState = rememberLazyListState()
                val displayedUsers = remember(state.users, state.relationships, state.sortMode) {
                    applySort(state.users, state.relationships, state.sortMode)
                }
                val shouldLoadMore by remember(state) {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            ?: return@derivedStateOf false
                        last >= state.users.size - 4 && !state.loadingMore && !state.exhausted
                    }
                }
                LaunchedEffect(listState, state.users.size, state.loadingMore, state.exhausted) {
                    snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    items(displayedUsers, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            relationship = state.relationships[user.id],
                            platformType = state.platform,
                            editMode = state.editMode,
                            selected = user.id in state.selected,
                            onTap = {
                                if (state.editMode) viewModel.toggleSelection(user.id)
                                else onOpenProfile(user.id)
                            },
                            onToggleFollow = { viewModel.toggleFollow(user) },
                            onToggleShowReblogs = { viewModel.setShowReblogs(user, it) },
                            onToggleMute = { viewModel.toggleMute(user) },
                            onToggleBlock = { viewModel.toggleBlock(user) },
                            onToggleSelection = { viewModel.toggleSelection(user.id) },
                        )
                        HorizontalDivider()
                    }
                    item(key = "__footer__") {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                state.loadingMore -> CircularProgressIndicator()
                                state.exhausted -> Text(
                                    "End of list",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.error?.takeIf { state.users.isNotEmpty() }?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::refresh) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = { /* swallow */ }) { Text("Dismiss") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserRow(
    user: UniversalUser,
    relationship: Relationship?,
    platformType: PlatformType,
    editMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleShowReblogs: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val following = relationship?.following == true
    val muting = relationship?.muting == true
    val blocking = relationship?.blocking == true
    val showReblogs = relationship?.showReblogs ?: true

    val followAction = if (following) "Unfollow" else "Follow"
    val muteAction = if (muting) "Unmute" else "Mute"
    val blockAction = if (blocking) "Unblock" else "Block"
    val boostsAction = if (showReblogs) "Hide boosts" else "Show boosts"
    val showBoostsToggle = platformType == PlatformType.MASTODON && following

    val spoken = buildString {
        append(user.displayName)
        append(", @").append(user.acct)
        if (user.bot) append(", bot")
        if (user.locked) append(", locked")
        if (following) append(", you follow them")
        if (muting) append(", muted")
        if (blocking) append(", blocked")
        if (showBoostsToggle && !showReblogs) append(", boosts hidden")
        if (editMode) {
            append(", ").append(if (selected) "selected" else "not selected")
        }
    }

    val actions = buildList {
        add(CustomAccessibilityAction(if (editMode) "Toggle selection" else "Open profile") {
            onTap(); true
        })
        if (!editMode) {
            add(CustomAccessibilityAction(followAction) { onToggleFollow(); true })
            if (showBoostsToggle) {
                add(CustomAccessibilityAction(boostsAction) {
                    onToggleShowReblogs(!showReblogs); true
                })
            }
            add(CustomAccessibilityAction(muteAction) { onToggleMute(); true })
            add(CustomAccessibilityAction(blockAction) { onToggleBlock(); true })
        } else {
            add(CustomAccessibilityAction("Toggle selection") { onToggleSelection(); true })
        }
    }

    // Long-press opens the same overflow menu as the per-row 3-dot button,
    // matching StatusItem's pattern so devices without a11y custom-action
    // support still have a way to reach Follow/Mute/Block/etc.
    val rowModifier = if (editMode) {
        Modifier.clickable(onClick = onTap)
    } else {
        Modifier.combinedClickable(
            onClick = onTap,
            onLongClick = { menuOpen = true },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .clearAndSetSemantics {
                contentDescription = spoken
                customActions = actions
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                "@${user.acct}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (user.note.isNotBlank()) {
                Text(
                    me.masonasons.fastsm.platform.mastodon.HtmlStrip.toPlainText(user.note),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!editMode) {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.clearAndSetSemantics { },
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Actions for ${user.displayName}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(followAction) },
                        onClick = { menuOpen = false; onToggleFollow() },
                    )
                    if (showBoostsToggle) {
                        DropdownMenuItem(
                            text = { Text(boostsAction) },
                            onClick = { menuOpen = false; onToggleShowReblogs(!showReblogs) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(muteAction) },
                        onClick = { menuOpen = false; onToggleMute() },
                    )
                    DropdownMenuItem(
                        text = { Text(blockAction) },
                        onClick = { menuOpen = false; onToggleBlock() },
                    )
                }
            }
        } else if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * Pull users matching the current sort criterion to the top of the list,
 * preserving their original (server-defined) relative order. Stable so the
 * list doesn't shuffle when individual relationships flip mid-session.
 */
private fun applySort(
    users: List<UniversalUser>,
    relationships: Map<String, Relationship>,
    mode: SortMode,
): List<UniversalUser> {
    if (mode == SortMode.Default) return users
    val matches: (UniversalUser) -> Boolean = when (mode) {
        SortMode.Default -> return users
        SortMode.FollowingFirst -> { user -> relationships[user.id]?.following == true }
        SortMode.BoostsHiddenFirst -> { user ->
            val rel = relationships[user.id]
            rel?.following == true && rel.showReblogs == false
        }
        SortMode.MutedFirst -> { user -> relationships[user.id]?.muting == true }
        SortMode.BlockedFirst -> { user -> relationships[user.id]?.blocking == true }
    }
    return users.sortedByDescending { matches(it) }
}

@Composable
private fun LoadingFill(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorFill(modifier: Modifier, message: String, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Could not load users", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}
