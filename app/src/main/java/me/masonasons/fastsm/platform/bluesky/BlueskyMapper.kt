package me.masonasons.fastsm.platform.bluesky

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.masonasons.fastsm.domain.model.NotificationType
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.UniversalNotification
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import java.time.Instant

internal fun BskyProfileDto.toUniversal(): UniversalUser = UniversalUser(
    id = did,
    acct = handle,
    username = handle.substringBefore('.'),
    displayName = displayName?.takeIf { it.isNotBlank() } ?: handle,
    note = description.orEmpty(),
    avatar = avatar,
    header = banner,
    followersCount = followersCount,
    followingCount = followsCount,
    statusesCount = postsCount,
    url = "https://bsky.app/profile/$handle",
    platform = PlatformType.BLUESKY,
)

internal fun BskyProfileBasicDto.toUniversal(): UniversalUser = UniversalUser(
    id = did,
    acct = handle,
    username = handle.substringBefore('.'),
    displayName = displayName?.takeIf { it.isNotBlank() } ?: handle,
    avatar = avatar,
    url = "https://bsky.app/profile/$handle",
    platform = PlatformType.BLUESKY,
)

/**
 * Map a feed-view post to UniversalStatus. Boosts (reason=reasonRepost) are
 * flattened to the Mastodon-style reblog pattern: outer `account` is the
 * booster, `reblog` holds the original post with its real author.
 */
internal fun BskyFeedViewPostDto.toUniversal(): UniversalStatus {
    val inner = post.toUniversal()
    val repostedBy = reason?.takeIf { it.type.endsWith("reasonRepost") }?.by?.toUniversal()
    return if (repostedBy != null) {
        inner.copy(
            account = repostedBy,
            reblog = inner,
        )
    } else inner
}

/**
 * Map a Bluesky notification. For mention/reply/quote we synthesize a
 * UniversalStatus from the notification's own record (the mentioning post);
 * for like/repost the caller passes in a pre-fetched subject post via
 * [subjectPosts]. Follow has no status.
 */
internal fun BskyNotificationDto.toUniversal(
    subjectPosts: Map<String, BskyPostViewDto>,
): UniversalNotification {
    val type = mapBlueskyReason(reason)
    val status: UniversalStatus? = when (type) {
        NotificationType.MENTION, NotificationType.REPLY, NotificationType.QUOTE ->
            synthesizeStatusFromRecord(uri, cid, author, record)
        NotificationType.FAVOURITE, NotificationType.REBLOG ->
            reasonSubject?.let { subjectPosts[it]?.toUniversal() }
        else -> null
    }
    return UniversalNotification(
        id = uri,
        type = type,
        typeRaw = reason,
        createdAt = runCatching { Instant.parse(indexedAt) }.getOrDefault(Instant.EPOCH),
        account = author.toUniversal(),
        status = status,
        platform = PlatformType.BLUESKY,
    )
}

private fun mapBlueskyReason(reason: String): NotificationType = when (reason) {
    "mention" -> NotificationType.MENTION
    "reply" -> NotificationType.REPLY
    "quote" -> NotificationType.QUOTE
    "repost" -> NotificationType.REBLOG
    "like" -> NotificationType.FAVOURITE
    "follow" -> NotificationType.FOLLOW
    else -> NotificationType.OTHER
}

