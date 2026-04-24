package me.masonasons.fastsm.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.masonasons.fastsm.domain.model.FollowState
import me.masonasons.fastsm.domain.model.UniversalMedia
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.domain.model.UserListInfo
import me.masonasons.fastsm.platform.mastodon.HtmlStrip

import me.masonasons.fastsm.ui.home.StatusItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onReply: (UniversalStatus) -> Unit,
    onOpenThread: (UniversalStatus) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (UniversalMedia) -> Unit,
    onOpenLink: (String) -> Unit,
    onCompose: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showListsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ProfileEvent.Closed -> onClose()
                ProfileEvent.OpenCompose -> onCompose()
            }
        }
    }

    if (showListsDialog) {
        ManageListsDialog(
            lists = state.availableLists,
            memberships = state.listMemberships,
            onToggle = viewModel::toggleList,
            onDismiss = { showListsDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.user?.let { "@${it.acct}" } ?: "Profile")
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close profile")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh profile")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading && state.user == null -> LoadingFill(Modifier.padding(innerPadding))
            state.error != null && state.user == null -> ErrorFill(
                Modifier.padding(innerPadding),
                state.error.orEmpty(),
                viewModel::refresh,
            )
            state.user == null -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text("Profile unavailable") }
            else -> {
                val listState = rememberLazyListState()
                val shouldLoadMore by remember(state) {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            ?: return@derivedStateOf false
                        last >= state.statuses.size - 4 && !state.loadingMore && !state.exhausted
                    }
                }
                LaunchedEffect(listState, state.statuses.size, state.loadingMore, state.exhausted) {
                    snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    item(key = "__header__") {
                        ProfileHeader(
                            user = state.user!!,
                            followState = state.relationship?.followState ?: FollowState.NOT_FOLLOWING,
                            followedBy = state.relationship?.followedBy == true,
                            isMe = state.isMe,
                            isRemoteMastodon = state.isRemoteMastodonUser,
                            availableLists = state.availableLists,
                            listMemberships = state.listMemberships,
                            onToggleFollow = viewModel::toggleFollow,
                            onOpenAsTimeline = viewModel::openAsTimeline,
                            onOpenInstanceTimeline = viewModel::openInstanceTimeline,
                            onOpenRemoteUserTimeline = viewModel::openRemoteUserTimeline,
                            onToggleList = viewModel::toggleList,
                            onShowListsDialog = { showListsDialog = true },
                        )
                        HorizontalDivider()
                    }
                    items(state.statuses, key = { it.id }) { status ->
                        StatusItem(
                            status = status,
                            myUserId = state.myUserId,
                            onReply = onReply,
                            onFavourite = viewModel::onFavourite,
                            onBoost = viewModel::onBoost,
                            onBookmark = viewModel::onBookmark,
                            onDelete = viewModel::onDelete,
                            onEdit = viewModel::prepareEdit,
                            onQuote = viewModel::prepareQuote,
                            onOpenThread = onOpenThread,
                            onOpenProfile = onOpenProfile,
                            onOpenMedia = onOpenMedia,
                            onOpenLink = onOpenLink,
                        )
                    }
                    item(key = "__footer__") {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            when {
                                state.loadingMore -> CircularProgressIndicator()
                                state.exhausted && state.statuses.isNotEmpty() -> Text(
                                    "End of posts",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                state.statuses.isEmpty() -> Text(
                                    "No posts yet",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeader(
    user: UniversalUser,
    followState: FollowState,
    followedBy: Boolean,
    isMe: Boolean,
    isRemoteMastodon: Boolean,
    availableLists: List<UserListInfo>,
    listMemberships: Set<String>,
    onToggleFollow: () -> Unit,
    onOpenAsTimeline: () -> Unit,
    onOpenInstanceTimeline: () -> Unit,
    onOpenRemoteUserTimeline: () -> Unit,
    onToggleList: (String) -> Unit,
    onShowListsDialog: () -> Unit,
) {
    val bio = HtmlStrip.toPlainText(user.note)
    val spoken = buildString {
        append(user.displayName)
        append(" (@").append(user.acct).append(")")
        if (user.bot) append(", bot account")
        if (user.locked) append(", locked account")
        append(". ")
        if (bio.isNotBlank()) append(bio).append(". ")
        append(user.followersCount).append(" follower")
        if (user.followersCount != 1) append("s")
        append(", ")
        append(user.followingCount).append(" following, ")
        append(user.statusesCount).append(" post")
        if (user.statusesCount != 1) append("s")
        append(". ")
        if (isMe) append("This is you.")
        else append(describeFollow(followState, followedBy))
    }

    val actions = buildList {
        if (!isMe) {
            val label = when (followState) {
                FollowState.NOT_FOLLOWING -> "Follow"
                FollowState.REQUESTED -> "Cancel follow request"
                FollowState.FOLLOWING -> "Unfollow"
            }
            add(CustomAccessibilityAction(label) { onToggleFollow(); true })
        }
        add(CustomAccessibilityAction("Open user timeline") { onOpenAsTimeline(); true })
        if (isRemoteMastodon) {
            val host = user.acct.removePrefix("@").substringAfter('@')
            add(CustomAccessibilityAction("Open $host timeline") {
                onOpenInstanceTimeline(); true
            })
            add(CustomAccessibilityAction("Open remote user timeline on $host") {
                onOpenRemoteUserTimeline(); true
            })
        }
        // One toggle action per list; cheapest a11y surface for list management.
        if (!isMe) {
            availableLists.forEach { list ->
                val isMember = list.id in listMemberships
                add(
                    CustomAccessibilityAction(
                        if (isMember) "Remove from ${list.title}" else "Add to ${list.title}"
                    ) { onToggleList(list.id); true }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                contentDescription = spoken
                customActions = actions
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(user.displayName, style = MaterialTheme.typography.titleLarge)
        Text(
            "@${user.acct}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bio.isNotBlank()) {
            Text(bio, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            "${user.followersCount} followers \u00b7 ${user.followingCount} following \u00b7 ${user.statusesCount} posts",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Buttons for sighted users. Each hides itself from TalkBack since the
        // header-level custom actions cover the same affordances.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!isMe) {
                val buttonLabel = when (followState) {
                    FollowState.NOT_FOLLOWING -> "Follow"
                    FollowState.REQUESTED -> "Requested"
                    FollowState.FOLLOWING -> "Unfollow"
                }
                TextButton(onClick = onToggleFollow) { Text(buttonLabel) }
            }
            TextButton(
                onClick = onOpenAsTimeline,
                modifier = Modifier.clearAndSetSemantics { },
            ) { Text("Open timeline") }
            if (isRemoteMastodon) {
                TextButton(
                    onClick = onOpenInstanceTimeline,
                    modifier = Modifier.clearAndSetSemantics { },
                ) { Text("Instance timeline") }
                TextButton(
                    onClick = onOpenRemoteUserTimeline,
                    modifier = Modifier.clearAndSetSemantics { },
                ) { Text("Remote user timeline") }
            }
            if (!isMe && availableLists.isNotEmpty()) {
                TextButton(
                    onClick = onShowListsDialog,
                    modifier = Modifier.clearAndSetSemantics { },
                ) { Text("Lists") }
            }
        }
    }
}

@Composable
private fun ManageListsDialog(
    lists: List<UserListInfo>,
    memberships: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lists") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (lists.isEmpty()) {
                    Text("No lists on this account.")
                } else {
                    lists.forEach { list ->
                        val isMember = list.id in memberships
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = isMember,
                                onCheckedChange = { onToggle(list.id) },
                            )
                            Text(list.title)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

private fun describeFollow(state: FollowState, followedBy: Boolean): String {
    val self = when (state) {
        FollowState.NOT_FOLLOWING -> "You don't follow them"
        FollowState.REQUESTED -> "Follow request pending"
        FollowState.FOLLOWING -> "You follow them"
    }
    val back = if (followedBy) "they follow you." else "."
    return "$self; $back"
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
        Text("Could not load profile", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = "Try again")
        }
    }
}
