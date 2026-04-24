package me.masonasons.fastsm.platform.mastodon

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType

/**
 * Thin Ktor wrapper over the Mastodon REST API. Single instance per account —
 * instance URL and token are bound at construction.
 */
class MastodonApi(
    private val httpClient: HttpClient,
    private val instanceBase: String,
    private val tokenProvider: () -> String?,
) {

    suspend fun registerApp(clientName: String, redirectUri: String, scopes: String, website: String?): MastodonAppDto =
        httpClient.submitForm(
            url = "$instanceBase/api/v1/apps",
            formParameters = Parameters.build {
                append("client_name", clientName)
                append("redirect_uris", redirectUri)
                append("scopes", scopes)
                if (website != null) append("website", website)
            },
        ).body()

    suspend fun exchangeToken(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
        scopes: String,
    ): MastodonTokenDto =
        httpClient.submitForm(
            url = "$instanceBase/oauth/token",
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("code", code)
                append("redirect_uri", redirectUri)
                append("scope", scopes)
            },
        ).body()

    suspend fun verifyCredentials(): MastodonAccountDto =
        httpClient.get("$instanceBase/api/v1/accounts/verify_credentials") { bearer() }.body()

    suspend fun getInstance(): MastodonInstanceDto =
        httpClient.get("$instanceBase/api/v1/instance").body()

    suspend fun homeTimeline(limit: Int, maxId: String?): List<MastodonStatusDto> =
        httpClient.get("$instanceBase/api/v1/timelines/home") {
            bearer()
            parameter("limit", limit)
            if (maxId != null) parameter("max_id", maxId)
        }.body()

    suspend fun getAccount(userId: String): MastodonAccountDto =
        httpClient.get("$instanceBase/api/v1/accounts/$userId") { bearer() }.body()

    suspend fun lookupAccount(acct: String): MastodonAccountDto =
        httpClient.get("$instanceBase/api/v1/accounts/lookup") {
            bearer()
            parameter("acct", acct)
        }.body()

    suspend fun getAccountStatuses(
        userId: String,
        limit: Int,
        maxId: String?,
        excludeReplies: Boolean,
    ): List<MastodonStatusDto> =
        httpClient.get("$instanceBase/api/v1/accounts/$userId/statuses") {
            bearer()
            parameter("limit", limit)
            if (maxId != null) parameter("max_id", maxId)
            if (excludeReplies) parameter("exclude_replies", true)
        }.body()

    suspend fun getRelationship(userId: String): MastodonRelationshipDto? =
        httpClient.get("$instanceBase/api/v1/accounts/relationships") {
            bearer()
            parameter("id[]", userId)
        }.body<List<MastodonRelationshipDto>>().firstOrNull()

    suspend fun followAccount(userId: String): MastodonRelationshipDto =
        httpClient.post("$instanceBase/api/v1/accounts/$userId/follow") { bearer() }.body()

    suspend fun unfollowAccount(userId: String): MastodonRelationshipDto =
        httpClient.post("$instanceBase/api/v1/accounts/$userId/unfollow") { bearer() }.body()

    suspend fun getStatus(statusId: String): MastodonStatusDto =
        httpClient.get("$instanceBase/api/v1/statuses/$statusId") { bearer() }.body()

    /** Raw text source (Mastodon 3.5+). For edit prefill — avoids HTML-strip loss. */
    suspend fun getStatusSource(statusId: String): MastodonStatusSourceDto =
        httpClient.get("$instanceBase/api/v1/statuses/$statusId/source") { bearer() }.body()

    suspend fun getStatusContext(statusId: String): MastodonContextDto =
        httpClient.get("$instanceBase/api/v1/statuses/$statusId/context") { bearer() }.body()

    suspend fun bookmarks(limit: Int, maxId: String?): List<MastodonStatusDto> =
        httpClient.get("$instanceBase/api/v1/bookmarks") {
            bearer()
            parameter("limit", limit)
            if (maxId != null) parameter("max_id", maxId)
        }.body()

    suspend fun favourites(limit: Int, maxId: String?): List<MastodonStatusDto> =
        httpClient.get("$instanceBase/api/v1/favourites") {
            bearer()
            parameter("limit", limit)
            if (maxId != null) parameter("max_id", maxId)
        }.body()

    suspend fun getLists(): List<MastodonListDto> =
        httpClient.get("$instanceBase/api/v1/lists") { bearer() }.body()

    suspend fun getListsContainingUser(userId: String): List<MastodonListDto> =
        httpClient.get("$instanceBase/api/v1/accounts/$userId/lists") { bearer() }.body()

    suspend fun addAccountToList(listId: String, accountId: String) {
        httpClient.submitForm(
            url = "$instanceBase/api/v1/lists/$listId/accounts",
            formParameters = Parameters.build { append("account_ids[]", accountId) },
        ) { bearer() }
    }

    suspend fun removeAccountFromList(listId: String, accountId: String) {
        httpClient.delete("$instanceBase/api/v1/lists/$listId/accounts") {
            bearer()
            parameter("account_ids[]", accountId)
        }
    }

    suspend fun listTimeline(listId: String, limit: Int, maxId: String?): List<MastodonStatusDto> =
        httpClient.get("$instanceBase/api/v1/timelines/list/$listId") {
            bearer()
            parameter("limit", limit)
            if (maxId != null) parameter("max_id", maxId)
        }.body()

    suspend fun hashtagTimeline(tag: String, limit: Int, maxId: String?): List<MastodonStatusDto> =
        httpClient.get("$instanceBase/api/v1/timelines/tag/${tag.removePrefix("#")}") {
            bearer()
            parameter("limit", limit)
            if (maxId != null) parameter("max_id", maxId)
        }.body()

    suspend fun search(query: String, limit: Int, resolve: Boolean): MastodonSearchResponseDto =
        httpClient.get("$instanceBase/api/v2/search") {
            bearer()
            parameter("q", query)
            parameter("limit", limit)
            parameter("resolve", resolve)
        }.body()

    suspend fun getMarkers(): MastodonMarkersResponseDto =
        httpClient.get("$instanceBase/api/v1/markers") {
            bearer()
            parameter("timeline[]", "home")
            parameter("timeline[]", "notifications")
        }.body()

    suspend fun setHomeMarker(statusId: String): MastodonMarkersResponseDto =
        httpClient.submitForm(
            url = "$instanceBase/api/v1/markers",
            formParameters = Parameters.build { append("home[last_read_id]", statusId) },
        ) { bearer() }.body()

    suspend fun notifications(limit: Int, maxId: String?): List<MastodonNotificationDto> =
        httpClient.get("$instanceBase/api/v1/notifications") {
            bearer()
            parameter("limit", limit)
            if (maxId != null) parameter("max_id", maxId)
        }.body()

    suspend fun publicTimeline(limit: Int, maxId: String?, local: Boolean): List<MastodonStatusDto> =
        httpClient.get("$instanceBase/api/v1/timelines/public") {
            bearer()
            parameter("limit", limit)
            if (maxId != null) parameter("max_id", maxId)
            if (local) parameter("local", true)
        }.body()

    suspend fun postStatus(
        status: String,
        inReplyToId: String?,
        visibility: String?,
        spoilerText: String?,
        mediaIds: List<String>,
        pollOptions: List<String> = emptyList(),
        pollExpiresInSec: Int? = null,
        pollMultiple: Boolean = false,
        pollHideTotals: Boolean = false,
        quotedStatusId: String? = null,
    ): MastodonStatusDto =
        httpClient.submitForm(
            url = "$instanceBase/api/v1/statuses",
            formParameters = Parameters.build {
                append("status", status)
                if (inReplyToId != null) append("in_reply_to_id", inReplyToId)
                if (visibility != null) append("visibility", visibility)
                if (!spoilerText.isNullOrEmpty()) append("spoiler_text", spoilerText)
                mediaIds.forEach { append("media_ids[]", it) }
                if (pollOptions.isNotEmpty() && pollExpiresInSec != null) {
                    pollOptions.forEach { append("poll[options][]", it) }
                    append("poll[expires_in]", pollExpiresInSec.toString())
                    append("poll[multiple]", pollMultiple.toString())
                    append("poll[hide_totals]", pollHideTotals.toString())
                }
                if (quotedStatusId != null) append("quoted_status_id", quotedStatusId)
            },
        ) { bearer() }.body()

    /**
     * Like [postStatus] but with `scheduled_at` set — the server returns a
     * ScheduledStatus shape instead of a Status, so we use a different return
     * type and let the caller treat it as fire-and-forget.
     */
    suspend fun scheduleStatus(
        status: String,
        scheduledAtIso: String,
        inReplyToId: String?,
        visibility: String?,
        spoilerText: String?,
        mediaIds: List<String>,
        pollOptions: List<String> = emptyList(),
        pollExpiresInSec: Int? = null,
        pollMultiple: Boolean = false,
        pollHideTotals: Boolean = false,
    ): MastodonScheduledStatusDto =
        httpClient.submitForm(
            url = "$instanceBase/api/v1/statuses",
            formParameters = Parameters.build {
                append("status", status)
                append("scheduled_at", scheduledAtIso)
                if (inReplyToId != null) append("in_reply_to_id", inReplyToId)
                if (visibility != null) append("visibility", visibility)
                if (!spoilerText.isNullOrEmpty()) append("spoiler_text", spoilerText)
                mediaIds.forEach { append("media_ids[]", it) }
                if (pollOptions.isNotEmpty() && pollExpiresInSec != null) {
                    pollOptions.forEach { append("poll[options][]", it) }
                    append("poll[expires_in]", pollExpiresInSec.toString())
                    append("poll[multiple]", pollMultiple.toString())
                    append("poll[hide_totals]", pollHideTotals.toString())
                }
            },
        ) { bearer() }.body()

    /**
     * v2 media upload — returns 202 when processing continues asynchronously,
     * 200 when ready. Either way, the returned id can be attached to a post
     * (Mastodon will hold the post until media finishes processing).
     */
    suspend fun uploadMedia(
        bytes: ByteArray,
        filename: String,
        mime: String,
        description: String?,
    ): MastodonMediaDto =
        httpClient.post("$instanceBase/api/v2/media") {
            bearer()
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                            },
                        )
                        if (!description.isNullOrBlank()) append("description", description)
                    }
                )
            )
        }.body()

    /** Fetch a media record by id — used to poll processing state. */
    suspend fun getMedia(mediaId: String): MastodonMediaDto =
        httpClient.get("$instanceBase/api/v1/media/$mediaId") { bearer() }.body()

    suspend fun updateMediaDescription(mediaId: String, description: String): MastodonMediaDto =
        httpClient.put("$instanceBase/api/v1/media/$mediaId") {
            bearer()
            setBody(
                FormDataContent(
                    Parameters.build { append("description", description) }
                )
            )
        }.body()

    /**
     * Edit an existing status. Mastodon's edit endpoint takes the same form
     * fields as post but as a PUT; visibility can't be changed by edit, so we
     * don't send it.
     */
    suspend fun editStatus(
        statusId: String,
        status: String,
        spoilerText: String?,
    ): MastodonStatusDto =
        httpClient.put("$instanceBase/api/v1/statuses/$statusId") {
            bearer()
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("status", status)
                        // Always send spoiler_text (even empty) so clearing a CW works.
                        append("spoiler_text", spoilerText.orEmpty())
                    }
                )
            )
        }.body()

    suspend fun favourite(statusId: String): MastodonStatusDto =
        httpClient.post("$instanceBase/api/v1/statuses/$statusId/favourite") { bearer() }.body()

    suspend fun unfavourite(statusId: String): MastodonStatusDto =
        httpClient.post("$instanceBase/api/v1/statuses/$statusId/unfavourite") { bearer() }.body()

    suspend fun reblog(statusId: String): MastodonStatusDto =
        httpClient.post("$instanceBase/api/v1/statuses/$statusId/reblog") { bearer() }.body()

    suspend fun unreblog(statusId: String): MastodonStatusDto =
        httpClient.post("$instanceBase/api/v1/statuses/$statusId/unreblog") { bearer() }.body()

    suspend fun bookmark(statusId: String): MastodonStatusDto =
        httpClient.post("$instanceBase/api/v1/statuses/$statusId/bookmark") { bearer() }.body()

    suspend fun unbookmark(statusId: String): MastodonStatusDto =
        httpClient.post("$instanceBase/api/v1/statuses/$statusId/unbookmark") { bearer() }.body()

    suspend fun deleteStatus(statusId: String) {
        httpClient.delete("$instanceBase/api/v1/statuses/$statusId") { bearer() }
    }

    private fun HttpRequestBuilder.bearer() {
        val token = tokenProvider()
        if (!token.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $token")
    }
}
