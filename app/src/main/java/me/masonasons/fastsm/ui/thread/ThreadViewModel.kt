package me.masonasons.fastsm.ui.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.platform.PlatformAccount
import me.masonasons.fastsm.platform.PlatformFactory
import me.masonasons.fastsm.ui.compose.ComposeDraft
import me.masonasons.fastsm.ui.compose.ComposeDraftStore

class ThreadViewModel(
    private val rootStatusId: String,
    private val accountRepository: AccountRepository,
    private val platformFactory: PlatformFactory,
    private val composeDraftStore: ComposeDraftStore,
    private val feedback: me.masonasons.fastsm.data.feedback.FeedbackManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ThreadState())
    val state: StateFlow<ThreadState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ThreadEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ThreadEvent> = _events.asSharedFlow()

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
            )
            runCatching {
                coroutineScope {
                    val targetDeferred = async { platform.getStatus(rootStatusId) }
                    val ctxDeferred = async { platform.getStatusContext(rootStatusId) }
                    val target = targetDeferred.await()
                    val ctx = ctxDeferred.await()
                    Triple(ctx.ancestors, target, ctx.descendants)
                }
            }
                .onSuccess { (ancestors, target, descendants) ->
                    _state.value = ThreadState(
                        loading = false,
                        myUserId = account.userId,
                        ancestors = ancestors,
                        target = target,
                        descendants = descendants,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: e.javaClass.simpleName,
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
                    if (status.id == _state.value.target?.id) {
                        _state.value = _state.value.copy(closed = true)
                    } else {
                        removeStatus(status.id)
                    }
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
            _events.tryEmit(ThreadEvent.OpenCompose)
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
            _events.tryEmit(ThreadEvent.OpenCompose)
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
            val text = mentions.joinToString(separator = " ", postfix = " ") { (_, acct) ->
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
                .onSuccess { updated -> replaceStatus(updated) }
        }
    }

    private fun replaceStatus(updated: UniversalStatus) {
        val s = _state.value
        _state.value = s.copy(
            ancestors = s.ancestors.map { if (it.id == updated.id) updated else it },
            target = if (s.target?.id == updated.id) updated else s.target,
            descendants = s.descendants.map { if (it.id == updated.id) updated else it },
        )
    }

    private fun removeStatus(statusId: String) {
        val s = _state.value
        _state.value = s.copy(
            ancestors = s.ancestors.filterNot { it.id == statusId },
            descendants = s.descendants.filterNot { it.id == statusId },
        )
    }
}

data class ThreadState(
    val loading: Boolean = false,
    val error: String? = null,
    val myUserId: String? = null,
    val ancestors: List<UniversalStatus> = emptyList(),
    val target: UniversalStatus? = null,
    val descendants: List<UniversalStatus> = emptyList(),
    val closed: Boolean = false,
)

sealed interface ThreadEvent {
    data object OpenCompose : ThreadEvent
}