private fun synthesizeStatusFromRecord(
    uri: String,
    cid: String,
    author: BskyProfileBasicDto,
    record: JsonObject,
): UniversalStatus {
    val text = (record["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    val createdAt = (record["createdAt"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: Instant.EPOCH
    val replyParentUri = (record["reply"] as? JsonObject)
        ?.get("parent")?.jsonObject
        ?.get("uri")?.jsonPrimitive?.content
    return UniversalStatus(
        id = uri,
        account = author.toUniversal(),
        content = text,
        text = text,
        createdAt = createdAt,
        inReplyToId = replyParentUri,
        url = "https://bsky.app/profile/${author.handle}/post/${uri.substringAfterLast('/')}",
        platform = PlatformType.BLUESKY,
        platformCid = cid,
    )
}

internal fun BskyPostViewDto.toUniversal(): UniversalStatus {
    val text = (record["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    val createdAt = (record["createdAt"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: indexedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: Instant.EPOCH
    val replyParentUri = (record["reply"] as? JsonObject)
        ?.get("parent")?.jsonObject
        ?.get("uri")?.jsonPrimitive?.content
    // Combine facet-derived links with anything pulled from the visible text —
    // facets aren't mandatory on Bluesky and older/cross-poster posts omit them.
    val links = (
        extractLinksFromFacets(record["facets"] as? JsonArray) + extractLinksFromText(text)
    ).distinct()

    return UniversalStatus(
        id = uri,
        account = author.toUniversal(),
        content = text,
        text = text,
        createdAt = createdAt,
        favouritesCount = likeCount,
        boostsCount = repostCount,
        repliesCount = replyCount,
        inReplyToId = replyParentUri,
        url = "https://bsky.app/profile/${author.handle}/post/${uri.substringAfterLast('/')}",
        favourited = viewer?.like != null,
        boosted = viewer?.repost != null,
        bookmarked = false,
        platform = PlatformType.BLUESKY,
        platformLikeUri = viewer?.like,
        platformRepostUri = viewer?.repost,
        platformCid = cid,
        links = links,
        quote = extractQuotedStatus(embed),
    )
}

/**
 * Pick the quoted post out of a Bluesky `embed` union. Handles both
 * `app.bsky.embed.record#view` (bare quote) and
 * `app.bsky.embed.recordWithMedia#view` (quote with attached images/video —
 * the record lives under a nested `record.record`). Returns null when the
 * embed is an image/video/external link or when the quoted post is blocked,
 * deleted, or otherwise unreadable (viewNotFound / viewBlocked).
 */
private fun extractQuotedStatus(embed: JsonObject?): UniversalStatus? {
    if (embed == null) return null
    val type = (embed["\$type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    val record: JsonObject = (when {
        type?.startsWith("app.bsky.embed.record#view") == true ->
            embed["record"] as? JsonObject
        type?.startsWith("app.bsky.embed.recordWithMedia#view") == true ->
            (embed["record"] as? JsonObject)?.get("record") as? JsonObject
        else -> null
    }) ?: return null
    val recordType = (record["\$type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    // Only #viewRecord carries the actual post payload; notFound/blocked
    // variants give us nothing useful to display.
    if (recordType != "app.bsky.embed.record#viewRecord") return null

    val uri = (record["uri"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
    val cid = (record["cid"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    val authorObj = record["author"] as? JsonObject ?: return null
    val handle = (authorObj["handle"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    val did = (authorObj["did"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    val authorDisplay = (authorObj["displayName"] as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.takeIf { it.isNotBlank() }
        ?: handle
    val innerRecord = record["value"] as? JsonObject ?: return null
    val text = (innerRecord["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    val createdAt = (innerRecord["createdAt"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: Instant.EPOCH

    return UniversalStatus(
        id = uri,
        account = UniversalUser(
            id = did,
            acct = handle,
            username = handle.substringBefore('.'),
            displayName = authorDisplay,
            url = "https://bsky.app/profile/$handle",
            platform = PlatformType.BLUESKY,
        ),
        content = text,
        text = text,
        createdAt = createdAt,
        url = "https://bsky.app/profile/$handle/post/${uri.substringAfterLast('/')}",
        platform = PlatformType.BLUESKY,
        platformCid = cid,
    )
}

private val urlRegex = Regex("""https?://[^\s)]+""")

/** Fallback when facets are missing: scan the post text for bare URLs. */
private fun extractLinksFromText(text: String): List<String> =
    urlRegex.findAll(text)
        .map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']') }
        .toList()

/**
 * Bluesky's `app.bsky.richtext.facet#link` features give us the canonical URL
 * for each link in the post text, no heuristics needed. Mentions/hashtags
 * have different feature $types so they're naturally excluded.
 */
private fun extractLinksFromFacets(facets: JsonArray?): List<String> {
    if (facets == null) return emptyList()
    val out = mutableListOf<String>()
    for (facet in facets) {
        val features = (facet as? JsonObject)?.get("features") as? JsonArray ?: continue
        for (feature in features) {
            val f = feature as? JsonObject ?: continue
            val type = (f["\$type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            if (type == "app.bsky.richtext.facet#link") {
                (f["uri"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.let(out::add)
            }
        }
    }
    return out.distinct()
}
