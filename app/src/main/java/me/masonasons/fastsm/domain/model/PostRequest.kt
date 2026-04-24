package me.masonasons.fastsm.domain.model

import java.time.Instant

data class PostRequest(
    val text: String,
    val inReplyToId: String? = null,
    val visibility: Visibility? = null,
    val spoilerText: String? = null,
    val mediaIds: List<String> = emptyList(),
    val poll: PollSpec? = null,
    /** If set, request the server to publish at this time instead of immediately (Mastodon). */
    val scheduledAt: Instant? = null,
    /** ID (Mastodon) or AT-URI (Bluesky) of the post being quoted. */
    val quoteStatusId: String? = null,
    /** Bluesky-only: the CID of the quoted post, required for the embed.record strongRef. */
    val quoteStatusCid: String? = null,
)
