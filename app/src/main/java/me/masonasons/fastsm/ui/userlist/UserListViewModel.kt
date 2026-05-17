package me.masonasons.fastsm.ui.userlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.masonasons.fastsm.data.feedback.FeedbackManager
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.domain.model.AppEvent
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.Relationship
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.platform.PlatformAccount
import me.masonasons.fastsm.platform.PlatformFactory

enum class UserListKind { Followers, Following }

/**
 * Secondary ordering applied on top of the server's default order. Each mode
 * pulls "matching" users to the top while preserving the original order
 * within each bucket. Sort is best-effort: when relationship data is unknown
 * for a user (Bluesky, or before the Mastodon batch returns), they fall to
 * the "unmatched" bucket.
 */
enum class SortMode(val label: String) {
    Default("Default"),
    FollowingFirst("Following first"),
    BoostsHiddenFirst("Boosts hidden first"),
    MutedFirst("Muted first"),
    BlockedFirst("Blocked first"),
}

private const val PAGE_SIZE = 40

class UserListViewModel(
    private val accountId: Long,
    private val targetUserId: String,
    private val kind: UserListKind,
    private val accountRepository: AccountRepository,
    private val platformFactory: PlatformFactory,
    private val feedback: FeedbackManager,
) : ViewModel() {

    private val _state = MutableStateFlow(UserListState(kind = kind))
    val state: StateFlow<UserListState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UserListEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UserListEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val platform = platform() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            val isMe = targetUserId == platform.account.userId
            runCatching {
                val targetUser = runCatching { platform.getUser(targetUserId) }.getOrNull()
                val page = fetchPage(platform, cursor = null)
                Triple(targetUser, page.first, page.second)
            }.onSuccess { (target, users, nextCursor) ->
                _state.value = _state.value.copy(
                    loading = false,
                    targetUser = target,
                    isMe = isMe,
                    platform = platform.platform,
                    users = users,
                    relationships = emptyMap(),
                    cursor = nextCursor,
                    exhausted = nextCursor == null,
                )
                fetchRelationships(platform, users.map { it.id })
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.exhausted) return
        val cursor = current.cursor ?: return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val platform = platform() ?: return@launch
            runCatching { fetchPage(platform, cursor) }
                .onSuccess { (more, nextCursor) ->
                    val merged = (_state.value.users + more).distinctBy { it.id }
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        users = merged,
                        cursor = nextCursor,
                        exhausted = nextCursor == null,
                    )
                    val newIds = more.map { it.id } - _state.value.relationships.keys
                    fetchRelationships(platform, newIds)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        error = e.message ?: e.javaClass.simpleName,
                    )
                }
        }
    }

    private suspend fun fetchPage(platform: PlatformAccount, cursor: String?): Pair<List<UniversalUser>, String?> {
        val page = when (kind) {
            UserListKind.Followers -> platform.getFollowers(targetUserId, PAGE_SIZE, cursor)
            UserListKind.Following -> platform.getFollowing(targetUserId, PAGE_SIZE, cursor)
        }
        return page.users to page.nextCursor
    }

    /**
     * Mastodon: one batched relationships call per page; result is merged into
     * the local map. Bluesky: silently no-op (the default impl would do N
     * round-trips). The UI initializes button labels from a per-(kind, isMe)
     * default and tolerates missing entries.
     */
    private fun fetchRelationships(platform: PlatformAccount, userIds: List<String>) {
        if (platform.platform != PlatformType.MASTODON || userIds.isEmpty()) return
        viewModelScope.launch {
            runCatching { platform.getRelationships(userIds) }
                .onSuccess { fresh ->
                    if (fresh.isEmpty()) return@onSuccess
                    _state.value = _state.value.copy(
                        relationships = _state.value.relationships + fresh,
                    )
                }
        }
    }

    fun toggleFollow(user: UniversalUser) {
        viewModelScope.launch {
            val platform = platform() ?: return@launch
            val wasFollowing = isFollowing(user.id)
            runCatching {
                if (wasFollowing) platform.unfollow(user.id) else platform.follow(user.id)
            }.onSuccess { newRel ->
                feedback.emit(if (wasFollowing) AppEvent.Unfollowed else AppEvent.Followed)
                updateRelationship(user.id, newRel)
            }.onFailure { e -> reportError("Could not update follow", e) }
        }
    }

    fun setShowReblogs(user: UniversalUser, show: Boolean) {
        viewModelScope.launch {
            val platform = platform() ?: return@launch
            if (platform.platform != PlatformType.MASTODON) return@launch
            runCatching { platform.setShowReblogs(user.id, show) }
                .onSuccess { rel -> if (rel != null) updateRelationship(user.id, rel) }
                .onFailure { e -> reportError("Could not change boost setting", e) }
        }
    }

    fun toggleMute(user: UniversalUser) {
        viewModelScope.launch {
            val platform = platform() ?: return@launch
            val wasMuting = isMuting(user.id)
            runCatching { if (wasMuting) platform.unmute(user.id) else platform.mute(user.id) }
                .onSuccess { rel -> updateRelationship(user.id, rel) }
                .onFailure { e -> reportError("Could not update mute", e) }
        }
    }

    fun toggleBlock(user: UniversalUser) {
        viewModelScope.launch {
            val platform = platform() ?: return@launch
            val wasBlocking = isBlocking(user.id)
            runCatching { if (wasBlocking) platform.unblock(user.id) else platform.block(user.id) }
                .onSuccess { rel -> updateRelationship(user.id, rel) }
                .onFailure { e -> reportError("Could not update block", e) }
        }
    }

    fun setSortMode(mode: SortMode) {
        _state.value = _state.value.copy(sortMode = mode)
    }

    fun setEditMode(on: Boolean) {
        _state.value = _state.value.copy(
            editMode = on,
            selected = if (on) _state.value.selected else emptySet(),
        )
    }

    fun toggleSelection(userId: String) {
        val s = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (userId in s) s - userId else s + userId,
        )
    }

    fun selectAll() {
        _state.value = _state.value.copy(
            selected = _state.value.users.map { it.id }.toSet(),
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    /** Follow every currently selected user that we aren't already following. */
    fun batchFollow() = runBatch { platform, user ->
        if (!isFollowing(user.id)) {
            val rel = platform.follow(user.id)
            updateRelationship(user.id, rel)
        }
    }

    /** Unfollow every currently selected user that we are following. */
    fun batchUnfollow() = runBatch { platform, user ->
        if (isFollowing(user.id)) {
            val rel = platform.unfollow(user.id)
            updateRelationship(user.id, rel)
        }
    }

    private fun runBatch(action: suspend (PlatformAccount, UniversalUser) -> Unit) {
        val selectedIds = _state.value.selected
        if (selectedIds.isEmpty() || _state.value.batchInProgress) return
        _state.value = _state.value.copy(batchInProgress = true)
        viewModelScope.launch {
            val platform = platform() ?: run {
                _state.value = _state.value.copy(batchInProgress = false)
                return@launch
            }
            val targets = _state.value.users.filter { it.id in selectedIds }
            var failures = 0
            for (user in targets) {
                runCatching { action(platform, user) }.onFailure { failures += 1 }
            }
            _state.value = _state.value.copy(
                batchInProgress = false,
                selected = emptySet(),
                editMode = false,
                error = if (failures > 0) "Batch finished with $failures failure(s)" else null,
            )
        }
    }

    private fun updateRelationship(userId: String, rel: Relationship) {
        _state.value = _state.value.copy(
            relationships = _state.value.relationships + (userId to rel),
        )
    }

    private fun reportError(prefix: String, e: Throwable) {
        _state.value = _state.value.copy(
            error = "$prefix: ${e.message ?: e.javaClass.simpleName}",
        )
    }

    private suspend fun platform(): PlatformAccount? {
        val account = accountRepository.getById(accountId) ?: return null
        return platformFactory.forAccount(account)
    }

    /**
     * Default-state probe: when we don't have a real relationship cached
     * (Bluesky, or before the Mastodon batch returns), assume "Following"
     * tab on self means we follow every listed user. Anything else: unknown.
     */
    private fun isFollowing(userId: String): Boolean {
        _state.value.relationships[userId]?.let { return it.following }
        return _state.value.isMe && kind == UserListKind.Following
    }

    private fun isMuting(userId: String): Boolean =
        _state.value.relationships[userId]?.muting ?: false

    private fun isBlocking(userId: String): Boolean =
        _state.value.relationships[userId]?.blocking ?: false
}

data class UserListState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val exhausted: Boolean = false,
    val error: String? = null,
    val targetUser: UniversalUser? = null,
    val users: List<UniversalUser> = emptyList(),
    /** May be partial — UI must tolerate missing entries. */
    val relationships: Map<String, Relationship> = emptyMap(),
    val cursor: String? = null,
    val kind: UserListKind = UserListKind.Followers,
    val platform: PlatformType = PlatformType.MASTODON,
    val isMe: Boolean = false,
    val editMode: Boolean = false,
    val selected: Set<String> = emptySet(),
    val batchInProgress: Boolean = false,
    val sortMode: SortMode = SortMode.Default,
)

sealed interface UserListEvent {
    data object Closed : UserListEvent
}
