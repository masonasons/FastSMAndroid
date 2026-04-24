package me.masonasons.fastsm.domain.model

import java.time.Instant

data class UniversalMedia(
    val id: String,
    val type: String,
    val url: String,
    val previewUrl: String? = null,
    val description: String? = null,
)

data class UniversalMention(
    val id: String,
    val acct: String,
    val username: String,
    val url: String? = null,
)

data class UniversalStatus(
    val id: String,
    val account: UniversalUser,
    val content: String,
    val text: String,
    val createdAt: Instant,
    val favouritesCount: Int = 0,
    val boostsCount: Int = 0,
    val repliesCount: Int = 0,
    val inReplyToId: String? = null,
    val inReplyToAccountId: String? = null,
    val reblog: UniversalStatus? = null,
    val quote: UniversalStatus? = null,
    val mediaAttachments: List<UniversalMedia> = emptyList(),
    val mentions: List<UniversalMention> = emptyList(),
    val url: String? = null,
    val visibility: Visibility? = null,
    val spoilerText: String? = null,
    val pinned: Boolean = false,
    val favourited: Boolean = false,
    val boosted: Boolean = false,
    val bookmarked: Boolean = false,
    val platform: PlatformType,
    /** Bluesky-only: the AT-URI of the viewer's like record, needed to unlike. */
    val platformLikeUri: String? = null,
    /** Bluesky-only: the AT-URI of the viewer's repost record, needed to unrepost. */
    val platformRepostUri: String? = null,
    /** Bluesky-only: the post's CID, needed as strongRef for likes/reposts/replies. */
    val platformCid: String? = null,
    /** External URLs found in the post — excludes mention/hashtag anchors. */
    val links: List<String> = emptyList(),
)
