package me.masonasons.fastsm.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.data.repo.TimelineRepository
import me.masonasons.fastsm.domain.model.FollowState
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.Relationship
import me.masonasons.fastsm.domain.model.TimelineSpec
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.domain.model.UserListInfo
import me.masonasons.fastsm.platform.PlatformAccount
import me.masonasons.fastsm.platform.PlatformFactory
import me.masonasons.fastsm.ui.compose.ComposeDraft
import me.masonasons.fastsm.ui.compose.ComposeDraftStore

private const val PAGE_SIZE = 40

class ProfileViewModel(
    private val userId: String,
    private val accountRepository: AccountRepository,
    private val timelineRepository: TimelineRepository,
    private val platformFactory: PlatformFactory,
    private val composeDraftStore: ComposeDraftStore,
    private val feedback: me.masonasons.fastsm.data.feedback.FeedbackManager,
) : ViewModel() {

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<ProfileEvent>(extraBufferCapacity = 4)
    val events: kotlinx.coroutines.flow.SharedFlow<ProfileEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            val platform = platformFactory.forAccount(account)
            _state.value = _state.value.copy(
                loading = true,
                error = null,
                myUserId = account.userId,
                isMe = userId == account.userId,
            )
            runCatching {
                coroutineScope {
                    val userDeferred = async { platform.getUser(userId) }
                    val relDeferred = async {
                        runCatching { platform.getRelationship(userId) }.getOrNull()
                    }
                    val statusesDeferred = async {
                        platform.getUserStatuses(userId, limit = PAGE_SIZE)
                    }
                    Triple(userDeferred.await(), relDeferred.await(), statusesDeferred.await())
                }
            }
                .onSuccess { (user, rel, statuses) ->
                    _state.value = _state.value.copy(
                        loading = false,
                        user = user,
                        relationship = rel,
                        statuses = statuses,
                        exhausted = statuses.size < PAGE_SIZE,
                    )
                    if (user.platform == PlatformType.MASTODON && userId != account.userId) {
                        loadListData(platform, user.id)
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    private fun loadListData(platform: PlatformAccount, targetUserId: String) {
        viewModelScope.launch {
            val allLists = runCatching { platform.getUserLists() }.getOrDefault(emptyList())
            val memberships = runCatching { platform.getListsContainingUser(targetUserId) }
                .getOrDefault(emptyList())
                .map { it.id }
                .toSet()
            _state.value = _state.value.copy(
                availableLists = allLists,
                listMemberships = memberships,
            )
        }
    }

    fun openInstanceTimeline() {
        val user = _state.value.user ?: return
        val inst = remoteInstanceOf(user) ?: return
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            timelineRepository.add(
                account.id,
                TimelineSpec.RemoteInstance(inst, localOnly = true),
            )
            _events.tryEmit(ProfileEvent.Closed)
        }
    }

    fun openRemoteUserTimeline() {
        val user = _state.value.user ?: return
        val inst = remoteInstanceOf(user) ?: return
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            timelineRepository.add(
                account.id,
                TimelineSpec.RemoteUser(inst, user.acct),
            )
            _events.tryEmit(ProfileEvent.Closed)
        }
    }

    fun toggleList(listId: String) {
        val user = _state.value.user ?: return
        val isMember = listId in _state.value.listMemberships
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            val platform = platformFactory.forAccount(account)
            runCatching {
                if (isMember) platform.removeUserFromList(listId, user.id)
                else platform.addUserToList(listId, user.id)
            }.onSuccess {
                val current = _state.value.listMemberships
                _state.value = _state.value.copy(
                    listMemberships = if (isMember) current - listId else current + listId,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    error = "Could not update list: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    /**
     * The instance hosting this user, normalized to an `https://host` URL.
     * Returns null for local Mastodon users (their instance == viewer's
     * instance) and for Bluesky accounts (the "instance" concept doesn't map).
     */
    private fun remoteInstanceOf(user: UniversalUser): String? {
        if (user.platform != PlatformType.MASTODON) return null
        val at = user.acct.removePrefix("@")
        val host = at.substringAfter('@', missingDelimiterValue = "")
        if (host.isBlank()) return null
        return "https://$host"
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.exhausted) return
        val maxId = current.statuses.lastOrNull()?.id ?: return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            val platform = platformFactory.forAccount(account)
            runCatching { platform.getUserStatuses(userId, PAGE_SIZE, maxId) }
                .onSuccess { older ->
                    _state.value = _state.value.let {
                        it.copy(
                            loadingMore = false,
                            statuses = (it.statuses + older).distinctBy { s -> s.id },
                            exhausted = older.size < PAGE_SIZE,
                        )
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        error = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    /**
     * Add this user's posts to the Home tab bar. Uses UserPosts(userId) so the
     * timeline is fetched via the viewer's own account (ideal for local/federated
     * users); for users on other instances the viewer's home instance may have a
     * limited cache — use "Open remote user timeline" for the raw remote view.
     */
    fun openAsTimeline() {
        val user = _state.value.user ?: return
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            timelineRepository.add(
                account.id,
                TimelineSpec.UserPosts(userId = user.id, displayName = "@${user.acct}"),
            )
            _events.tryEmit(ProfileEvent.Closed)
        }
    }

    fun toggleFollow() {
        val rel = _state.value.relationship
        val currentFollowState = rel?.followState ?: FollowState.NOT_FOLLOWING
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            val platform = platformFactory.forAccount(account)
            val wasFollowing = currentFollowState != FollowState.NOT_FOLLOWING
            runCatching {
                if (wasFollowing) platform.unfollow(userId) else platform.follow(userId)
            }.onSuccess { newRel ->
                feedback.emit(
                    if (wasFollowing) me.masonasons.fastsm.domain.model.AppEvent.Unfollowed
                    else me.masonasons.fastsm.domain.model.AppEvent.Followed
                )
                _state.value = _state.value.copy(relationship = newRel)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    error = "Follow action failed: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    fun onFavourite(status: UniversalStatus) = mutate(status) { platform, s ->
        val wasFav = s.favourited
        if (wasFav) platform.unfavourite(s.id) else platform.favourite(s.id)
        feedback.emit(
            if (wasFav) me.masonasons.fastsm.domain.model.AppEvent.Unfavourited
            else me.masonasons.fastsm.domain.model.AppEvent.Favourited
        )
        s.copy(
            favourited = !wasFav,
            favouritesCount = s.favouritesCount + if (wasFav) -1 else 1,
        )
    }

    fun onBoost(status: UniversalStatus) = mutate(status) { platform, s ->
        val wasBoosted = s.boosted
        if (wasBoosted) platform.unboost(s.id) else platform.boost(s.id)
        if (!wasBoosted) feedback.emit(me.masonasons.fastsm.domain.model.AppEvent.BoostSent)
        s.copy(
            boosted = !wasBoosted,
            boostsCount = s.boostsCount + if (wasBoosted) -1 else 1,
        )
    }

    fun onBookmark(status: UniversalStatus) = mutate(status) { platform, s ->
        if (s.bookmarked) platform.unbookmark(s.id) else platform.bookmark(s.id)
        feedback.emit(me.masonasons.fastsm.domain.model.AppEvent.Bookmarked)
        s.copy(bookmarked = !s.bookmarked)
    }

    fun onDelete(status: UniversalStatus) {
        viewModelScope.launch {
            feedback.emit(me.masonasons.fastsm.domain.model.AppEvent.Deleted)
            val account = accountRepository.getActiveAccount() ?: return@launch
            val platform = platformFactory.forAccount(account)
            runCatching { platform.deleteStatus(status.id) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        statuses = _state.value.statuses.filterNot { it.id == status.id },
                    )
                }
        }
    }

    fun prepareEdit(status: UniversalStatus) {
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            val platform = platformFactory.forAccount(account)
            if (!platform.supportsEdit) return@launch
            val source = platform.getStatusSourceText(status.id)
            composeDraftStore.set(
                ComposeDraft(
                    text = source?.text ?: status.text,
                    spoilerText = source?.spoilerText?.ifBlank { null } ?: status.spoilerText,
                    visibility = status.visibility,
                    editingStatusId = status.id,
                )
            )
            _events.tryEmit(ProfileEvent.OpenCompose)
        }
    }

    fun prepareQuote(status: UniversalStatus) {
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            val platform = platformFactory.forAccount(account)
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
            _events.tryEmit(ProfileEvent.OpenCompose)
        }
    }

    fun prepareReply(status: UniversalStatus) {
        viewModelScope.launch {
            val me = accountRepository.getActiveAccount()
            val mentions = buildList {
                add(status.account.id to status.account.acct)
                status.mentions.forEach { add(it.id to it.acct) }
            }
                .filterNot { (id, _) -> me != null && id == me.userId }
                .distinctBy { it.first }
            val text = mentions.joinToString(" ", postfix = " ") { (_, acct) ->
                "@${acct.removePrefix("@")}"
            }
            composeDraftStore.set(
                ComposeDraft(
                    text = text,
                    replyToId = status.id,
                    replyToAuthor = status.account.acct,
                    replyToText = status.text,
                    replyToSpoiler = status.spoilerText,
                    spoilerText = status.spoilerText,
                    visibility = status.visibility,
                )
            )
        }
    }

    private fun mutate(
        status: UniversalStatus,
        block: suspend (PlatformAccount, UniversalStatus) -> UniversalStatus,
    ) {
        viewModelScope.launch {
            val account = accountRepository.getActiveAccount() ?: return@launch
            val platform = platformFactory.forAccount(account)
            runCatching { block(platform, status) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        statuses = _state.value.statuses.map { if (it.id == updated.id) updated else it },
                    )
                }
        }
    }
}

data class ProfileState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val exhausted: Boolean = false,
    val error: String? = null,
    val myUserId: String? = null,
    val isMe: Boolean = false,
    val user: UniversalUser? = null,
    val relationship: Relationship? = null,
    val statuses: List<UniversalStatus> = emptyList(),
    val availableLists: List<UserListInfo> = emptyList(),
    val listMemberships: Set<String> = emptySet(),
) {
    /** True when the viewed user is on a different instance (Mastodon-only). */
    val isRemoteMastodonUser: Boolean get() =
        user?.platform == PlatformType.MASTODON && user.acct.removePrefix("@").contains('@')
}

sealed interface ProfileEvent {
    data object Closed : ProfileEvent
    data object OpenCompose : ProfileEvent
}
