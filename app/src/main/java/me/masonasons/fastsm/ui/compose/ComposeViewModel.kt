package me.masonasons.fastsm.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.masonasons.fastsm.data.feedback.FeedbackManager
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.domain.model.PollSpec
import me.masonasons.fastsm.domain.model.PostRequest
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.domain.model.Visibility
import me.masonasons.fastsm.platform.PlatformFactory
import java.time.Instant
import java.util.UUID

class ComposeViewModel(
    private val accountRepository: AccountRepository,
    private val platformFactory: PlatformFactory,
    private val draftStore: ComposeDraftStore,
    private val feedback: FeedbackManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ComposeState())
    val state: StateFlow<ComposeState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ComposeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ComposeEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val active = accountRepository.activeAccountId.value
                ?.let { accountRepository.getById(it) }
                ?: accountRepository.getAll().firstOrNull()
            if (active == null) {
                _state.value = _state.value.copy(error = "No account signed in")
                return@launch
            }
            val platform = platformFactory.forAccount(active)
            val draft = draftStore.consume()
            _state.value = _state.value.copy(
                accountLabel = active.label,
                platformName = active.platform.displayName,
                supportsVisibility = platform.supportsVisibility,
                supportsContentWarning = platform.supportsContentWarning,
                supportsMedia = platform.supportsMedia,
                maxMediaAttachments = platform.maxMediaAttachments,
                supportsScheduling = platform.supportsScheduling,
                text = draft?.text.orEmpty(),
                spoilerText = draft?.spoilerText.orEmpty(),
                visibility = draft?.visibility ?: Visibility.PUBLIC,
                replyToId = draft?.replyToId,
                replyToAuthor = draft?.replyToAuthor,
                replyToText = draft?.replyToText,
                replyToSpoiler = draft?.replyToSpoiler,
                editingStatusId = draft?.editingStatusId,
                quoteStatusId = draft?.quoteStatusId,
                quoteStatusCid = draft?.quoteStatusCid,
                quoteAuthor = draft?.quoteAuthor,
                quoteText = draft?.quoteText,
            )
            runCatching { platform.getMaxPostChars() }
                .onSuccess { limit -> _state.value = _state.value.copy(maxChars = limit) }
        }
    }

    fun onTextChanged(value: String) {
        _state.value = _state.value.copy(text = value)
    }

    fun onVisibilityChanged(value: Visibility) {
        _state.value = _state.value.copy(visibility = value)
    }

    fun onSpoilerChanged(value: String) {
        _state.value = _state.value.copy(spoilerText = value)
    }

    fun setPoll(poll: PollSpec?) {
        _state.value = _state.value.copy(poll = poll)
    }

    fun setScheduledAt(at: Instant?) {
        _state.value = _state.value.copy(scheduledAt = at)
    }

    private var mentionJob: Job? = null

    /**
     * Search for users matching [query] to mention. Debounces by ~250ms so
     * rapid typing doesn't hammer the server — each new query cancels the
     * previous inflight job.
     */
    fun searchMentions(query: String) {
        mentionJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(mentionQuery = query, mentionResults = emptyList(), mentionSearching = false)
            return
        }
        _state.value = _state.value.copy(mentionQuery = query, mentionSearching = true)
        mentionJob = viewModelScope.launch {
            delay(250)
            val account = accountRepository.activeAccountId.value
                ?.let { accountRepository.getById(it) } ?: return@launch
            val platform = platformFactory.forAccount(account)
            val results = runCatching { platform.search(query).users.take(10) }
                .getOrDefault(emptyList())
            _state.value = _state.value.copy(mentionResults = results, mentionSearching = false)
        }
    }

    fun clearMentionSearch() {
        mentionJob?.cancel()
        _state.value = _state.value.copy(mentionQuery = "", mentionResults = emptyList(), mentionSearching = false)
    }

    /**
     * Begin uploading a media attachment. A local-id placeholder is inserted
     * synchronously so the UI can render a "Uploading…" entry; when the
     * server returns an id we update the entry in place.
     */
    fun addMedia(bytes: ByteArray, filename: String, mime: String) {
        val current = _state.value
        if (current.attachments.size >= current.maxMediaAttachments) {
            _state.value = current.copy(error = "Up to ${current.maxMediaAttachments} attachments allowed")
            return
        }
        val localId = UUID.randomUUID().toString()
        _state.value = current.copy(
            attachments = current.attachments + AttachedMedia(
                localId = localId,
                filename = filename,
                serverMediaId = null,
                uploading = true,
                error = null,
                description = "",
                previewUrl = null,
            ),
            error = null,
        )
        viewModelScope.launch {
            val account = accountRepository.activeAccountId.value
                ?.let { accountRepository.getById(it) } ?: return@launch
            val platform = platformFactory.forAccount(account)
            runCatching { platform.uploadMedia(bytes, filename, mime, null) }
                .onSuccess { result ->
                    updateAttachment(localId) {
                        it.copy(
                            serverMediaId = result.mediaId,
                            previewUrl = result.previewUrl,
                            uploading = false,
                        )
                    }
                }
                .onFailure { e ->
                    updateAttachment(localId) {
                        it.copy(uploading = false, error = e.message ?: "Upload failed")
                    }
                }
        }
    }

    fun removeAttachment(localId: String) {
        _state.value = _state.value.copy(
            attachments = _state.value.attachments.filterNot { it.localId == localId },
        )
    }

    fun updateAttachmentDescription(localId: String, description: String) {
        updateAttachment(localId) { it.copy(description = description) }
    }

    private inline fun updateAttachment(localId: String, block: (AttachedMedia) -> AttachedMedia) {
        _state.value = _state.value.copy(
            attachments = _state.value.attachments.map {
                if (it.localId == localId) block(it) else it
            },
        )
    }

    fun submit() {
        val state = _state.value
        if (state.busy) return
        if (state.text.isBlank() && state.attachments.isEmpty()) {
            _state.value = state.copy(error = "Post is empty")
            return
        }
        if (state.attachments.any { it.uploading }) {
            _state.value = state.copy(error = "Attachments are still uploading")
            return
        }
        if (state.scheduledAt != null) {
            val minFuture = Instant.now().plusSeconds(5 * 60)
            if (state.scheduledAt.isBefore(minFuture)) {
                _state.value = state.copy(error = "Scheduled time must be at least 5 minutes in the future")
                return
            }
        }
        _state.value = state.copy(busy = true, error = null)
        viewModelScope.launch {
            val active = accountRepository.activeAccountId.value
                ?.let { accountRepository.getById(it) }
                ?: accountRepository.getAll().firstOrNull()
            if (active == null) {
                _state.value = state.copy(busy = false, error = "No account signed in")
                return@launch
            }
            val platform = platformFactory.forAccount(active)
            val attachments = state.attachments.mapNotNull { it.serverMediaId?.let { id -> it to id } }
            // Push any pending alt-text changes to the server before posting —
            // the Mastodon media record retains the description across posts.
            attachments.forEach { (att, id) ->
                if (att.description.isNotBlank()) {
                    runCatching { platform.updateMediaDescription(id, att.description) }
                }
            }
            val request = PostRequest(
                text = state.text,
                inReplyToId = state.replyToId,
                visibility = state.visibility.takeIf { platform.supportsVisibility },
                spoilerText = state.spoilerText.takeIf {
                    platform.supportsContentWarning && it.isNotBlank()
                },
                mediaIds = attachments.map { (_, id) -> id },
                poll = state.poll,
                scheduledAt = state.scheduledAt,
                quoteStatusId = state.quoteStatusId,
                quoteStatusCid = state.quoteStatusCid,
            )
            runCatching {
                when {
                    state.isEdit -> platform.editStatus(state.editingStatusId!!, request)
                    state.scheduledAt != null -> platform.schedulePost(request)
                    else -> platform.post(request)
                }
            }.onSuccess {
                val sent = if (state.replyToId != null) me.masonasons.fastsm.domain.model.AppEvent.ReplySent
                    else me.masonasons.fastsm.domain.model.AppEvent.PostSent
                feedback.emit(sent)
                _state.value = ComposeState()
                _events.tryEmit(ComposeEvent.Posted)
            }.onFailure { e ->
                val msg = e.message ?: e.javaClass.simpleName
                feedback.emit(me.masonasons.fastsm.domain.model.AppEvent.PostFailed(msg))
                _state.value = _state.value.copy(
                    busy = false,
                    error = msg,
                )
            }
        }
    }
}

