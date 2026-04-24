package me.masonasons.fastsm.platform.mastodon

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.masonasons.fastsm.domain.model.TimelineEvent

@Serializable
private data class MastodonStreamEnvelope(
    val stream: List<String> = emptyList(),
    val event: String,
    val payload: String? = null,
)

/**
 * Mastodon user-stream WebSocket — emits home posts, notifications, and
 * deletions. Reconnects with exponential backoff (1s → 30s cap) on any
 * failure; cancelling the collector tears the socket down.
 */
internal class MastodonStreaming(
    private val httpClient: HttpClient,
    private val instanceBase: String,
    private val tokenProvider: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun events(): Flow<TimelineEvent> = channelFlow {
        var backoffMs = 1_000L
        while (isActive) {
            val token = tokenProvider().orEmpty()
            if (token.isBlank()) break
            val result = runCatching { runSession(this, token) }
            if (!isActive) break
            if (result.isSuccess) {
                backoffMs = 1_000L
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }

    private suspend fun runSession(
        scope: ProducerScope<TimelineEvent>,
        token: String,
    ) {
        val wsUrl = buildString {
            append(instanceBase.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://"))
            append("/api/v1/streaming")
            append("?stream=user&access_token=").append(token)
        }
        httpClient.webSocket(urlString = wsUrl) {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    parse(frame.readText())?.let { scope.send(it) }
                }
            }
        }
    }

    private fun parse(raw: String): TimelineEvent? {
        val env = runCatching { json.decodeFromString(MastodonStreamEnvelope.serializer(), raw) }
            .getOrNull() ?: return null
        return when (env.event) {
            "update" -> env.payload
                ?.let { runCatching { json.decodeFromString(MastodonStatusDto.serializer(), it) }.getOrNull() }
                ?.let { TimelineEvent.StatusUpdate(it.toUniversal()) }
            "notification" -> env.payload
                ?.let { runCatching { json.decodeFromString(MastodonNotificationDto.serializer(), it) }.getOrNull() }
                ?.let { TimelineEvent.NotificationReceived(it.toUniversal()) }
            "delete" -> env.payload?.takeIf { it.isNotBlank() }?.let { TimelineEvent.StatusDeleted(it) }
            else -> null
        }
    }
}
