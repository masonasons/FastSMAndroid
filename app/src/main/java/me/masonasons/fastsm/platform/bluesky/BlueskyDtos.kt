package me.masonasons.fastsm.platform.bluesky

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// --- XRPC error envelope ---

@Serializable
data class BskyXrpcError(
    val error: String? = null,
    val message: String? = null,
)

// --- Session ---

@Serializable
data class CreateSessionRequest(
    val identifier: String,
    val password: String,
)

@Serializable
data class SessionDto(
    val did: String,
    val handle: String,
    val accessJwt: String,
    val refreshJwt: String,
    val email: String? = null,
)

// --- Profile ---

@Serializable
data class BskyProfileDto(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val followersCount: Int = 0,
    val followsCount: Int = 0,
    val postsCount: Int = 0,
    val viewer: BskyProfileViewerDto? = null,
)

@Serializable
data class BskyProfileViewerDto(
    val muted: Boolean = false,
    val blockedBy: Boolean = false,
    val blocking: String? = null,
    val following: String? = null,
    val followedBy: String? = null,
)

// --- Feed ---

@Serializable
data class BskyFeedResponseDto(
    val feed: List<BskyFeedViewPostDto> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class BskyFeedViewPostDto(
    val post: BskyPostViewDto,
    val reason: BskyReasonDto? = null,
    val reply: BskyReplyRefDto? = null,
)

@Serializable
data class BskyReasonDto(
    @SerialName("\$type") val type: String = "",
    val by: BskyProfileBasicDto? = null,
)

@Serializable
data class BskyReplyRefDto(
    val root: BskyPostViewDto? = null,
    val parent: BskyPostViewDto? = null,
)

@Serializable
data class BskyPostViewDto(
    val uri: String,
    val cid: String,
    val author: BskyProfileBasicDto,
    val record: JsonObject,
    val replyCount: Int = 0,
    val repostCount: Int = 0,
    val likeCount: Int = 0,
    val indexedAt: String? = null,
    val viewer: BskyPostViewerDto? = null,
    /**
     * The `embed` union — for quotes this carries either
     * `app.bsky.embed.record#view` (pure quote) or
     * `app.bsky.embed.recordWithMedia#view` (quote with images/video attached).
     * Left as a raw JsonObject because the union's shape varies; the mapper
     * picks through it at parse time.
     */
    val embed: JsonObject? = null,
)

@Serializable
data class BskyPostViewerDto(
    val like: String? = null,
    val repost: String? = null,
)

@Serializable
data class BskyProfileBasicDto(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
)

// --- Thread ---

@Serializable
data class BskyThreadResponseDto(
    val thread: JsonElement,
)

// --- CreateRecord ---

@Serializable
data class CreateRecordRequest(
    val repo: String,
    val collection: String,
    val record: JsonObject,
)

@Serializable
data class CreateRecordResponse(
    val uri: String,
    val cid: String,
)

@Serializable
data class DeleteRecordRequest(
    val repo: String,
    val collection: String,
    val rkey: String,
)

@Serializable
data class StrongRef(
    val uri: String,
    val cid: String,
)

@Serializable
data class GetPostsResponse(
    val posts: List<BskyPostViewDto> = emptyList(),
)

@Serializable
data class ResolveHandleResponse(
    val did: String,
)

// --- Search ---

@Serializable
data class BskySearchPostsResponseDto(
    val posts: List<BskyPostViewDto> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class BskySearchActorsResponseDto(
    val actors: List<BskyProfileBasicDto> = emptyList(),
    val cursor: String? = null,
)

// --- Notifications ---

@Serializable
data class BskyNotificationsResponseDto(
    val notifications: List<BskyNotificationDto> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class BskyNotificationDto(
    val uri: String,
    val cid: String,
    val author: BskyProfileBasicDto,
    val reason: String,
    val reasonSubject: String? = null,
    val record: JsonObject,
    val isRead: Boolean = false,
    val indexedAt: String,
)
