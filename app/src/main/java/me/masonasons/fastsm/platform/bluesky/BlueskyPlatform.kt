package me.masonasons.fastsm.platform.bluesky

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.masonasons.fastsm.domain.model.Account
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.PostRequest
import me.masonasons.fastsm.domain.model.Relationship
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.masonasons.fastsm.domain.model.SearchResults
import me.masonasons.fastsm.domain.model.StatusContext
import me.masonasons.fastsm.domain.model.TimelineEvent
import me.masonasons.fastsm.domain.model.UniversalNotification
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.domain.model.UserListInfo
import me.masonasons.fastsm.platform.PlatformAccount
import java.time.Instant
import java.time.format.DateTimeFormatter

class BlueskyPlatform(
    override val account: Account,
    httpClient: HttpClient,
    accessJwtProvider: () -> String?,
    private val authCoordinator: BlueskyAuthCoordinator,
) : PlatformAccount {

    private val pdsBase: String = account.instance.ifBlank { BlueskyApi.DEFAULT_PDS }

    private val api = BlueskyApi(
        httpClient = httpClient,
        pdsBase = pdsBase,
        accessJwtProvider = accessJwtProvider,
    )

    /**
     * Run a call, and if it fails with an expired-token signal, refresh the JWT
     * once and retry. AT Protocol returns HTTP 400 + `{"error":"ExpiredToken"}`
     * for expired access JWTs — not 401 — so we have to inspect the XRPC error
     * body. The refresh itself is deduped in [BlueskyAuthCoordinator] so
     * parallel expiries only trigger one network round-trip.
     */
    private suspend fun <T> authed(call: suspend () -> T): T = try {
        call()
    } catch (e: ResponseException) {
        if (isTokenExpired(e) && authCoordinator.refresh(account.id, pdsBase)) call() else throw e
    }

    private suspend fun isTokenExpired(e: ResponseException): Boolean {
        if (e.response.status == HttpStatusCode.Unauthorized) return true
        if (e.response.status != HttpStatusCode.BadRequest) return false
        val error = runCatching { e.response.body<BskyXrpcError>() }.getOrNull()?.error
        return error == "ExpiredToken" || error == "InvalidToken"
    }

    override val platform: PlatformType = PlatformType.BLUESKY
    override val supportsVisibility: Boolean = false
    override val supportsContentWarning: Boolean = false
    // Bluesky's getTimeline / getAuthorFeed / searchPosts all cap at 100 per
    // page — fetching that wide means fewer round-trips for the same scroll
    // distance on data-heavy accounts.
    override val maxPageSize: Int = 100

    override suspend fun getMaxPostChars(): Int = 300

    override suspend fun verifyCredentials(): UniversalUser = authed {
        api.getProfile(account.userId).toUniversal()
    }

    override suspend fun getHomeTimeline(limit: Int, maxId: String?): List<UniversalStatus> = authed {
        api.getTimeline(limit, maxId).feed.map { it.toUniversal() }
    }

    override suspend fun getLocalTimeline(limit: Int, maxId: String?): List<UniversalStatus> =
        emptyList()

    override suspend fun getFederatedTimeline(limit: Int, maxId: String?): List<UniversalStatus> =
        emptyList()

    // Bluesky isn't a federation-of-instances the way Mastodon is; remote
    // instance timelines don't map. A "remote user" on Bluesky is just a
    // user — handle-based, no different from any other Bluesky profile.
    override suspend fun getRemoteInstanceTimeline(
        instance: String,
        localOnly: Boolean,
        limit: Int,
        maxId: String?,
    ): List<UniversalStatus> = emptyList()

    override suspend fun getRemoteUserTimeline(
        instance: String,
        acct: String,
        limit: Int,
        maxId: String?,
    ): List<UniversalStatus> = getUserStatuses(acct.removePrefix("@"), limit, maxId)

    // Bluesky has no native bookmarks.
    override suspend fun getBookmarks(limit: Int, maxId: String?): List<UniversalStatus> = emptyList()

    // Bluesky's "my favourites" is the viewer's like feed.
    override suspend fun getFavourites(limit: Int, maxId: String?): List<UniversalStatus> = authed {
        api.getActorLikes(account.userId, limit, maxId).feed.map { it.toUniversal() }
    }

    override suspend fun getUserLists(): List<UserListInfo> = emptyList()

    override suspend fun getListTimeline(
        listId: String,
        limit: Int,
        maxId: String?,
    ): List<UniversalStatus> = emptyList()

    override suspend fun getHashtagTimeline(
        tag: String,
        limit: Int,
        maxId: String?,
    ): List<UniversalStatus> = authed {
        // Bluesky has no dedicated hashtag timeline endpoint; use searchPosts
        // with a `#tag` query. Cursor-based pagination, not max-id.
        val q = "#${tag.removePrefix("#")}"
        api.searchPosts(q, limit, maxId).posts.map { it.toUniversal() }
    }

    override suspend fun getListsContainingUser(userId: String): List<UserListInfo> = emptyList()
    override suspend fun addUserToList(listId: String, userId: String): Boolean = false
    override suspend fun removeUserFromList(listId: String, userId: String): Boolean = false

    override suspend fun search(query: String): SearchResults {
        if (query.isBlank()) return SearchResults()
        val actors = runCatching { api.searchActors(query, 10) }.getOrNull()?.actors.orEmpty()
        val posts = runCatching { api.searchPosts(query, 10, cursor = null) }.getOrNull()?.posts.orEmpty()
        return SearchResults(
            users = actors.map { it.toUniversal() },
            posts = posts.map { it.toUniversal() },
            hashtags = emptyList(),
        )
    }

    // Bluesky's firehose is server-infra-shaped (every event from every user);
    // wiring it into a per-user home feed is a larger job. Skip for now — the
    // home tab falls back to pull-to-refresh / periodic reloads.
    override fun streamEvents(): Flow<TimelineEvent> = emptyFlow()

    // Bluesky has no equivalent per-timeline server-side read marker.
    override suspend fun getHomeMarker(): String? = null
    override suspend fun setHomeMarker(statusId: String): Boolean = false

    override suspend fun getStatus(statusId: String): UniversalStatus = authed {
        val posts = api.getPosts(listOf(statusId)).posts
        val post = posts.firstOrNull()
            ?: error("Post not found: $statusId")
        post.toUniversal()
    }

    override suspend fun getStatusContext(statusId: String): StatusContext = authed {
        val response = api.getPostThread(statusId)
        BlueskyThreadFlattener.flatten(response.thread, statusId)
    }

    override suspend fun getNotifications(limit: Int, maxId: String?): List<UniversalNotification> = authed {
        val response = api.listNotifications(limit, maxId)
        // Batch-fetch subject posts for like/repost notifications so the UI can
        // show the post that was liked/boosted without an N+1 round-trip.
        val subjectUris = response.notifications
            .filter { it.reason == "like" || it.reason == "repost" }
            .mapNotNull { it.reasonSubject }
            .distinct()
        val subjectMap: Map<String, BskyPostViewDto> = if (subjectUris.isEmpty()) {
            emptyMap()
        } else {
            // getPosts accepts up to 25 URIs per call.
            subjectUris.chunked(25)
                .flatMap { chunk -> runCatching { api.getPosts(chunk).posts }.getOrDefault(emptyList()) }
                .associateBy { it.uri }
        }
        response.notifications.map { it.toUniversal(subjectMap) }
    }

    override suspend fun getUser(userId: String): UniversalUser = authed {
        api.getProfile(userId).toUniversal()
    }

    override suspend fun getUserStatuses(
        userId: String,
        limit: Int,
        maxId: String?,
        excludeReplies: Boolean,
    ): List<UniversalStatus> = authed {
        api.getAuthorFeed(userId, limit, maxId).feed
            .let { feed ->
                if (excludeReplies) feed.filter { it.reply == null } else feed
            }
            .map { it.toUniversal() }
    }

    override suspend fun getRelationship(userId: String): Relationship? = authed {
        val profile = api.getProfile(userId)
        val v = profile.viewer ?: return@authed null
        Relationship(
            userId = profile.did,
            following = v.following != null,
            requested = false,
            followedBy = v.followedBy != null,
            muting = v.muted,
            blocking = v.blocking != null,
        )
    }

    override suspend fun follow(userId: String): Relationship = authed {
        val record = buildJsonObject {
            put("\$type", "app.bsky.graph.follow")
            put("subject", userId)
            put("createdAt", nowIso())
        }
        api.createRecord(account.userId, "app.bsky.graph.follow", record)
        getRelationship(userId) ?: Relationship(userId, true, false, false, false, false)
    }

    override suspend fun unfollow(userId: String): Relationship = authed {
        val rel = api.getProfile(userId).viewer
        val followUri = rel?.following
        if (followUri != null) {
            val rkey = followUri.substringAfterLast('/')
            api.deleteRecord(account.userId, "app.bsky.graph.follow", rkey)
        }
        getRelationship(userId) ?: Relationship(userId, false, false, false, false, false)
    }

    override suspend fun post(request: PostRequest): UniversalStatus = authed {
        val facets = BlueskyFacets.buildMentionFacets(api, request.text)
        val replyBlock: JsonObject? = request.inReplyToId?.let { parentUri ->
            val parent = api.getPosts(listOf(parentUri)).posts.firstOrNull()
            parent?.let { buildReplyBlock(it) }
        }
        // Quote = embed.record with a strongRef to the quoted post. If we
        // didn't receive a CID alongside the URI (e.g. quote came from a code
        // path that only knows the URI), fetch it — posting without a cid
        // will be rejected by the PDS.
        val quoteEmbed: JsonObject? = request.quoteStatusId?.let { uri ->
            val cid = request.quoteStatusCid
                ?: runCatching { api.getPosts(listOf(uri)).posts.firstOrNull()?.cid }.getOrNull()
                ?: return@let null
            buildJsonObject {
                put("\$type", "app.bsky.embed.record")
                putJsonObject("record") {
                    put("uri", uri)
                    put("cid", cid)
                }
            }
        }

        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.post")
            put("text", request.text)
            put("createdAt", nowIso())
            if (facets.isNotEmpty()) put("facets", facets)
            if (replyBlock != null) put("reply", replyBlock)
            if (quoteEmbed != null) put("embed", quoteEmbed)
        }
        val response = api.createRecord(account.userId, "app.bsky.feed.post", record)
        getStatus(response.uri)
    }

    /**
     * Build the `reply` subrecord: `parent` points at the post being replied to,
     * `root` points at the top of the thread. If the parent itself has a `reply`
     * block, reuse its `root`; otherwise the parent IS the root.
     */
    private fun buildReplyBlock(parent: BskyPostViewDto): JsonObject {
        val parentRef = buildJsonObject {
            put("uri", parent.uri)
            put("cid", parent.cid)
        }
        val parentsExistingReply = parent.record["reply"] as? JsonObject
        val existingRoot = parentsExistingReply?.get("root") as? JsonObject
        val rootRef = if (existingRoot != null) {
            // Defensive: only keep uri+cid so we don't forward unexpected fields.
            buildJsonObject {
                existingRoot["uri"]?.jsonPrimitive?.content?.let { put("uri", it) }
                existingRoot["cid"]?.jsonPrimitive?.content?.let { put("cid", it) }
            }
        } else parentRef
        return buildJsonObject {
            put("root", rootRef)
            put("parent", parentRef)
        }
    }

    override suspend fun favourite(statusId: String): Boolean = authed {
        val post = api.getPosts(listOf(statusId)).posts.firstOrNull() ?: return@authed false
        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.like")
            putJsonObject("subject") {
                put("uri", post.uri)
                put("cid", post.cid)
            }
            put("createdAt", nowIso())
        }
        api.createRecord(account.userId, "app.bsky.feed.like", record)
        true
    }

    override suspend fun unfavourite(statusId: String): Boolean = authed {
        val post = api.getPosts(listOf(statusId)).posts.firstOrNull() ?: return@authed false
        val likeUri = post.viewer?.like ?: return@authed false
        val rkey = likeUri.substringAfterLast('/')
        api.deleteRecord(account.userId, "app.bsky.feed.like", rkey)
        true
    }

    override suspend fun boost(statusId: String): Boolean = authed {
        val post = api.getPosts(listOf(statusId)).posts.firstOrNull() ?: return@authed false
        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.repost")
            putJsonObject("subject") {
                put("uri", post.uri)
                put("cid", post.cid)
            }
            put("createdAt", nowIso())
        }
        api.createRecord(account.userId, "app.bsky.feed.repost", record)
        true
    }

    override suspend fun unboost(statusId: String): Boolean = authed {
        val post = api.getPosts(listOf(statusId)).posts.firstOrNull() ?: return@authed false
        val repostUri = post.viewer?.repost ?: return@authed false
        val rkey = repostUri.substringAfterLast('/')
        api.deleteRecord(account.userId, "app.bsky.feed.repost", rkey)
        true
    }

    // Bluesky has no native bookmarking — silently no-op so UI toggle state stays sane.
    override suspend fun bookmark(statusId: String): Boolean = false
    override suspend fun unbookmark(statusId: String): Boolean = false

    override suspend fun deleteStatus(statusId: String): Boolean = authed {
        val rkey = statusId.substringAfterLast('/')
        api.deleteRecord(account.userId, "app.bsky.feed.post", rkey)
        true
    }

    private fun nowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
}