data class AttachedMedia(
    val localId: String,
    val filename: String,
    val serverMediaId: String?,
    val uploading: Boolean,
    val error: String?,
    val description: String,
    val previewUrl: String?,
)

data class ComposeState(
    val accountLabel: String = "",
    val platformName: String = "",
    val text: String = "",
    val spoilerText: String = "",
    val visibility: Visibility = Visibility.PUBLIC,
    val maxChars: Int = 500,
    val supportsVisibility: Boolean = true,
    val supportsContentWarning: Boolean = true,
    val supportsMedia: Boolean = false,
    val maxMediaAttachments: Int = 4,
    val attachments: List<AttachedMedia> = emptyList(),
    val poll: PollSpec? = null,
    val supportsScheduling: Boolean = false,
    val scheduledAt: Instant? = null,
    val mentionQuery: String = "",
    val mentionResults: List<UniversalUser> = emptyList(),
    val mentionSearching: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val replyToId: String? = null,
    val replyToAuthor: String? = null,
    val replyToText: String? = null,
    val replyToSpoiler: String? = null,
    val editingStatusId: String? = null,
    val quoteStatusId: String? = null,
    val quoteStatusCid: String? = null,
    val quoteAuthor: String? = null,
    val quoteText: String? = null,
) {
    val isEdit: Boolean get() = editingStatusId != null
    val isQuote: Boolean get() = quoteStatusId != null
    val remaining: Int get() = maxChars - text.length
    val canSubmit: Boolean get() = !busy &&
        (text.isNotBlank() || attachments.isNotEmpty()) &&
        remaining >= 0 &&
        attachments.none { it.uploading }
}

sealed interface ComposeEvent {
    data object Posted : ComposeEvent
}
