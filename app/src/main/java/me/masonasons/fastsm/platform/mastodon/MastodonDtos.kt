package me.masonasons.fastsm.platform.mastodon

import kotlinx.serialization.Serializable

@Serializable
data class MastodonAppDto(
    val id: String? = null,
    val name: String? = null,
    val website: String? = null,
    val client_id: String,
    val client_secret: String,
    val redirect_uri: String? = null,
    val vapid_key: String? = null,
)

@Serializable
data class MastodonTokenDto(
    val access_token: String,
    val token_type: String,
    val scope: String,
    val created_at: Long,
)

@Serializable
data class MastodonAccountDto(
    val id: String,
    val username: String,
    val acct: String,
    val display_name: String = "",
    val note: String = "",
    val avatar: String? = null,
    val header: String? = null,
    val followers_count: Int = 0,
    val following_count: Int = 0,
    val statuses_count: Int = 0,
    val url: String? = null,
    val bot: Boolean = false,
    val locked: Boolean = false,
)

@Serializable
data class MastodonMediaDto(
    val id: String,
    val type: String,
    val url: String? = null,
    val preview_url: String? = null,
    val description: String? = null,
)

@Serializable
data class MastodonMentionDto(
    val id: String,
    val acct: String,
    val username: String,
    val url: String? = null,
)

@Serializable
data class MastodonInstanceStatusesConfigDto(
    val max_characters: Int? = null,
)

@Serializable
data class MastodonInstanceConfigurationDto(
    val statuses: MastodonInstanceStatusesConfigDto? = null,
)

@Serializable
data class MastodonInstanceDto(
    val configuration: MastodonInstanceConfigurationDto? = null,
    val max_toot_chars: Int? = null,
)

@Serializable
data class MastodonContextDto(
    val ancestors: List<MastodonStatusDto> = emptyList(),
    val descendants: List<MastodonStatusDto> = emptyList(),
)

@Serializable
data class MastodonListDto(
    val id: String,
    val title: String,
)

@Serializable
data class MastodonTagDto(
    val name: String,
    val url: String? = null,
)

@Serializable
data class MastodonMarkerDto(
    val last_read_id: String,
)

@Serializable
data class MastodonMarkersResponseDto(
    val home: MastodonMarkerDto? = null,
    val notifications: MastodonMarkerDto? = null,
)

@Serializable
data class MastodonSearchResponseDto(
    val accounts: List<MastodonAccountDto> = emptyList(),
    val statuses: List<MastodonStatusDto> = emptyList(),
    val hashtags: List<MastodonTagDto> = emptyList(),
)

@Serializable
data class MastodonRelationshipDto(
    val id: String,
    val following: Boolean = false,
    val requested: Boolean = false,
    val followed_by: Boolean = false,
    val muting: Boolean = false,
    val blocking: Boolean = false,
)

@Serializable
data class MastodonNotificationDto(
    val id: String,
    val type: String,
    val created_at: String,
    val account: MastodonAccountDto,
    val status: MastodonStatusDto? = null,
)

@Serializable
data class MastodonStatusSourceDto(
    val id: String,
    val text: String,
    val spoiler_text: String = "",
)

@Serializable
data class MastodonScheduledStatusDto(
    val id: String,
    val scheduled_at: String,
)

@Serializable
data class MastodonCardDto(
    val url: String,
    val title: String = "",
    val description: String = "",
    val type: String = "",
)

@Serializable
data class MastodonStatusDto(
    val id: String,
    val account: MastodonAccountDto,
    val content: String = "",
    val created_at: String,
    val favourites_count: Int = 0,
    val reblogs_count: Int = 0,
    val replies_count: Int = 0,
    val in_reply_to_id: String? = null,
    val in_reply_to_account_id: String? = null,
    val reblog: MastodonStatusDto? = null,
    val media_attachments: List<MastodonMediaDto> = emptyList(),
    val mentions: List<MastodonMentionDto> = emptyList(),
    val url: String? = null,
    val visibility: String? = null,
    val spoiler_text: String = "",
    val pinned: Boolean = false,
    val favourited: Boolean = false,
    val reblogged: Boolean = false,
    val bookmarked: Boolean = false,
    val card: MastodonCardDto? = null,
    /** Mastodon 4.4+ quote posts. Null when the post doesn't quote anything. */
    val quote: MastodonQuoteDto? = null,
)

@Serializable
data class MastodonQuoteDto(
    /** accepted | pending | rejected | revoked | deleted | unauthorized */
    val state: String = "accepted",
    val quoted_status: MastodonStatusDto? = null,
    val quoted_status_id: String? = null,
)
