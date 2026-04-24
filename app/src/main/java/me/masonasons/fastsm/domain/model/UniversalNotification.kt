package me.masonasons.fastsm.domain.model

import java.time.Instant

enum class NotificationType(val wire: String) {
    MENTION("mention"),
    REPLY("reply"),
    QUOTE("quote"),
    REBLOG("reblog"),
    FAVOURITE("favourite"),
    FOLLOW("follow"),
    FOLLOW_REQUEST("follow_request"),
    POLL("poll"),
    UPDATE("update"),
    STATUS("status"),
    OTHER("");

    companion object {
        fun fromWire(value: String): NotificationType =
            entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

data class UniversalNotification(
    val id: String,
    val type: NotificationType,
    val typeRaw: String,
    val createdAt: Instant,
    val account: UniversalUser,
    val status: UniversalStatus? = null,
    val platform: PlatformType,
)
