package me.masonasons.fastsm.platform

import kotlinx.coroutines.flow.Flow
import me.masonasons.fastsm.domain.model.Account
import me.masonasons.fastsm.domain.model.MediaUploadResult
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.PostRequest
import me.masonasons.fastsm.domain.model.Relationship
import me.masonasons.fastsm.domain.model.SearchResults
import me.masonasons.fastsm.domain.model.StatusContext
import me.masonasons.fastsm.domain.model.TimelineEvent
import me.masonasons.fastsm.domain.model.UniversalNotification
import me.masonasons.fastsm.domain.model.StatusSource
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.domain.model.UserListInfo

/**
 * Authenticated account on a specific platform. Mirrors the desktop
 * FastSM's `platforms/base.py` PlatformAccount — but only the slice the
 * Android MVP actually uses. Widen as new features land.
 */
interface PlatformAccount {

    val account: Account
    val platform: PlatformType
    val supportsVisibility: Boolean
    val supportsContentWarning: Boolean

    /** Instance-reported maximum post length. Fetched from the server and cached. */
    suspend fun getMaxPostChars(): Int

    /**
     * Server-reported maximum page size for timeline fetches. Default 40
     * matches Mastodon's hard cap; Bluesky overrides to 100.
     */
    val maxPageSize: Int get() = 40

    suspend fun verifyCredentials(): UniversalUser

    suspend fun getHomeTimeline(limit: Int = 40, maxId: String? = null): List<UniversalStatus>

    suspend fun getLocalTimeline(limit: Int = 40, maxId: String? = null): List<UniversalStatus>

    suspend fun getFederatedTimeline(limit: Int = 40, maxId: String? = null): List<UniversalStatus>

    /** Public timeline from a different instance, fetched unauthenticated. */
    suspend fun getRemoteInstanceTimeline(
        instance: String,
        localOnly: Boolean,
        limit: Int = 40,
        maxId: String? = null,
    ): List<UniversalStatus>

    /** Posts from a user on a remote instance, fetched directly from that instance. */
    suspend fun getRemoteUserTimeline(
        instance: String,
        acct: String,
        limit: Int = 40,
        maxId: String? = null,
    ): List<UniversalStatus>

    suspend fun getBookmarks(limit: Int = 40, maxId: String? = null): List<UniversalStatus>

    suspend fun getFavourites(limit: Int = 40, maxId: String? = null): List<UniversalStatus>

    /** Lists (Mastodon lists; Bluesky equivalent is not yet supported). */
    suspend fun getUserLists(): List<UserListInfo>

    suspend fun getListTimeline(listId: String, limit: Int = 40, maxId: String? = null): List<UniversalStatus>

    suspend fun getHashtagTimeline(tag: String, limit: Int = 40, maxId: String? = null): List<UniversalStatus>

    /** Lists that currently contain this user (Mastodon only). */
    suspend fun getListsContainingUser(userId: String): List<UserListInfo>

    suspend fun addUserToList(listId: String, userId: String): Boolean

    suspend fun removeUserFromList(listId: String, userId: String): Boolean

    suspend fun getStatus(statusId: String): UniversalStatus

    /**
     * If [status] came from a remote-instance or remote-user timeline (i.e.
     * the id is only valid on another server), return the current account's
     * local mirror of it. Default implementation is a pass-through — only
     * platforms that have a "resolve" concept (Mastodon) override.
     *
     * Callers should feed the returned status's id into favourite/boost/etc.
     */
    suspend fun resolveLocal(status: UniversalStatus): UniversalStatus = status

    suspend fun getStatusContext(statusId: String): StatusContext

    suspend fun getNotifications(limit: Int = 40, maxId: String? = null): List<UniversalNotification>

    suspend fun getUser(userId: String): UniversalUser

    suspend fun getUserStatuses(
        userId: String,
        limit: Int = 40,
        maxId: String? = null,
        excludeReplies: Boolean = false,
    ): List<UniversalStatus>

    suspend fun getRelationship(userId: String): Relationship?

    suspend fun follow(userId: String): Relationship

    suspend fun unfollow(userId: String): Relationship

    suspend fun post(request: PostRequest): UniversalStatus

    /** Whether this platform supports scheduling a post for future publication. */
    val supportsScheduling: Boolean get() = false

    /**
     * Schedule a post for future publication. Returns true on success.
     * Platforms without scheduling throw by default.
     */
    suspend fun schedulePost(request: PostRequest): Boolean =
        throw UnsupportedOperationException("Scheduling not supported on $platform")

    /** Whether this platform supports editing existing posts. */
    val supportsEdit: Boolean get() = false

    /** Whether this platform supports attaching media to a post. */
    val supportsMedia: Boolean get() = false

    /** Server-reported maximum media attachments per post. Platforms override when known. */
    val maxMediaAttachments: Int get() = 4

    /**
     * Upload an attachment. Throws [UnsupportedOperationException] by default
     * so platforms without media support don't silently no-op.
     */
    suspend fun uploadMedia(
        bytes: ByteArray,
        filename: String,
        mime: String,
        description: String?,
    ): MediaUploadResult = throw UnsupportedOperationException("Media upload not supported on $platform")

    /** Change the alt text of an already-uploaded attachment. */
    suspend fun updateMediaDescription(mediaId: String, description: String): Boolean = false

    /**
     * Raw text source of an existing post, for prefilling an edit compose.
     * Returns null when the platform can't retrieve it (Bluesky) or when the
     * server's too old to support /source — caller should fall back to the
     * already-displayed text.
     */
    suspend fun getStatusSourceText(statusId: String): StatusSource? = null

    /**
     * Edit an existing post. Required when [supportsEdit] is true; default
     * implementation throws so platforms without edit don't silently no-op.
     */
    suspend fun editStatus(statusId: String, request: PostRequest): UniversalStatus =
        throw UnsupportedOperationException("Edit not supported on $platform")

    suspend fun deleteStatus(statusId: String): Boolean

    suspend fun favourite(statusId: String): Boolean
    suspend fun unfavourite(statusId: String): Boolean
    suspend fun boost(statusId: String): Boolean
    suspend fun unboost(statusId: String): Boolean
    suspend fun bookmark(statusId: String): Boolean
    suspend fun unbookmark(statusId: String): Boolean

    suspend fun search(query: String): SearchResults

    /**
     * A cold flow of realtime events for the viewer's home/notifications.
     * Implementations retry with backoff internally; cancel the collector to stop.
     * Platforms without a client-friendly stream (Bluesky today) return empty.
     */
    fun streamEvents(): Flow<TimelineEvent>

    /** Server-side read marker for the home timeline (Mastodon). null when unsupported. */
    suspend fun getHomeMarker(): String?

    suspend fun setHomeMarker(statusId: String): Boolean
}
