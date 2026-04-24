package me.masonasons.fastsm.platform.bluesky

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

/**
 * Thin wrapper over Bluesky's PDS XRPC endpoints. Default PDS is bsky.social.
 * The access JWT is supplied lazily so a refresh doesn't require rebuilding the client.
 */
class BlueskyApi(
    private val httpClient: HttpClient,
    private val pdsBase: String = DEFAULT_PDS,
    private val accessJwtProvider: () -> String? = { null },
) {

    suspend fun createSession(identifier: String, password: String): SessionDto =
        httpClient.post("$pdsBase/xrpc/com.atproto.server.createSession") {
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(identifier, password))
        }.body()

    /** Uses the refresh JWT as the Authorization bearer to mint a new session. */
    suspend fun refreshSession(refreshJwt: String): SessionDto =
        httpClient.post("$pdsBase/xrpc/com.atproto.server.refreshSession") {
            header(HttpHeaders.Authorization, "Bearer $refreshJwt")
        }.body()

    suspend fun getTimeline(limit: Int, cursor: String?): BskyFeedResponseDto =
        httpClient.get("$pdsBase/xrpc/app.bsky.feed.getTimeline") {
            bearer()
            parameter("limit", limit)
            if (cursor != null) parameter("cursor", cursor)
        }.body()

    suspend fun getAuthorFeed(actor: String, limit: Int, cursor: String?): BskyFeedResponseDto =
        httpClient.get("$pdsBase/xrpc/app.bsky.feed.getAuthorFeed") {
            bearer()
            parameter("actor", actor)
            parameter("limit", limit)
            if (cursor != null) parameter("cursor", cursor)
        }.body()

    suspend fun getActorLikes(actor: String, limit: Int, cursor: String?): BskyFeedResponseDto =
        httpClient.get("$pdsBase/xrpc/app.bsky.feed.getActorLikes") {
            bearer()
            parameter("actor", actor)
            parameter("limit", limit)
            if (cursor != null) parameter("cursor", cursor)
        }.body()

    suspend fun searchPosts(query: String, limit: Int, cursor: String?): BskySearchPostsResponseDto =
        httpClient.get("$pdsBase/xrpc/app.bsky.feed.searchPosts") {
            bearer()
            parameter("q", query)
            parameter("limit", limit)
            if (cursor != null) parameter("cursor", cursor)
        }.body()

    suspend fun searchActors(query: String, limit: Int): BskySearchActorsResponseDto =
        httpClient.get("$pdsBase/xrpc/app.bsky.actor.searchActors") {
            bearer()
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    suspend fun getProfile(actor: String): BskyProfileDto =
        httpClient.get("$pdsBase/xrpc/app.bsky.actor.getProfile") {
            bearer()
            parameter("actor", actor)
        }.body()

    suspend fun resolveHandle(handle: String): ResolveHandleResponse =
        httpClient.get("$pdsBase/xrpc/com.atproto.identity.resolveHandle") {
            parameter("handle", handle)
        }.body()

    suspend fun listNotifications(limit: Int, cursor: String?): BskyNotificationsResponseDto =
        httpClient.get("$pdsBase/xrpc/app.bsky.notification.listNotifications") {
            bearer()
            parameter("limit", limit)
            if (cursor != null) parameter("cursor", cursor)
        }.body()

    suspend fun getPosts(uris: List<String>): GetPostsResponse =
        httpClient.get("$pdsBase/xrpc/app.bsky.feed.getPosts") {
            bearer()
            uris.forEach { parameter("uris", it) }
        }.body()

    suspend fun getPostThread(uri: String, depth: Int = 10): BskyThreadResponseDto =
        httpClient.get("$pdsBase/xrpc/app.bsky.feed.getPostThread") {
            bearer()
            parameter("uri", uri)
            parameter("depth", depth)
        }.body()

    suspend fun createRecord(repo: String, collection: String, record: JsonObject): CreateRecordResponse =
        httpClient.post("$pdsBase/xrpc/com.atproto.repo.createRecord") {
            bearer()
            contentType(ContentType.Application.Json)
            setBody(CreateRecordRequest(repo, collection, record))
        }.body()

    suspend fun deleteRecord(repo: String, collection: String, rkey: String) {
        httpClient.post("$pdsBase/xrpc/com.atproto.repo.deleteRecord") {
            bearer()
            contentType(ContentType.Application.Json)
            setBody(DeleteRecordRequest(repo, collection, rkey))
        }
    }

    private fun HttpRequestBuilder.bearer() {
        val jwt = accessJwtProvider()
        if (!jwt.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $jwt")
    }

    companion object {
        const val DEFAULT_PDS = "https://bsky.social"
    }
}
