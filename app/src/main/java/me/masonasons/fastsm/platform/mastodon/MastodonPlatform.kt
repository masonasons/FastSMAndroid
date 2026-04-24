package me.masonasons.fastsm.platform.mastodon

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.masonasons.fastsm.domain.model.Account
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.MediaUploadResult
import me.masonasons.fastsm.domain.model.PostRequest
import me.masonasons.fastsm.domain.model.Relationship
import me.masonasons.fastsm.domain.model.SearchResults
import me.masonasons.fastsm.domain.model.StatusContext
import me.masonasons.fastsm.domain.model.StatusSource
import me.masonasons.fastsm.domain.model.TimelineEvent
import me.masonasons.fastsm.domain.model.UniversalNotification
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.domain.model.UserListInfo
import me.masonasons.fastsm.platform.PlatformAccount

class MastodonPlatform(
    override val account: Account,
    private val httpClient: HttpClient,
    private val tokenProvider: () -> String?,
) : PlatformAccount {

    private val api = MastodonApi(
        httpClient = httpClient,
        instanceBase = account.instance,
        tokenProvider = tokenProvider,
    )

    private val streaming = MastodonStreaming(
        httpClient = httpClient,
        instanceBase = account.instance,
        tokenProvider = tokenProvider,
    )

    /** Unauthenticated client for a remote instance. Cheap to construct. */
    private fun remoteApi(instance: String): MastodonApi = MastodonApi(
        httpClient = httpClient,
        instanceBase = normalizeInstance(instance),
        tokenProvider = { null },
    )

    private fun normalizeInstance(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        return when {
            trimmed.isEmpty() -> trimmed
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    override val platform: PlatformType = PlatformType.MASTODON
    override val supportsVisibility: Boolean = true
    override val supportsContentWarning: Boolean = true

    private val maxCharsMutex = Mutex()

    @Volatile
    private var cachedMaxChars: Int? = null

    override suspend fun getMaxPostChars(): Int {
        cachedMaxChars?.let { return it }
        return maxCharsMutex.withLock {
            cachedMaxChars ?: runCatching {
                val info = api.getInstance()
                info.configuration?.statuses?.max_characters
                    ?: info.max_toot_chars
                    ?: DEFAULT_MAX_CHARS
            }.getOrDefault(DEFAULT_MAX_CHARS).also { cachedMaxChars = it }
        }
    }

    override suspend fun verifyCredentials(): UniversalUser =
        api.verifyCredentials().toUniversal()

    override suspend fun getHomeTimeline(limit: Int, maxId: String?): List<UniversalStatus> =
        api.homeTimeline(limit, maxId).map { it.toUniversal() }

    override suspend fun getLocalTimeline(limit: Int, maxId: String?): List<UniversalStatus> =
        api.publicTimeline(limit, maxId, local = true).map { it.toUniversal() }

    override suspend fun getFederatedTimeline(limit: Int, maxId: String?): List<UniversalStatus> =
        api.publicTimeline(limit, maxId, local = false).map { it.toUniversal() }

    override suspend fun getRemoteInstanceTimeline(
        instance: String,
        localOnly: Boolean,
        limit: Int,
        maxId: String?,
    ): List<UniversalStatus> =
        remoteApi(instance).publicTimeline(limit, maxId, local = localOnly).map { it.toUniversal() }

    override suspend fun getRemoteUserTimeline(
        instance: String,
        acct: String,
        limit: Int,
        maxId: String?,
    ): List<UniversalStatus> {
        val remote = remoteApi(instance)
        val bareAcct = acct.removePrefix("@").substringBefore('@')
        val user = remote.lookupAccount(bareAcct)
        return remote.getAccountStatuses(user.id, limit, maxId, excludeReplies = false).map { it.toUniversal() }
    }

    override suspend fun getBookmarks(limit: Int, maxId: String?): List<UniversalStatus> =
        api.bookmarks(limit, maxId).map { it.toUniversal() }

    override suspend fun getFavourites(limit: Int, maxId: String?): List<UniversalStatus> =
        api.favourites(limit, maxId).map { it.toUniversal() }

    override suspend fun getUserLists(): List<UserListInfo> =
        api.getLists().map { UserListInfo(it.id, it.title, PlatformType.MASTODON) }

    override suspend fun getListTimeline(
        listId: String,
        limit: Int,
        maxId: String?,
    ): List<UniversalStatus> =
        api.listTimeline(listId, limit, maxId).map { it.toUniversal() }

    override suspend fun getHashtagTimeline(
        tag: String,
        limit: Int,
        maxId: String?,
    ): List<UniversalStatus> =
        api.hashtagTimeline(tag, limit, maxId).map { it.toUniversal() }

    override suspend fun getListsContainingUser(userId: String): List<UserListInfo> =
        api.getListsContainingUser(userId)
            .map { UserListInfo(it.id, it.title, PlatformType.MASTODON) }

    override suspend fun addUserToList(listId: String, userId: String): Boolean {
        api.addAccountToList(listId, userId); return true
    }

    override suspend fun removeUserFromList(listId: String, userId: String): Boolean {
        api.removeAccountFromList(listId, userId); return true
    }

    override suspend fun search(query: String): SearchResults {
        if (query.isBlank()) return SearchResults()
        val r = api.search(query, limit = 10, resolve = true)
        return SearchResults(
            users = r.accounts.map { it.toUniversal() },
            posts = r.statuses.map { it.toUniversal() },
            hashtags = r.hashtags.map { it.name },
        )
    }

    override fun streamEvents(): Flow<TimelineEvent> = streaming.events()

    override suspend fun getHomeMarker(): String? =
        runCatching { api.getMarkers().home?.last_read_id }.getOrNull()

    override suspend fun setHomeMarker(statusId: String): Boolean =
        runCatching { api.setHomeMarker(statusId) }.isSuccess

    override suspend fun getStatus(statusId: String): UniversalStatus =
        api.getStatus(statusId).toUniversal()

    /**
     * Resolve a post into this account's local mirror when needed.
     *
     * A post is "remote" to our server when its canonical URL is hosted
     * elsewhere than [account.instance] — this happens on RemoteInstance /
     * RemoteUser timelines where we fetched unauthenticated from another
     * server. Interacting with those ids against our server fails, so we
     * use v2 search with resolve=true to have the server fetch + cache a
     * local copy, then act on that. We cheaply cache resolved ids for the
     * life of this platform so we don't re-resolve every interaction.
     */
    override suspend fun resolveLocal(status: UniversalStatus): UniversalStatus {
        val url = status.url ?: return status
        val myHost = hostOf(account.instance) ?: return status
        val postHost = hostOf(url) ?: return status
        if (myHost.equals(postHost, ignoreCase = true)) return status
        resolveCache[url]?.let { return it }
        val resolved = runCatching { api.search(url, limit = 1, resolve = true) }
            .getOrNull()
            ?.statuses
            ?.firstOrNull()
            ?.toUniversal()
            ?: return status
        resolveCache[url] = resolved
        return resolved
    }

    private val resolveCache = mutableMapOf<String, UniversalStatus>()

    private fun hostOf(url: String): String? =
        runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()

    override suspend fun getStatusContext(statusId: String): StatusContext {
        val ctx = api.getStatusContext(statusId)
        return StatusContext(
            ancestors = ctx.ancestors.map { it.toUniversal() },
            descendants = ctx.descendants.map { it.toUniversal() },
        )
    }

    override suspend fun getNotifications(limit: Int, maxId: String?): List<UniversalNotification> =
        api.notifications(limit, maxId).map { it.toUniversal() }

    override suspend fun getUser(userId: String): UniversalUser =
        api.getAccount(userId).toUniversal()

    override suspend fun getUserStatuses(
        userId: String,
        limit: Int,
        maxId: String?,
        excludeReplies: Boolean,
    ): List<UniversalStatus> =
        api.getAccountStatuses(userId, limit, maxId, excludeReplies).map { it.toUniversal() }

    override suspend fun getRelationship(userId: String): Relationship? =
        api.getRelationship(userId)?.toUniversal()

    override suspend fun follow(userId: String): Relationship =
        api.followAccount(userId).toUniversal()

    override suspend fun unfollow(userId: String): Relationship =
        api.unfollowAccount(userId).toUniversal()

    override val supportsEdit: Boolean = true
    override val supportsMedia: Boolean = true
    override val supportsScheduling: Boolean = true

    override suspend fun schedulePost(request: PostRequest): Boolean {
        val at = request.scheduledAt ?: error("scheduledAt required")
        api.scheduleStatus(
            status = request.text,
            scheduledAtIso = at.toString(),
            inReplyToId = request.inReplyToId,
            visibility = request.visibility?.wire,
            spoilerText = request.spoilerText,
            mediaIds = request.mediaIds,
            pollOptions = request.poll?.options.orEmpty(),
            pollExpiresInSec = request.poll?.expiresInSec,
            pollMultiple = request.poll?.multiple ?: false,
            pollHideTotals = request.poll?.hideTotals ?: false,
        )
        return true
    }

    override suspend fun uploadMedia(
        bytes: ByteArray,
        filename: String,
        mime: String,
        description: String?,
    ): MediaUploadResult {
        val initial = api.uploadMedia(bytes, filename, mime, description)
        // v2 returns a record with `url` null while transcoding. Some servers
        // (Glitch-Soc, Fedibird) reject not-yet-ready ids in a post instead of
        // queueing — poll until the server says the attachment is ready. Bound
        // by ~60s so we fail loudly rather than hang forever.
        var current = initial
        var delayMs = 500L
        val deadline = System.currentTimeMillis() + 60_000
        while (current.url.isNullOrBlank() && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(delayMs)
            val next = runCatching { api.getMedia(current.id) }.getOrNull() ?: break
            current = next
            delayMs = (delayMs * 2).coerceAtMost(3_000)
        }
        return MediaUploadResult(
            mediaId = current.id,
            previewUrl = current.preview_url,
            processing = current.url.isNullOrBlank(),
        )
    }

    override suspend fun updateMediaDescription(mediaId: String, description: String): Boolean {
        api.updateMediaDescription(mediaId, description)
        return true
    }

    override suspend fun getStatusSourceText(statusId: String): StatusSource? =
        runCatching { api.getStatusSource(statusId) }
            .map { StatusSource(text = it.text, spoilerText = it.spoiler_text) }
            .getOrNull()

    override suspend fun editStatus(statusId: String, request: PostRequest): UniversalStatus =
        api.editStatus(
            statusId = statusId,
            status = request.text,
            spoilerText = request.spoilerText,
        ).toUniversal()

    override suspend fun post(request: PostRequest): UniversalStatus =
        api.postStatus(
            status = request.text,
            inReplyToId = request.inReplyToId,
            visibility = request.visibility?.wire,
            mediaIds = request.mediaIds,
            pollOptions = request.poll?.options.orEmpty(),
            pollExpiresInSec = request.poll?.expiresInSec,
            pollMultiple = request.poll?.multiple ?: false,
            pollHideTotals = request.poll?.hideTotals ?: false,
            spoilerText = request.spoilerText,
            quotedStatusId = request.quoteStatusId,
        ).toUniversal()

    override suspend fun favourite(statusId: String): Boolean {
        api.favourite(statusId); return true
    }

    override suspend fun unfavourite(statusId: String): Boolean {
        api.unfavourite(statusId); return true
    }

    override suspend fun boost(statusId: String): Boolean {
        api.reblog(statusId); return true
    }

    override suspend fun unboost(statusId: String): Boolean {
        api.unreblog(statusId); return true
    }

    override suspend fun bookmark(statusId: String): Boolean {
        api.bookmark(statusId); return true
    }

    override suspend fun unbookmark(statusId: String): Boolean {
        api.unbookmark(statusId); return true
    }

    override suspend fun deleteStatus(statusId: String): Boolean {
        api.deleteStatus(statusId); return true
    }

    private companion object {
        const val DEFAULT_MAX_CHARS = 500
    }
}
