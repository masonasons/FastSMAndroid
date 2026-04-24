package me.masonasons.fastsm.domain.model

/** Realtime events for a user stream — home-timeline posts, notifications, deletions. */
sealed class TimelineEvent {
    data class StatusUpdate(val status: UniversalStatus) : TimelineEvent()
    data class NotificationReceived(val notification: UniversalNotification) : TimelineEvent()
    data class StatusDeleted(val statusId: String) : TimelineEvent()
}
