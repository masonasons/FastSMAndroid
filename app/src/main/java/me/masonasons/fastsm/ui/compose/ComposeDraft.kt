package me.masonasons.fastsm.ui.compose

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.masonasons.fastsm.domain.model.Visibility

/**
 * Prefill for the compose screen — a reply, an edit, or an empty draft.
 * [HomeViewModel] / [ProfileViewModel] / [ThreadViewModel] write here before
 * navigating to compose; [ComposeViewModel] reads it on init and clears the
 * store so the next compose starts empty.
 */
data class ComposeDraft(
    val text: String,
    val replyToId: String? = null,
    val replyToAuthor: String? = null,
    /** Plain-text body of the post being replied to, for the parent preview. */
    val replyToText: String? = null,
    val replyToSpoiler: String? = null,
    val spoilerText: String? = null,
    val visibility: Visibility? = null,
    /** When non-null, compose acts as an edit of this existing status (Mastodon only). */
    val editingStatusId: String? = null,
    /** When non-null, compose attaches this post as a quote. */
    val quoteStatusId: String? = null,
    val quoteStatusCid: String? = null,
    val quoteAuthor: String? = null,
    val quoteText: String? = null,
)

class ComposeDraftStore {
    private val _pending = MutableStateFlow<ComposeDraft?>(null)
    val pending: StateFlow<ComposeDraft?> = _pending.asStateFlow()

    fun set(draft: ComposeDraft) {
        _pending.value = draft
    }

    fun consume(): ComposeDraft? {
        val current = _pending.value
        _pending.value = null
        return current
    }
}
