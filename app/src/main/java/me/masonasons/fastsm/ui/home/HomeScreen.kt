package me.masonasons.fastsm.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.masonasons.fastsm.domain.model.TimelineSpec
import me.masonasons.fastsm.domain.model.UniversalMedia
import me.masonasons.fastsm.domain.model.UniversalStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onReply: (UniversalStatus) -> Unit,
    onOpenThread: (UniversalStatus) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (UniversalMedia) -> Unit,
    onOpenLink: (String) -> Unit,
    onCompose: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccountSettings: (Long) -> Unit,
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeId by viewModel.activeAccountId.collectAsStateWithLifecycle()
    val myUserId by viewModel.myUserId.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val statusPages by viewModel.statusPages.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val pendingLogout by viewModel.pendingLogout.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    // When the tabs list changes (e.g. a tab was closed), clamp currentPage.
    LaunchedEffect(tabs.size) {
        if (pagerState.currentPage >= tabs.size && tabs.isNotEmpty()) {
            pagerState.scrollToPage(tabs.size - 1)
        }
    }

    LaunchedEffect(pagerState.currentPage, activeId, tabs) {
        tabs.getOrNull(pagerState.currentPage)?.let(viewModel::onTabSelected)
    }

    LaunchedEffect(viewModel, tabs) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.FocusTab -> {
                    val index = tabs.indexOfFirst { it.id == event.specId }
                    if (index >= 0) pagerState.animateScrollToPage(index)
                }
                HomeEvent.OpenCompose -> onCompose()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("FastSM") },
                    actions = {
                        IconButton(onClick = {
                            tabs.getOrNull(pagerState.currentPage)?.let(viewModel::refresh)
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Filter this tab") },
                                onClick = { menuExpanded = false; showFilterDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Search") },
                                onClick = { menuExpanded = false; onOpenSearch() },
                            )
                            DropdownMenuItem(
                                text = { Text("Add timeline") },
                                onClick = { menuExpanded = false; showAddDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { menuExpanded = false; onOpenSettings() },
                            )
                        }
                    },
                )
                AccountPicker(
                    accounts = accounts,
                    activeAccountId = activeId,
                    onSwitch = viewModel::switchTo,
                    onAddAccount = onAddAccount,
                    onLogOut = viewModel::requestLogout,
                    onOpenAccountSettings = onOpenAccountSettings,
                )
                val mutedSpecs by viewModel.feedbackPrefs.mutedSpecs.collectAsStateWithLifecycle()
                TimelineTabs(
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage,
                    mutedSpecs = mutedSpecs,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    onClose = viewModel::closeTimeline,
                    onToggleMute = { viewModel.toggleMuted(it.id) },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCompose) {
                Icon(Icons.Filled.Edit, contentDescription = "Compose new post")
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { pageIndex ->
            val spec = tabs.getOrNull(pageIndex) ?: return@HorizontalPager
            when (spec) {
                TimelineSpec.Notifications -> NotificationsPageContent(
                    state = notifications,
                    myUserId = myUserId,
                    onRefresh = { viewModel.refresh(spec) },
                    onLoadMore = { viewModel.loadMore(spec) },
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
                else -> StatusPageContent(
                    state = statusPages[spec.id] ?: StatusPageState(),
                    myUserId = myUserId,
                    filter = filters[spec.id] ?: me.masonasons.fastsm.domain.model.TimelineFilter.NONE,
                    onRefresh = { viewModel.refresh(spec) },
                    onLoadMore = { viewModel.loadMore(spec) },
                    onScrollHandled = { viewModel.onScrollHandled(spec.id) },
                    onScrollSettled = { statusId -> viewModel.onScrollSettled(spec, statusId) },
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
        }
    }

    if (showFilterDialog) {
        val currentSpec = tabs.getOrNull(pagerState.currentPage)
        if (currentSpec != null) {
            val currentFilter = filters[currentSpec.id] ?: me.masonasons.fastsm.domain.model.TimelineFilter.NONE
            FilterDialog(
                initial = currentFilter,
                onDismiss = { showFilterDialog = false },
                onApply = { f ->
                    viewModel.setFilter(currentSpec.id, f)
                    showFilterDialog = false
                },
                onClear = { viewModel.clearFilter(currentSpec.id) },
            )
        } else {
            showFilterDialog = false
        }
    }

    pendingLogout?.let { account ->
        AlertDialog(
            onDismissRequest = viewModel::cancelLogout,
            title = { Text("Log out?") },
            text = { Text("Log out of ${account.label}? The account will be removed from FastSM.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLogout) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelLogout) { Text("Cancel") }
            },
        )
    }

    if (showAddDialog) {
        AddTimelineDialog(
            loadLists = { viewModel.loadAvailableLists() },
            onAdd = { spec -> showAddDialog = false; viewModel.addTimeline(spec) },
            onDismiss = { showAddDialog = false },
        )
    }
}

/**
 * Tab strip. Plain `Row + horizontalScroll` rather than ScrollableTabRow so
 * *every* tab stays in the accessibility tree even when it's off-screen —
 * TalkBack's swipe-right reaches them all. On selection change we animate
 * the strip so the active tab is visible. Semantic state announces tab name,
 * muted state, and whether it's the currently selected tab. Long-press opens
 * a DropdownMenu mirroring the custom a11y actions for non-TalkBack users.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineTabs(
    tabs: List<TimelineSpec>,
    selectedIndex: Int,
    mutedSpecs: Set<String>,
    onSelect: (Int) -> Unit,
    onClose: (TimelineSpec) -> Unit,
    onToggleMute: (TimelineSpec) -> Unit,
) {
    val scrollState = rememberScrollState()
    val tabPositions = remember { mutableStateMapOf<Int, Int>() }
    val scrollScope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex, tabs.size) {
        val target = tabPositions[selectedIndex] ?: return@LaunchedEffect
        scrollScope.launch { scrollState.animateScrollTo(target) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        tabs.forEachIndexed { index, spec ->
            val selected = index == selectedIndex
            val muted = spec.id in mutedSpecs
            var menuOpen by remember(spec.id) { mutableStateOf(false) }
            val menuActions = buildList {
                add(
                    MenuAction(if (muted) "Unmute sounds for this tab" else "Mute sounds for this tab") {
                        onToggleMute(spec)
                    }
                )
                if (spec.closable) {
                    add(MenuAction("Close tab") { onClose(spec) })
                }
            }
            val actions = menuActions.toAccessibilityActions()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        tabPositions[index] = coords.positionInParent().x.toInt()
                    }
                    .combinedClickable(
                        onClick = { onSelect(index) },
                        onLongClick = { menuOpen = true },
                    )
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .clearAndSetSemantics {
                        contentDescription = buildString {
                            append(spec.label).append(" tab")
                            if (muted) append(", muted")
                            if (selected) append(", selected")
                        }
                        customActions = actions
                        onClick { onSelect(index); true }
                    },
            ) {
                Text(spec.label)
                if (spec.closable) {
                    IconButton(
                        onClick = { onClose(spec) },
                        modifier = Modifier
                            .size(32.dp)
                            .clearAndSetSemantics { }, // exposed via the tab's custom action
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menuActions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = { action.run(); menuOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
private fun StatusPageContent(
    state: StatusPageState,
    myUserId: String?,
    filter: me.masonasons.fastsm.domain.model.TimelineFilter,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onScrollHandled: () -> Unit,
    onScrollSettled: (String) -> Unit,
    onReply: (UniversalStatus) -> Unit,
    onFavourite: (UniversalStatus) -> Unit,
    onBoost: (UniversalStatus) -> Unit,
    onBookmark: (UniversalStatus) -> Unit,
    onDelete: (UniversalStatus) -> Unit,
    onEdit: (UniversalStatus) -> Unit,
    onQuote: (UniversalStatus) -> Unit,
    onOpenThread: (UniversalStatus) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (UniversalMedia) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    // Client-side filter pass. We keep the underlying state.statuses intact so
    // pagination anchors (last-id maxId) don't drift when the user toggles a
    // filter mid-scroll.
    val visibleStatuses = remember(state.statuses, filter, myUserId) {
        if (!filter.isActive) state.statuses
        else state.statuses.filter { filter.matches(it, myUserId) }
    }
    PullToRefreshBox(
        isRefreshing = state.loading && state.statuses.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading && state.statuses.isEmpty() -> LoadingBox()
            state.error != null && state.statuses.isEmpty() -> ErrorBox(state.error, onRefresh)
            state.statuses.isEmpty() -> EmptyBox("No posts yet.")
            visibleStatuses.isEmpty() -> EmptyBox("No posts match this filter.")
            else -> {
                val listState = rememberLazyListState()
                val shouldLoadMore by remember(state) {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            ?: return@derivedStateOf false
                        last >= visibleStatuses.size - 5 && !state.loadingMore && !state.exhausted
                    }
                }
                LaunchedEffect(listState, visibleStatuses.size, state.loadingMore, state.exhausted) {
                    snapshotFlow { shouldLoadMore }.collect { if (it) onLoadMore() }
                }

                // Marker sync: when the ViewModel asks us to scroll to a specific
                // status id (the server-side read marker), jump there once.
                LaunchedEffect(state.pendingScrollId, visibleStatuses) {
                    val target = state.pendingScrollId ?: return@LaunchedEffect
                    val index = visibleStatuses.indexOfFirst { it.id == target }
                    if (index >= 0) listState.scrollToItem(index)
                    onScrollHandled()
                }

                // Persist the first-visible status id after the user settles on
                // a position — so "Remember timeline positions" can restore it
                // on next refresh. Gated on pendingScrollId being null so the
                // initial restoration doesn't race to overwrite itself with
                // "top of list" in the split second before scrollToItem runs.
                LaunchedEffect(listState, visibleStatuses, state.pendingScrollId) {
                    if (state.pendingScrollId != null) return@LaunchedEffect
                    if (visibleStatuses.isEmpty()) return@LaunchedEffect
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .distinctUntilChanged()
                        .debounce(1000)
                        .collect { idx ->
                            val status = visibleStatuses.getOrNull(idx) ?: return@collect
                            onScrollSettled(status.id)
                        }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(visibleStatuses, key = { it.id }) { status ->
                        StatusItem(
                            status = status,
                            myUserId = myUserId,
                            onReply = onReply,
                            onFavourite = onFavourite,
                            onBoost = onBoost,
                            onBookmark = onBookmark,
                            onDelete = onDelete,
                            onEdit = onEdit,
                            onQuote = onQuote,
                            onOpenThread = onOpenThread,
                            onOpenProfile = onOpenProfile,
                            onOpenMedia = onOpenMedia,
                            onOpenLink = onOpenLink,
                        )
                    }
                    item(key = "__footer__") {
                        PaginationFooter(
                            loadingMore = state.loadingMore,
                            exhausted = state.exhausted,
                            error = state.error.takeIf { state.statuses.isNotEmpty() },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsPageContent(
    state: NotificationsPageState,
    myUserId: String?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onReply: (UniversalStatus) -> Unit,
    onFavourite: (UniversalStatus) -> Unit,
    onBoost: (UniversalStatus) -> Unit,
    onBookmark: (UniversalStatus) -> Unit,
    onDelete: (UniversalStatus) -> Unit,
    onEdit: (UniversalStatus) -> Unit,
    onQuote: (UniversalStatus) -> Unit,
    onOpenThread: (UniversalStatus) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (UniversalMedia) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.loading && state.notifications.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading && state.notifications.isEmpty() -> LoadingBox()
            state.error != null && state.notifications.isEmpty() -> ErrorBox(state.error, onRefresh)
            state.notifications.isEmpty() -> EmptyBox("No notifications yet.")
            else -> {
                val listState = rememberLazyListState()
                val shouldLoadMore by remember(state) {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            ?: return@derivedStateOf false
                        last >= state.notifications.size - 5 && !state.loadingMore && !state.exhausted
                    }
                }
                LaunchedEffect(listState, state.notifications.size, state.loadingMore, state.exhausted) {
                    snapshotFlow { shouldLoadMore }.collect { if (it) onLoadMore() }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.notifications, key = { it.id }) { n ->
                        NotificationItem(
                            notification = n,
                            myUserId = myUserId,
                            onReply = onReply,
                            onFavourite = onFavourite,
                            onBoost = onBoost,
                            onBookmark = onBookmark,
                            onDelete = onDelete,
                            onEdit = onEdit,
                            onQuote = onQuote,
                            onOpenThread = onOpenThread,
                            onOpenProfile = onOpenProfile,
                            onOpenMedia = onOpenMedia,
                            onOpenLink = onOpenLink,
                        )
                    }
                    item(key = "__footer__") {
                        PaginationFooter(
                            loadingMore = state.loadingMore,
                            exhausted = state.exhausted,
                            error = state.error.takeIf { state.notifications.isNotEmpty() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
        Text("Could not load", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = "Try again")
        }
    }
}

@Composable
private fun PaginationFooter(loadingMore: Boolean, exhausted: Boolean, error: String?) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loadingMore -> CircularProgressIndicator()
            error != null -> Text(
                "Could not load more: $error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
            exhausted -> Text(
                "End of feed",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {}
        }
    }
}
