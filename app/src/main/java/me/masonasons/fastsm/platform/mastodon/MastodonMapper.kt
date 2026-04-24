package me.masonasons.fastsm.platform.mastodon

import me.masonasons.fastsm.domain.model.NotificationType
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.Relationship
import me.masonasons.fastsm.domain.model.UniversalMedia
import me.masonasons.fastsm.domain.model.UniversalMention
import me.masonasons.fastsm.domain.model.UniversalNotification
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.domain.model.Visibility
import java.time.Instant

internal fun MastodonAccountDto.toUniversal(): UniversalUser = UniversalUser(
    id = id,
    acct = acct,
    username = username,
    displayName = display_name.ifBlank { username },
    note = note,
    avatar = avatar,
    header = header,
    followersCount = followers_count,
    followingCount = following_count,
    statusesCount = statuses_count,
    url = url,
    bot = bot,
    locked = locked,
    platform = PlatformType.MASTODON,
)

internal fun MastodonMediaDto.toUniversal(): UniversalMedia = UniversalMedia(
    id = id,
    type = type,
    url = url.orEmpty(),
    previewUrl = preview_url,
    description = description,
)

internal fun MastodonMentionDto.toUniversal(): UniversalMention = UniversalMention(
    id = id,
    acct = acct,
    username = username,
    url = url,
)

internal fun MastodonRelationshipDto.toUniversal(): Relationship = Relationship(
    userId = id,
    following = following,
    requested = requested,
    followedBy = followed_by,
    muting = muting,
    blocking = blocking,
)

internal fun MastodonNotificationDto.toUniversal(): UniversalNotification = UniversalNotification(
    id = id,
    type = NotificationType.fromWire(type),
    typeRaw = type,
    createdAt = runCatching { Instant.parse(created_at) }.getOrDefault(Instant.EPOCH),
    account = account.toUniversal(),
    status = status?.toUniversal(),
    platform = PlatformType.MASTODON,
)

internal fun MastodonStatusDto.toUniversal(): UniversalStatus {
    // Resolve the quote first so we can scrub RE:/URL markers out of the body
    // text. Those markers only exist to accommodate pre-native-quote clients;
    // once we have a proper quote object, they're just noise for TalkBack.
    val quoted = quote?.takeIf { it.state == "accepted" }?.quoted_status?.toUniversal()
    val rawText = HtmlStrip.toPlainText(content)
    val cleanText =
        if (quoted != null) HtmlStrip.stripQuoteMarkers(rawText, quoted.url) else rawText
    return UniversalStatus(
    id = id,
    account = account.toUniversal(),
    content = content,
    text = cleanText,
    createdAt = runCatching { Instant.parse(created_at) }.getOrDefault(Instant.EPOCH),
    favouritesCount = favourites_count,
    boostsCount = reblogs_count,
    repliesCount = replies_count,
    inReplyToId = in_reply_to_id,
    inReplyToAccountId = in_reply_to_account_id,
    reblog = reblog?.toUniversal(),
    // Only surface the quote when the server says it's actually viewable —
    // pending/rejected/deleted/unauthorized states give us no readable
    // quoted_status to render.
    quote = quoted,
    mediaAttachments = media_attachments.map { it.toUniversal() },
    mentions = mentions.map { it.toUniversal() },
    url = url,
    visibility = Visibility.fromWire(visibility),
    spoilerText = spoiler_text.ifBlank { null },
    pinned = pinned,
    favourited = favourited,
    boosted = reblogged,
    bookmarked = bookmarked,
    platform = PlatformType.MASTODON,
    // Combine anchor links with the card URL (if the post has a link-preview
    // card, the URL often isn't in the content HTML).
    links = (HtmlStrip.extractLinks(content) + listOfNotNull(card?.url)).distinct(),
    )
}
