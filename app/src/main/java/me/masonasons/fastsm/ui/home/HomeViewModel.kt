package me.masonasons.fastsm.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.masonasons.fastsm.data.feedback.FeedbackManager
import me.masonasons.fastsm.data.prefs.AppPrefs
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.data.repo.TimelinePositionRepository
import me.masonasons.fastsm.domain.model.AppEvent
import me.masonasons.fastsm.data.repo.TimelineRepository
import me.masonasons.fastsm.domain.model.Account
import me.masonasons.fastsm.domain.model.TimelineEvent
import me.masonasons.fastsm.domain.model.TimelineSpec
import me.masonasons.fastsm.domain.model.UniversalNotification
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.platform.PlatformAccount
import me.masonasons.fastsm.platform.PlatformFactory
import me.masonasons.fastsm.ui.compose.ComposeDraft
import me.masonasons.fastsm.ui.compose.ComposeDraftStore

/**
 * Home-screen state. Tabs are dynamic: Home + Notifications are always present
 * and non-closable; the rest come from [TimelineRepository] per active account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val accountRepository: AccountRepository,
    private val timelineRepository: TimelineRepository,
    private val timelinePositionRepository: TimelinePositionRepository,
    private val platformFactory: PlatformFactory,
    private val composeDraftStore: ComposeDraftStore,
    private val appPrefs: AppPrefs,
    private val feedback: FeedbackManager,
) : ViewModel() {

    val feedbackPrefs = feedback.prefs

    fun toggleMuted(specId: String) {
        feedback.toggleMuted(specId)
    }

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _activeAccountId = MutableStateFlow<Long?>(null)
    val activeAccountId: StateFlow<Long?> = _activeAccountId.asStateFlow()

    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    private val _tabs = MutableStateFlow<List<TimelineSpec>>(
        listOf(TimelineSpec.Home, TimelineSpec.Notifications),
    )
    val tabs: StateFlow<List<TimelineSpec>> = _tabs.asStateFlow()

    /** Page state per status-timeline spec, keyed by spec.id. Notifications lives separately. */
    private val _statusPages = MutableStateFlow<Map<String, StatusPageState>>(emptyMap())
    val statusPages: StateFlow<Map<String, StatusPageState>> = _statusPages.asStateFlow()

    /** Per-spec display filter. Applied client-side in the UI. */
    private val _filters = MutableStateFlow<Map<String, me.masonasons.fastsm.domain.model.TimelineFilter>>(emptyMap())
    val filters: StateFlow<Map<String, me.masonasons.fastsm.domain.model.TimelineFilter>> = _filters.asStateFlow()

    fun setFilter(specId: String, filter: me.masonasons.fastsm.domain.model.TimelineFilter) {
        _filters.value = if (filter.isActive) _filters.value + (specId to filter)
        else _filters.value - specId
    }

    fun clearFilter(specId: String) {
        _filters.value = _filters.value - specId
    }

    private val _notifications = MutableStateFlow(NotificationsPageState())
    val notifications: StateFlow<NotificationsPageState> = _notifications.asStateFlow()

    private val _pendingLogout = MutableStateFlow<Account?>(null)
    val pendingLogout: StateFlow<Account?> = _pendingLogout.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    init {
        combine(accountRepository.accounts, accountRepository.activeAccountId) { accts, active ->
            accts to active
        }
            .onEach { (accts, active) ->
                val prevActive = _activeAccountId.value
                _accounts.value = accts
                val resolvedActive = when {
                    active != null && accts.any { it.id == active } -> active
                    else -> accts.firstOrNull()?.id
                }
                _activeAccountId.value = resolvedActive
                _myUserId.value = accts.firstOrNull { it.id == resolvedActive }?.userId
                if (resolvedActive != null && resolvedActive != prevActive) {
                    _statusPages.value = emptyMap()
                    _notifications.value = NotificationsPageState()
                    refresh(TimelineSpec.Home)
                }
            }
            .launchIn(viewModelScope)

        // Stream realtime events from the active account's platform. Re-keys
        // on (accountId, streamingEnabled) — toggling the setting off cancels
        // the socket, back on reconnects. Bluesky emits nothing.
        combine(_activeAccountId, appPrefs.streamingEnabled) { id, enabled -> id to enabled }
            .flatMapLatest { (id, enabled) ->
                if (id == null || !enabled) emptyFlow<TimelineEvent>()
                else flow {
                    val account = accountRepository.getById(id) ?: return@flow
                    val platform = platformFactory.forAccount(account)
                    emitAll(platform.streamEvents())
                }
            }
            .onEach { applyStreamEvent(it) }
            .launchIn(viewModelScope)

        // Mirror the persisted timeline list into [_tabs], prefixed by Home + Notifications.
        _activeAccountId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else timelineRepository.observe(id)
            }
            .onEach { extras ->
                _tabs.value = buildList {
                    add(TimelineSpec.Home)
                    add(TimelineSpec.Notifications)
                    addAll(extras)
                }
            }
            .launchIn(viewModelScope)
    }

    fun switchTo(accountId: Long) {
        if (accountId == _activeAccountId.value) return
        accountRepository.setActive(accountId)
    }

    fun requestLogout() {
        _pendingLogout.value = currentAccount()
    }

    fun cancelLogout() {
        _pendingLogout.value = null
    }

    fun confirmLogout() {
        val target = _pendingLogout.value ?: return
        _pendingLogout.value = null
        viewModelScope.launch {
            accountRepository.delete(target.id)
            platformFactory.invalidate(target.id)
        }
    }

    fun addTimeline(spec: TimelineSpec) {
        val accountId = _activeAccountId.value ?: return
        viewModelScope.launch {
            timelineRepository.add(accountId, spec)
            _events.tryEmit(HomeEvent.FocusTab(spec.id))
        }
    }

    fun closeTimeline(spec: TimelineSpec) {
        if (!spec.closable) return
        val accountId = _activeAccountId.value ?: return
        viewModelScope.launch {
            timelineRepository.remove(accountId, spec)
            _statusPages.value = _statusPages.value - spec.id
        }
    }

    fun onTabSelected(spec: TimelineSpec) {
        when (spec) {
            TimelineSpec.Notifications -> {
                val s = _notifications.value
                if (s.notifications.isEmpty() && !s.loading) refresh(spec)
            }
            else -> {
                val s = _statusPages.value[spec.id]
                if (s == null || (s.statuses.isEmpty() && !s.loading)) refresh(spec)
            }
        }
    }

    fun refresh(spec: TimelineSpec) {
        val account = currentAccount() ?: return
        val platform = platformFactory.forAccount(account)
        when (spec) {
            TimelineSpec.Notifications -> refreshNotifications(platform)
            else -> refreshStatuses(spec, platform)
        }
    }

    fun loadMore(spec: TimelineSpec) {
        val account = currentAccount() ?: return
        val platform = platformFactory.forAccount(account)
        when (spec) {
            TimelineSpec.Notifications -> loadMoreNotifications(platform)
            else -> loadMoreStatuses(spec, platform)
        }
    }

    private fun refreshStatuses(spec: TimelineSpec, platform: PlatformAccount) {
        updateStatusPage(spec.id) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val accountId = _activeAccountId.value
            val pages = appPrefs.fetchPages.value
            runCatching { loadStatusesPaged(spec, platform, platform.maxPageSize, maxId = null, pages = pages) }
                .onSuccess { result ->
                    val list = result.items
                    val scrollTo = if (list.isNotEmpty() && accountId != null) {
                        resolveInitialScroll(spec, list, platform, accountId)
                    } else null
                    updateStatusPage(spec.id) {
                        StatusPageState(
                            statuses = list,
                            loading = false,
                            exhausted = result.exhausted,
                            pendingScrollId = scrollTo,
                        )
                    }
                    feedback.emit(AppEvent.TabLoaded(spec.id, list.size), specId = spec.id)
                }
                .onFailure { e ->
                    updateStatusPage(spec.id) {
                        it.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
                    }
                }
        }
    }

    /**
     * Decide where to scroll a freshly-loaded timeline. Priority:
     *
     *  1. **Home + marker sync on** — try the server-side read marker. If
     *     that's present in [list], use it (and publish the newest status
     *     as the new marker so other devices see our read progress).
     *  2. **Remember enabled** — fall back to the locally-stored position
     *     for this (account, timeline). Applies to every timeline, and
     *     catches the "home sync failed / marker stale" case too.
     *
     * Returns `null` when neither source has a usable anchor; the UI then
     * shows the list from the top.
     */
    private suspend fun resolveInitialScroll(
        spec: TimelineSpec,
        list: List<UniversalStatus>,
        platform: PlatformAccount,
        accountId: Long,
    ): String? {
        val syncEnabled = appPrefs.markerSyncEnabled.value
        val rememberEnabled = appPrefs.rememberTimelinePositions.value

        if (spec == TimelineSpec.Home && syncEnabled) {
            val serverAnchor = applyHomeMarker(list, platform)
            if (serverAnchor != null) return serverAnchor
            // Server didn't return a usable anchor — fall through to the
            // local remember fallback below.
        }

        if (rememberEnabled) {
            val stored = timelinePositionRepository.get(accountId, spec.id)
            if (stored != null && list.any { it.id == stored }) return stored
        }
        return null
    }

    /**
     * Fetch the server-side read marker and, if that status is in [list],
     * return its id. Also publish the newest visible status id back as the
     * new marker so other devices see our read progress. Both requests are
     * tolerated to fail silently — the marker is best-effort.
     */
    private suspend fun applyHomeMarker(
        list: List<UniversalStatus>,
        platform: PlatformAccount,
    ): String? {
        val existing = platform.getHomeMarker()
        val anchor = existing?.takeIf { markerId -> list.any { it.id == markerId } }
        list.firstOrNull()?.id?.let { newest ->
            // Don't move the marker backwards if the cached read position is
            // already newer than what's on top.
            if (existing == null || newest != existing) {
                platform.setHomeMarker(newest)
            }
        }
        return anchor
    }

    /**
     * Called from the timeline UI when the user's scroll position has
     * settled on [statusId]. Persisted per (account, timeline) so the next
     * app-open can restore it. No-op when the "Remember timeline positions"
     * setting is off, to keep the table from filling up for users who
     * didn't opt in.
     */
    fun onScrollSettled(spec: TimelineSpec, statusId: String) {
        if (!appPrefs.rememberTimelinePositions.value) return
        val accountId = _activeAccountId.value ?: return
        viewModelScope.launch {
            runCatching { timelinePositionRepository.save(accountId, spec.id, statusId) }
        }
    }

    fun onScrollHandled(specId: String) {
        val state = _statusPages.value[specId] ?: return
        if (state.pendingScrollId != null) {
            updateStatusPage(specId) { it.copy(pendingScrollId = null) }
        }
    }

    private fun loadMoreStatuses(spec: TimelineSpec, platform: PlatformAccount) {
        val current = _statusPages.value[spec.id] ?: return
        if (current.loading || current.loadingMore || current.exhausted) return
        val maxId = current.statuses.lastOrNull()?.id ?: return
        updateStatusPage(spec.id) { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            val pages = appPrefs.fetchPages.value
            runCatching { loadStatusesPaged(spec, platform, platform.maxPageSize, maxId, pages) }
                .onSuccess { result ->
                    updateStatusPage(spec.id) {
                        it.copy(
                            loadingMore = false,
                            statuses = (it.statuses + result.items).distinctBy { s -> s.id },
                            exhausted = result.exhausted,
                        )
                    }
                }
                .onFailure { e ->
                    updateStatusPage(spec.id) {
                        it.copy(loadingMore = false, error = e.message ?: e.javaClass.simpleName)
                    }
                }
        }
    }

    private data class PagedResult(val items: List<UniversalStatus>, val exhausted: Boolean)

    /**
     * Fetch up to [pages] back-to-back pages, using each page's last id as the
     * next maxId. Stops early when the server returns fewer than [limit] —
     * that's our signal that there's nothing older to fetch.
     */
    private suspend fun loadStatusesPaged(
        spec: TimelineSpec,
        platform: PlatformAccount,
        limit: Int,
        maxId: String?,
        pages: Int,
    ): PagedResult {
        val combined = mutableListOf<UniversalStatus>()
        var cursor = maxId
        var exhausted = false
        val target = pages.coerceAtLeast(1)
        var i = 0
        while (i < target) {
            val page = loadStatuses(spec, platform, limit, cursor)
            combined += page
            if (page.size < limit) {
                exhausted = true
                break
            }
            cursor = page.lastOrNull()?.id ?: break
            i++
        }
        return PagedResult(combined.distinctBy { it.id }, exhausted)
    }

    private suspend fun loadStatuses(
        spec: TimelineSpec,
        platform: PlatformAccount,
        limit: Int,
        maxId: String?,
    ): List<UniversalStatus> = when (spec) {
        TimelineSpec.Home -> platform.getHomeTimeline(limit, maxId)
        TimelineSpec.LocalPublic -> platform.getLocalTimeline(limit, maxId)
        TimelineSpec.FederatedPublic -> platform.getFederatedTimeline(limit, maxId)
        TimelineSpec.Bookmarks -> platform.getBookmarks(limit, maxId)
        TimelineSpec.Favourites -> platform.getFavourites(limit, maxId)
        is TimelineSpec.RemoteInstance ->
            platform.getRemoteInstanceTimeline(spec.instance, spec.localOnly, limit, maxId)
        is TimelineSpec.RemoteUser ->
            platform.getRemoteUserTimeline(spec.instance, spec.acct, limit, maxId)
        is TimelineSpec.UserPosts ->
            platform.getUserStatuses(spec.userId, limit, maxId)
        is TimelineSpec.UserList ->
            platform.getListTimeline(spec.listId, limit, maxId)
        is TimelineSpec.Hashtag ->
            platform.getHashtagTimeline(spec.tag, limit, maxId)
        TimelineSpec.Notifications ->
            error("Notifications doesn't load as statuses")
    }

    suspend fun loadAvailableLists(): List<me.masonasons.fastsm.domain.model.UserListInfo> {
        val account = currentAccount() ?: return emptyList()
        val platform = platformFactory.forAccount(account)
        return runCatching { platform.getUserLists() }.getOrDefault(emptyList())
    }

    private fun refreshNotifications(platform: PlatformAccount) {
        _notifications.value = _notifications.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { platform.getNotifications(platform.maxPageSize) }
                .onSuccess { list ->
                    _notifications.value = NotificationsPageState(
                        notifications = list,
                        loading = false,
                        exhausted = list.size < platform.maxPageSize,
                    )
                }
                .onFailure { e ->
                    _notifications.value = _notifications.value.copy(
                        loading = false,
                        error = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    private fun loadMoreNotifications(platform: PlatformAccount) {
        val current = _notifications.value
        if (current.loading || current.loadingMore || current.exhausted) return
        val maxId = current.notifications.lastOrNull()?.id ?: return
        _notifications.value = current.copy(loadingMore = true, error = null)
        viewModelScope.launch {
            runCatching { platform.getNotifications(platform.maxPageSize, maxId) }
                .onSuccess { older ->
                    _notifications.value = _notifications.value.let {
                        it.copy(
                            loadingMore = false,
                            notifications = (it.notifications + older).distinctBy { n -> n.id },
                            exhausted = older.size < platform.maxPageSize,
                        )
                    }
                }
                .onFailure { e ->
                    _notifications.value = _notifications.value.copy(
                        loadingMore = false,
                        error = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    fun prepareEdit(status: UniversalStatus) {
        val account = currentAccount() ?: return
        val platform = platformFactory.forAccount(account)
        if (!platform.supportsEdit) return
        viewModelScope.launch {
            // Prefer the server's /source endpoint to preserve mentions and
            // custom emoji shortcodes; fall back to the already-displayed text
            // if the server doesn't expose it (pre-3.5 Mastodon).
            val source = platform.getStatusSourceText(status.id)
            composeDraftStore.set(
                ComposeDraft(
                    text = source?.text ?: status.text,
                    spoilerText = source?.spoilerText?.ifBlank { null } ?: status.spoilerText,
                    visibility = status.visibility,
                    editingStatusId = status.id,
                )
            )
            _events.tryEmit(HomeEvent.OpenCompose)
        }
    }

    fun prepareQuote(status: UniversalStatus) {
        val me = currentAccount() ?: return
        val platform = platformFactory.forAccount(me)
        viewModelScope.launch {
            val resolved = runCatching { platform.resolveLocal(status) }.getOrDefault(status)
            composeDraftStore.set(
                ComposeDraft(
                    text = "",
                    quoteStatusId = resolved.id,
                    quoteStatusCid = resolved.platformCid,
                    quoteAuthor = resolved.account.acct,
                    quoteText = resolved.text,
                    visibility = resolved.visibility,
                )
            )
            _events.tryEmit(HomeEvent.OpenCompose)
        }
    }

    fun prepareReply(status: UniversalStatus) {
        val me = currentAccount() ?: return
        val platform = platformFactory.forAccount(me)
        val mentions = buildList {
            add(status.account.id to status.account.acct)
            status.mentions.forEach { add(it.id to it.acct) }
        }
            .filterNot { (id, _) -> id == me.userId }
            .distinctBy { it.first }
        val text = mentions.joinToString(" ", postfix = " ") { (_, acct) ->
            "@${acct.removePrefix("@")}"
        }
        viewModelScope.launch {
            // Resolve so reply uses the local status id — on remote timelines
            // the id we have only makes sense on the origin server.
            val resolved = runCatching { platform.resolveLocal(status) }.getOrDefault(status)
            composeDraftStore.set(
                ComposeDraft(
                    text = text,
                    replyToId = resolved.id,
                    replyToAuthor = resolved.account.acct,
                    replyToText = resolved.text,
                    replyToSpoiler = resolved.spoilerText,
                    spoilerText = resolved.spoilerText,
                    visibility = resolved.visibility,
                )
            )
        }
    }

    fun onFavourite(status: UniversalStatus) = mutate(status) { platform, s ->
        val wasFav = s.favourited
        if (wasFav) platform.unfavourite(s.id) else platform.favourite(s.id)
        feedback.emit(if (wasFav) AppEvent.Unfavourited else AppEvent.Favourited)
        s.copy(
            favourited = !wasFav,
            favouritesCount = s.favouritesCount + if (wasFav) -1 else 1,
        )
    }

    fun onBoost(status: UniversalStatus) = mutate(status) { platform, s ->
        val wasBoosted = s.boosted
        if (wasBoosted) platform.unboost(s.id) else platform.boost(s.id)
        // Desktop fires "send_repost" on boost. No specific "unrepost" sound —
        // stay silent on undo to avoid a confusing echo.
        if (!wasBoosted) feedback.emit(AppEvent.BoostSent)
        s.copy(
            boosted = !wasBoosted,
            boostsCount = s.boostsCount + if (wasBoosted) -1 else 1,
        )
    }

    fun onBookmark(status: UniversalStatus) = mutate(status) { platform, s ->
        if (s.bookmarked) platform.unbookmark(s.id) else platform.bookmark(s.id)
        // No bookmark sound in the default pack — emit for haptics but speech
        // and sound are silent by design.
        feedback.emit(AppEvent.Bookmarked)
        s.copy(bookmarked = !s.bookmarked)
    }

    fun onDelete(status: UniversalStatus) {
        val account = currentAccount() ?: return
        val platform = platformFactory.forAccount(account)
        viewModelScope.launch {
            runCatching { platform.deleteStatus(status.id) }
                .onSuccess {
                    feedback.emit(AppEvent.Deleted)
                    removeStatusById(status.id)
                }
        }
    }

    private fun mutate(
        status: UniversalStatus,
        block: suspend (PlatformAccount, UniversalStatus) -> UniversalStatus,
    ) {
        val account = currentAccount() ?: return
        val platform = platformFactory.forAccount(account)
        viewModelScope.launch {
            runCatching {
                // Resolve once — when the post came from a remote-instance /
                // remote-user timeline, interactions must target our local
                // server's mirror of it. For local posts resolveLocal is a
                // no-op, so this adds no network roundtrip in the common case.
                val resolved = platform.resolveLocal(status)
                val updated = block(platform, resolved)
                // If the caller's block worked on the resolved status (local
                // id), we still want the original row in the UI to reflect
                // the new state — splice the mutation fields onto the local
                // row so the timeline position stays stable.
                if (resolved.id != status.id) {
                    status.copy(
                        favourited = updated.favourited,
                        boosted = updated.boosted,
                        bookmarked = updated.bookmarked,
                        favouritesCount = updated.favouritesCount,
                        boostsCount = updated.boostsCount,
                        repliesCount = updated.repliesCount,
                    )
                } else updated
            }.onSuccess { updated -> replaceStatus(updated) }
        }
    }

    private fun replaceStatus(updated: UniversalStatus) {
        _statusPages.value = _statusPages.value.mapValues { (_, state) ->
            state.copy(statuses = state.statuses.map { if (it.id == updated.id) updated else it })
        }
        _notifications.value = _notifications.value.copy(
            notifications = _notifications.value.notifications.map { n ->
                if (n.status?.id == updated.id) n.copy(status = updated) else n
            },
        )
    }

    private fun removeStatusById(statusId: String) {
        _statusPages.value = _statusPages.value.mapValues { (_, state) ->
            state.copy(statuses = state.statuses.filterNot { it.id == statusId })
        }
        _notifications.value = _notifications.value.copy(
            notifications = _notifications.value.notifications.filterNot { it.status?.id == statusId },
        )
    }

    /**
     * Stream events arrive while the user is active; prepend to the Home tab
     * (only place realtime updates land) and notifications. Dedup by id so a
     * reconnect + buffered backlog doesn't double-post.
     */
    private fun applyStreamEvent(event: TimelineEvent) = when (event) {
        is TimelineEvent.StatusUpdate -> {
            val home = _statusPages.value[TimelineSpec.Home.id]
            if (home != null && home.statuses.none { it.id == event.status.id }) {
                _statusPages.value = _statusPages.value.toMutableMap().also {
                    it[TimelineSpec.Home.id] = home.copy(
                        statuses = listOf(event.status) + home.statuses,
                    )
                }
                feedback.emit(
                    AppEvent.NewPostReceived(TimelineSpec.Home.id, TimelineSpec.Home.newPostSoundKey),
                    specId = TimelineSpec.Home.id,
                )
            }
            Unit
        }
        is TimelineEvent.NotificationReceived -> {
            val current = _notifications.value
            if (current.notifications.none { it.id == event.notification.id }) {
                _notifications.value = current.copy(
                    notifications = listOf(event.notification) + current.notifications,
                )
                val n = event.notification
                val summary = "${n.account.displayName} ${n.typeRaw}"
                feedback.emit(
                    AppEvent.NotificationReceived(summary),
                    specId = TimelineSpec.Notifications.id,
                )
            }
            Unit
        }
        is TimelineEvent.StatusDeleted -> removeStatusById(event.statusId)
    }

    private fun updateStatusPage(specId: String, block: (StatusPageState) -> StatusPageState) {
        _statusPages.value = _statusPages.value.toMutableMap().also {
            it[specId] = block(it[specId] ?: StatusPageState())
        }
    }

    private fun currentAccount(): Account? {
        val id = _activeAccountId.value ?: return null
        return _accounts.value.firstOrNull { it.id == id }
    }
}

data class StatusPageState(
    val statuses: List<UniversalStatus> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val exhausted: Boolean = false,
    val error: String? = null,
    /** Status id to scroll to after the list renders (marker sync). Cleared by the UI. */
    val pendingScrollId: String? = null,
)

data class NotificationsPageState(
    val notifications: List<UniversalNotification> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val exhausted: Boolean = false,
    val error: String? = null,
)

sealed interface HomeEvent {
    data class FocusTab(val specId: String) : HomeEvent
    /** Request navigation to the compose screen (used for edit — reply has its own flow). */
    data object OpenCompose : HomeEvent
}
